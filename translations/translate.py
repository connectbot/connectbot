#!/usr/bin/env python3
"""
Translate missing Android string resources using a local Ollama LLM.

Usage:
    python translations/translate.py app/src/main/res

For each values-<locale>/ directory found under <res_dir>, this script:
  - Reads values/strings.xml from the source locale
  - For each <string>, <string-array>, <plurals> element:
      - Skips elements with translatable="false"
      - Skips elements already in the target WITHOUT tools:machine_translated="true"
      - Translates elements that are missing OR have tools:machine_translated="true"
  - Writes back the target file with tools:machine_translated="true" on new
    translations, preserving utf-8 encoding

Up to MAX_CONCURRENT Ollama requests run in parallel with backpressure via a semaphore.
"""

import argparse
import asyncio
import os
import sys
import time
from dataclasses import dataclass, field
from itertools import chain
from pathlib import Path

from lxml import etree
import ollama

OLLAMA_HOST = os.environ.get("OLLAMA_HOST", "http://localhost:11434")
MODEL = "translategemma:27b"
TEMPERATURE = 0.1
MAX_CONCURRENT = 5

TOOLS_NS = "http://schemas.android.com/tools"
TOOLS_MACHINE_TRANSLATED = f"{{{TOOLS_NS}}}machine_translated"

TRANSLATABLE_TAGS = {"string", "string-array", "plurals"}
WHITESPACE = " \n\t"
EOF = None

SOURCE_LANG = "English"
SOURCE_LANG_CODE = "en"

PROMPT_WITH_CONTEXT = """\
You are a professional {source_lang} ({src_lang_code}) to {target_lang} ({tgt_lang_code}) translator. Your goal is to accurately convey the meaning and nuances of the original {source_lang} text while adhering to {target_lang} grammar, vocabulary, and cultural sensitivities. Produce only the {target_lang} translation, without any additional explanations or commentary.

[DOMAIN CONTEXT]
The following comment provides technical or visual context for the string. Do NOT translate this context block. Use it only to understand the situation:
{context_comment}
[/DOMAIN CONTEXT]

Please translate the following {source_lang} text into {target_lang}:

{text}"""

PROMPT_NO_CONTEXT = """\
You are a professional {source_lang} ({src_lang_code}) to {target_lang} ({tgt_lang_code}) translator. Your goal is to accurately convey the meaning and nuances of the original {source_lang} text while adhering to {target_lang} grammar, vocabulary, and cultural sensitivities. Produce only the {target_lang} translation, without any additional explanations or commentary.

Please translate the following {source_lang} text into {target_lang}:

{text}"""

# Maps Android values-<suffix> → (full language name, BCP47 code)
LOCALE_MAP: dict[str, tuple[str, str]] = {
    "af": ("Afrikaans", "af"),
    "ar": ("Arabic", "ar"),
    "be": ("Belarusian", "be"),
    "bg": ("Bulgarian", "bg"),
    "ca": ("Catalan", "ca"),
    "cs": ("Czech", "cs"),
    "da": ("Danish", "da"),
    "de": ("German", "de"),
    "el": ("Greek", "el"),
    "en-rCA": ("Canadian English", "en-CA"),
    "en-rGB": ("British English", "en-GB"),
    "es": ("Spanish", "es"),
    "eu": ("Basque", "eu"),
    "fa": ("Persian", "fa"),
    "fi": ("Finnish", "fi"),
    "fr": ("French", "fr"),
    "gl": ("Galician", "gl"),
    "he": ("Hebrew", "he"),
    "hr": ("Croatian", "hr"),
    "hu": ("Hungarian", "hu"),
    "id": ("Indonesian", "id"),
    "in": ("Indonesian", "id"),
    "is": ("Icelandic", "is"),
    "it": ("Italian", "it"),
    "iw": ("Hebrew", "he"),
    "ja": ("Japanese", "ja"),
    "ka": ("Georgian", "ka"),
    "ko": ("Korean", "ko"),
    "lo": ("Lao", "lo"),
    "lt": ("Lithuanian", "lt"),
    "lv": ("Latvian", "lv"),
    "mk": ("Macedonian", "mk"),
    "nb": ("Norwegian Bokmål", "nb"),
    "ne": ("Nepali", "ne"),
    "nl": ("Dutch", "nl"),
    "pl": ("Polish", "pl"),
    "pt": ("Portuguese", "pt"),
    "pt-rBR": ("Brazilian Portuguese", "pt-BR"),
    "ro": ("Romanian", "ro"),
    "ru": ("Russian", "ru"),
    "sk": ("Slovak", "sk"),
    "sl": ("Slovenian", "sl"),
    "sr": ("Serbian", "sr"),
    "sv": ("Swedish", "sv"),
    "ta": ("Tamil", "ta"),
    "th": ("Thai", "th"),
    "tk": ("Turkmen", "tk"),
    "tr": ("Turkish", "tr"),
    "uk": ("Ukrainian", "uk"),
    "vi": ("Vietnamese", "vi"),
    "zh-rCN": ("Simplified Chinese", "zh-CN"),
    "zh-rHK": ("Traditional Chinese (Hong Kong)", "zh-HK"),
    "zh-rTW": ("Traditional Chinese (Taiwan)", "zh-TW"),
}


# ---------------------------------------------------------------------------
# Translation job: one unit of work sent to Ollama
# ---------------------------------------------------------------------------

@dataclass
class TranslationJob:
    name: str
    text: str
    context: str
    target_lang: str
    tgt_code: str
    item_index: int | None = None
    # filled in after completion
    result: str = ""


# ---------------------------------------------------------------------------
# XML helpers
# ---------------------------------------------------------------------------

def make_parser() -> etree.XMLParser:
    return etree.XMLParser(remove_comments=False, remove_blank_text=False)


def load_xml(path: Path) -> etree._ElementTree:
    return etree.parse(str(path), make_parser())


def new_resources_tree() -> etree._ElementTree:
    root = etree.Element("resources", nsmap={"tools": TOOLS_NS})
    root.text = "\n"
    return etree.ElementTree(root)


def ensure_tools_namespace(root: etree._Element) -> etree._Element:
    """Return root with xmlns:tools declared. If already present, returns root unchanged.
    lxml nsmap is immutable post-construction, so a new element must be created to add it."""
    if TOOLS_NS in root.nsmap.values():
        return root
    nsmap = {**root.nsmap, "tools": TOOLS_NS}
    new_root = etree.Element(root.tag, attrib=root.attrib, nsmap=nsmap)
    new_root.text = root.text
    new_root.tail = root.tail
    for child in root:
        new_root.append(child)
    return new_root


def preceding_comment(elem: etree._Element) -> str:
    parent = elem.getparent()
    if parent is None:
        return ""
    children = list(parent)
    idx = children.index(elem)
    for i in range(idx - 1, -1, -1):
        sibling = children[i]
        if isinstance(sibling, etree._Comment):
            return (sibling.text or "").strip()
        tag = getattr(sibling, "tag", None)
        if tag is not None and not callable(tag):
            break
    return ""


def android_unescape_text(text: str) -> str:
    """Decode Android string resource quotes/escapes for translator prompts."""
    text = text.replace("<", "&lt;").replace(">", "&gt;")

    space_count = 0
    active_quote = False
    active_percent = False
    active_escape = False
    chars: list[str | None] = list(text) + [EOF]
    i = 0
    while i < len(chars):
        c = chars[i]

        if c is not EOF and c in WHITESPACE:
            space_count += 1
        elif space_count > 1:
            if not active_quote or c is EOF:
                chars[i - space_count : i] = [" "]
                i -= space_count - 1
            space_count = 0
        elif space_count == 1:
            chars[i - 1] = " "
            space_count = 0
        else:
            space_count = 0

        if c == '"' and not active_escape:
            active_quote = not active_quote
            del chars[i]
            i -= 1

        if c == "%" and not active_escape:
            active_percent = not active_percent
        elif not active_escape and active_percent:
            active_percent = False

        if c == "\\":
            if not active_escape:
                active_escape = True
            else:
                del chars[i]
                i -= 1
                active_escape = False
        elif active_escape:
            if c is EOF:
                pass
            elif c == "n":
                chars[i - 1 : i + 1] = ["\n"]
                i -= 1
            elif c == "t":
                chars[i - 1 : i + 1] = ["\t"]
                i -= 1
            elif c in "\"'@":
                chars[i - 1 : i] = []
                i -= 1
            elif c == "u":
                max_slice = min(i + 5, len(chars) - 1)
                codepoint_str = "".join(c for c in chars[i + 1 : max_slice] if c is not None)
                if len(codepoint_str) < 4:
                    codepoint_str = "0" * (4 - len(codepoint_str)) + codepoint_str
                try:
                    if not codepoint_str.isalnum():
                        raise ValueError
                    chars[i - 1 : max_slice] = [chr(int(codepoint_str, 16))]
                    i -= 1
                except ValueError:
                    pass
            else:
                chars[i - 1 : i + 1] = []
                i -= 1
            active_escape = False

        i += 1

    return "".join(c for c in chars[:-1] if c is not None)


def android_escape_text(text: str) -> str:
    """Escape characters that are special in Android string resources."""
    return (
        text.replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
        .replace("'", "\\'")
        .replace('"', '\\"')
        .replace("@", "\\@")
    )


def android_quote_text(text: str) -> str:
    """Quote text when Android would otherwise trim or collapse whitespace."""
    needs_quoting = text.strip(WHITESPACE) != text

    if not needs_quoting:
        space_count = 0
        for c in chain(text, [EOF]):
            if c is not EOF and c in WHITESPACE:
                space_count += 1
                if space_count >= 2:
                    needs_quoting = True
                    break
            else:
                space_count = 0

    return f'"{text}"' if needs_quoting else text


def android_format_text(text: str) -> str:
    """Format translator output according to Android string resource rules."""
    text = text.replace("&lt;", "<").replace("&gt;", ">")
    return android_quote_text(android_escape_text(text))


def get_element_text(elem: etree._Element) -> str:
    local = etree.QName(elem.tag).localname
    if local == "string":
        return android_unescape_text((elem.text or "").strip(WHITESPACE))
    return "\n".join(
        android_unescape_text(c.text or "")
        for c in elem
        if etree.QName(c.tag).localname == "item"
    )


def set_element_text(
    elem: etree._Element, translated: str, source_elem: etree._Element
) -> None:
    local = etree.QName(elem.tag).localname
    if local == "string":
        elem.text = android_format_text(translated)
        return
    lines = translated.split("\n")
    source_items = [c for c in source_elem if etree.QName(c.tag).localname == "item"]
    for child in list(elem):
        elem.remove(child)
    for i, src in enumerate(source_items):
        item = etree.SubElement(elem, "item")
        for attr, val in src.attrib.items():
            item.set(attr, val)
        item.text = android_format_text(lines[i] if i < len(lines) else "")
        item.tail = src.tail


def build_target_index(root: etree._Element) -> dict[str, etree._Element]:
    index: dict[str, etree._Element] = {}
    for elem in root:
        tag = getattr(elem, "tag", None)
        if callable(tag) or tag is None:
            continue
        if etree.QName(tag).localname in TRANSLATABLE_TAGS:
            name = elem.get("name")
            if name:
                index[name] = elem
    return index


def needs_translation(existing: etree._Element | None) -> bool:
    if existing is None:
        return True
    return existing.get(TOOLS_MACHINE_TRANSLATED) == "true"


def copy_structure(source: etree._Element) -> etree._Element:
    new = etree.Element(source.tag)
    for attr, val in source.attrib.items():
        if attr in (TOOLS_MACHINE_TRANSLATED, "translatable"):
            continue
        new.set(attr, val)
    new.text = source.text
    new.tail = "\n  "
    return new


_QUOTE_PAIRS = [
    ('"', '"'),
    ("“", "”"),  # LEFT/RIGHT DOUBLE QUOTATION MARK
    ("‘", "’"),  # LEFT/RIGHT SINGLE QUOTATION MARK
    ("'" , "'"),
    ("«", "»"),
    ("«", "»."),
    ("„", "“"),
    ("”", "”"),
]


def strip_outer_quotes(text: str, source: str) -> str:
    """Strip quotes the LLM added around the whole translation but that weren’t in the source."""
    for open_q, close_q in _QUOTE_PAIRS:
        if (
            text.startswith(open_q)
            and text.endswith(close_q)
            and len(text) > len(open_q) + len(close_q)
            and not source.startswith(open_q)
        ):
            return text[len(open_q):-len(close_q)].strip()
    return text


# ---------------------------------------------------------------------------
# LLM integration (async)
# ---------------------------------------------------------------------------

def build_prompt(text: str, context: str, target_lang: str, tgt_code: str) -> str:
    template = PROMPT_WITH_CONTEXT if context else PROMPT_NO_CONTEXT
    return template.format(
        source_lang=SOURCE_LANG,
        src_lang_code=SOURCE_LANG_CODE,
        target_lang=target_lang,
        tgt_lang_code=tgt_code,
        context_comment=context,
        text=text,
    )


# synchronous wrapper used by tests
def llm_translate(text: str, context: str, target_lang: str, tgt_code: str) -> str:
    client = ollama.Client(host=OLLAMA_HOST)
    response = client.chat(
        model=MODEL,
        messages=[{"role": "user", "content": build_prompt(text, context, target_lang, tgt_code)}],
        options={"temperature": TEMPERATURE},
    )
    return response.message.content.strip()


# ---------------------------------------------------------------------------
# Per-file processing: collect jobs, then apply results
# ---------------------------------------------------------------------------

@dataclass
class FileWork:
    source_file: Path
    target_file: Path
    target_lang: str
    tgt_code: str
    target_tree: etree._ElementTree
    target_root: etree._Element
    # list of (job, source_elem, existing_elem_or_None)
    jobs: list[tuple[TranslationJob, etree._Element, etree._Element | None]] = field(
        default_factory=list
    )


def collect_jobs(
    source_file: Path,
    target_file: Path,
    target_lang: str,
    tgt_code: str,
) -> FileWork | None:
    """Parse source and target XML, return a FileWork with pending jobs (no LLM calls)."""
    source_root = load_xml(source_file).getroot()

    if target_file.exists():
        target_tree = load_xml(target_file)
        target_root = ensure_tools_namespace(target_tree.getroot())
        target_tree._setroot(target_root)
    else:
        target_tree = new_resources_tree()
        target_root = target_tree.getroot()

    index = build_target_index(target_root)
    work = FileWork(source_file, target_file, target_lang, tgt_code, target_tree, target_root)

    for src_elem in source_root:
        tag = getattr(src_elem, "tag", None)
        if callable(tag) or tag is None:
            continue
        local = etree.QName(tag).localname
        if local not in TRANSLATABLE_TAGS:
            continue
        if src_elem.get("translatable") == "false":
            continue

        name = src_elem.get("name")
        if not name:
            continue

        existing = index.get(name)
        if not needs_translation(existing):
            continue

        source_text = get_element_text(src_elem)
        if not source_text.strip():
            continue

        context = preceding_comment(src_elem)

        if local == "string":
            job = TranslationJob(name, source_text, context, target_lang, tgt_code)
            work.jobs.append((job, src_elem, existing))
        else:
            # one job per <item>
            items = [
                android_unescape_text(c.text or "")
                for c in src_elem
                if etree.QName(c.tag).localname == "item"
            ]
            for i, item_text in enumerate(items):
                if not item_text.strip():
                    continue
                item_name = f"{name}[{i}]"
                job = TranslationJob(item_name, item_text, context, target_lang, tgt_code, i)
                work.jobs.append((job, src_elem, existing))

    return work if work.jobs else None


def sort_work_by_pending_strings(work: list[FileWork]) -> None:
    """Process locales with fewer pending strings first."""
    work.sort(key=lambda file_work: len(file_work.jobs))


def apply_results(work: FileWork) -> None:
    """Write translated text back into the XML tree and save the file."""
    target_root = work.target_root
    index = build_target_index(target_root)

    # Group array/plurals jobs back by parent name and original item index.
    parent_results: dict[str, dict[int, tuple[str, str]]] = {}
    singular_jobs: list[tuple[TranslationJob, etree._Element, etree._Element | None]] = []

    for job, src_elem, existing in work.jobs:
        local = etree.QName(src_elem.tag).localname
        if local == "string":
            singular_jobs.append((job, src_elem, existing))
        else:
            parent_name = src_elem.get("name", "")
            if job.item_index is not None:
                parent_results.setdefault(parent_name, {})[job.item_index] = (job.result, job.text)

    # Apply <string> results
    for job, src_elem, existing in singular_jobs:
        new_elem = copy_structure(src_elem)
        set_element_text(new_elem, strip_outer_quotes(job.result, job.text), src_elem)
        new_elem.set(TOOLS_MACHINE_TRANSLATED, "true")
        _replace_or_append(target_root, index, src_elem.get("name"), new_elem, existing)

    # Apply <string-array>/<plurals> results
    seen_parents: set[str] = set()
    for _, src_elem, existing in work.jobs:
        local = etree.QName(src_elem.tag).localname
        if local == "string":
            continue
        parent_name = src_elem.get("name", "")
        if parent_name in seen_parents:
            continue
        seen_parents.add(parent_name)
        source_items = [c for c in src_elem if etree.QName(c.tag).localname == "item"]
        results_by_index = parent_results.get(parent_name, {})
        translated_lines = []
        for i, src_item in enumerate(source_items):
            if i in results_by_index:
                result, source_text = results_by_index[i]
                translated_lines.append(strip_outer_quotes(result, source_text))
            else:
                translated_lines.append(src_item.text or "")
        translated = "\n".join(translated_lines)
        new_elem = copy_structure(src_elem)
        set_element_text(new_elem, translated, src_elem)
        new_elem.set(TOOLS_MACHINE_TRANSLATED, "true")
        _replace_or_append(target_root, index, parent_name, new_elem, existing)

    # Normalize whitespace: root.text indents first child, each element's tail
    # indents the next sibling (or dedents before </resources> for the last).
    children = list(target_root)
    if children:
        target_root.text = "\n  "
        for child in children[:-1]:
            child.tail = "\n  "
        children[-1].tail = "\n"

    work.target_file.parent.mkdir(parents=True, exist_ok=True)
    work.target_tree.write(
        str(work.target_file),
        encoding="utf-8",
        xml_declaration=True,
        pretty_print=True,
    )
    print(f"  Wrote {work.target_file}", flush=True)


def _replace_or_append(
    root: etree._Element,
    index: dict[str, etree._Element],
    name: str,
    new_elem: etree._Element,
    existing: etree._Element | None,
) -> None:
    if existing is not None and existing in root:
        idx = list(root).index(existing)
        root.remove(existing)
        root.insert(idx, new_elem)
    else:
        root.append(new_elem)
    index[name] = new_elem


# ---------------------------------------------------------------------------
# Async orchestration
# ---------------------------------------------------------------------------

@dataclass
class Stats:
    completed: int = 0
    total_time: float = 0.0

    def record(self, elapsed: float) -> None:
        self.completed += 1
        self.total_time += elapsed

    def avg(self) -> float | None:
        return self.total_time / self.completed if self.completed else None

    def eta_str(self, remaining: int) -> str:
        a = self.avg()
        if a is None or remaining <= 0:
            return "?"
        secs = a * remaining / MAX_CONCURRENT
        if secs < 60:
            return f"{secs:.0f}s"
        return f"{secs/60:.1f}m"


async def llm_translate_async_tracked(
    job: TranslationJob,
    semaphore: asyncio.Semaphore,
    client: ollama.AsyncClient,
    stats: Stats,
    locale_stats: Stats,
    locale_remaining: list[int],
    overall_remaining: list[int],
) -> None:
    prompt = build_prompt(job.text, job.context, job.target_lang, job.tgt_code)
    async with semaphore:
        t0 = time.monotonic()
        response = await client.chat(
            model=MODEL,
            messages=[{"role": "user", "content": prompt}],
            options={"temperature": TEMPERATURE},
        )
        elapsed = time.monotonic() - t0
    job.result = response.message.content.strip()
    stats.record(elapsed)
    locale_stats.record(elapsed)
    locale_remaining[0] -= 1
    overall_remaining[0] -= 1
    locale_eta = locale_stats.eta_str(locale_remaining[0])
    overall_eta = stats.eta_str(overall_remaining[0])
    print(
        f"  en→{job.tgt_code}  [{job.name}]  ({elapsed:.1f}s)"
        f"  locale: {locale_eta} left  overall: {overall_eta} left",
        flush=True,
    )


async def run_locale(
    work: FileWork,
    semaphore: asyncio.Semaphore,
    client: ollama.AsyncClient,
    stats: Stats,
    overall_remaining: list[int],
) -> None:
    locale_stats = Stats()
    locale_remaining = [len(work.jobs)]
    tasks = [
        llm_translate_async_tracked(
            job, semaphore, client, stats, locale_stats, locale_remaining, overall_remaining
        )
        for job, _, _ in work.jobs
    ]
    await asyncio.gather(*tasks)



# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def locale_suffix(dir_name: str) -> str:
    return dir_name[len("values-"):] if dir_name.startswith("values-") else ""


def main(res_dir: str) -> None:
    res_path = Path(res_dir).resolve()
    source_values = res_path / "values"
    if not source_values.is_dir():
        print(f"Error: {source_values} not found", file=sys.stderr)
        sys.exit(1)

    source_file = source_values / "strings.xml"
    if not source_file.exists():
        print(f"Error: {source_file} not found", file=sys.stderr)
        sys.exit(1)

    # Collect all jobs across all locales first
    all_work: list[FileWork] = []
    for values_dir in sorted(res_path.iterdir()):
        if not values_dir.is_dir():
            continue
        suffix = locale_suffix(values_dir.name)
        if not suffix or suffix not in LOCALE_MAP:
            continue

        target_lang, tgt_code = LOCALE_MAP[suffix]
        work = collect_jobs(source_file, values_dir / "strings.xml", target_lang, tgt_code)
        if work:
            all_work.append(work)

    if not all_work:
        print("Nothing to translate.", flush=True)
        return

    total = sum(len(w.jobs) for w in all_work)
    locale_count = len(all_work)
    sort_work_by_pending_strings(all_work)
    print(
        f"Translating {total} strings across {locale_count} locales "
        f"({MAX_CONCURRENT} parallel requests)…",
        flush=True,
    )

    done_locales = 0
    overall_remaining = [total]

    async def run_with_progress() -> None:
        nonlocal done_locales
        semaphore = asyncio.Semaphore(MAX_CONCURRENT)
        client = ollama.AsyncClient(host=OLLAMA_HOST)
        stats = Stats()

        for work in all_work:
            n = len(work.jobs)
            overall_eta = stats.eta_str(overall_remaining[0])
            print(
                f"\n[{done_locales + 1}/{locale_count}] {work.target_lang} ({work.tgt_code})"
                f" — {n} strings  overall ETA: {overall_eta}",
                flush=True,
            )
            await run_locale(work, semaphore, client, stats, overall_remaining)
            apply_results(work)
            done_locales += 1

    asyncio.run(run_with_progress())

    print("\nDone.", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Translate Android string resources using a local Ollama LLM."
    )
    parser.add_argument("res_dir", help="Path to app/src/main/res")
    args = parser.parse_args()
    main(args.res_dir)
