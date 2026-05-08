"""
Tests for translate.py — XML parsing, comment extraction, and human-translation protection.
No Ollama connection required; llm_translate is not called.
"""

from pathlib import Path

import pytest
from lxml import etree

from translations.translate import (
    FileWork,
    LOCALE_MAP,
    TOOLS_MACHINE_TRANSLATED,
    TOOLS_NS,
    TranslationJob,
    android_format_text,
    android_unescape_text,
    apply_results,
    build_target_index,
    collect_jobs,
    ensure_tools_namespace,
    get_element_text,
    load_xml,
    needs_translation,
    new_resources_tree,
    preceding_comment,
    set_element_text,
    sort_work_by_pending_strings,
    strip_outer_quotes,
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def parse(xml: str) -> etree._Element:
    parser = etree.XMLParser(remove_comments=False, remove_blank_text=False)
    return etree.fromstring(xml.encode(), parser)


def resources_xml(inner: str) -> str:
    """Returns a full XML document string (with declaration) for writing to files."""
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f'<resources xmlns:tools="{TOOLS_NS}">\n'
        f'{inner}\n'
        '</resources>\n'
    )


def resources_elem(inner: str) -> etree._Element:
    """Parses a <resources> element with comments preserved, without XML declaration."""
    xml = f'<resources xmlns:tools="http://schemas.android.com/tools">\n{inner}\n</resources>'
    return parse(xml)


# ---------------------------------------------------------------------------
# preceding_comment
# ---------------------------------------------------------------------------

class TestPrecedingComment:
    def test_picks_up_comment_before_element(self):
        root = resources_elem('<!-- Window title -->\n<string name="title">Hello</string>')
        elem = root.find("string")
        assert preceding_comment(elem) == "Window title"

    def test_returns_empty_when_no_comment(self):
        root = resources_elem('<string name="title">Hello</string>')
        elem = root.find("string")
        assert preceding_comment(elem) == ""

    def test_stops_at_intervening_element(self):
        root = resources_elem(
            '<!-- Old comment -->\n'
            '<string name="other">Other</string>\n'
            '<string name="title">Hello</string>'
        )
        elem = root.findall("string")[1]
        assert preceding_comment(elem) == ""

    def test_strips_whitespace(self):
        root = resources_elem('<!--   padded comment   -->\n<string name="x">X</string>')
        elem = root.find("string")
        assert preceding_comment(elem) == "padded comment"


# ---------------------------------------------------------------------------
# needs_translation
# ---------------------------------------------------------------------------

class TestNeedsTranslation:
    def test_missing_element_needs_translation(self):
        assert needs_translation(None) is True

    def test_machine_translated_true_needs_translation(self):
        elem = parse(
            f'<string xmlns:tools="{TOOLS_NS}" '
            f'tools:machine_translated="true" name="x">foo</string>'
        )
        assert needs_translation(elem) is True

    def test_human_translated_skipped(self):
        elem = parse('<string name="x">foo</string>')
        assert needs_translation(elem) is False

    def test_machine_translated_false_skipped(self):
        elem = parse(
            f'<string xmlns:tools="{TOOLS_NS}" '
            f'tools:machine_translated="false" name="x">foo</string>'
        )
        assert needs_translation(elem) is False


# ---------------------------------------------------------------------------
# get_element_text / set_element_text
# ---------------------------------------------------------------------------

class TestElementText:
    def test_get_string(self):
        elem = parse('<string name="x">Hello world</string>')
        assert get_element_text(elem) == "Hello world"

    def test_get_string_array(self):
        elem = parse(
            "<string-array name='x'>"
            "<item>One</item><item>Two</item>"
            "</string-array>"
        )
        assert get_element_text(elem) == "One\nTwo"

    def test_set_string(self):
        elem = parse('<string name="x">Hello</string>')
        source = parse('<string name="x">Hello</string>')
        set_element_text(elem, "Hola", source)
        assert elem.text == "Hola"

    def test_set_string_array(self):
        source = parse(
            "<string-array name='x'>"
            "<item>One</item><item>Two</item>"
            "</string-array>"
        )
        target = parse(
            "<string-array name='x'>"
            "<item>Eins</item><item>Zwei</item>"
            "</string-array>"
        )
        set_element_text(target, "Uno\nDos", source)
        items = target.findall("item")
        assert [i.text for i in items] == ["Uno", "Dos"]

    def test_set_plurals_preserves_item_attributes(self):
        source = parse(
            "<plurals name='x'>"
            "<item quantity='one'>One</item><item quantity='other'>Many</item>"
            "</plurals>"
        )
        target = parse(
            "<plurals name='x'>"
            "<item quantity='one'>Uno</item><item quantity='other'>Varios</item>"
            "</plurals>"
        )
        set_element_text(target, "Ein\nViele", source)
        items = target.findall("item")
        assert [i.get("quantity") for i in items] == ["one", "other"]
        assert [i.text for i in items] == ["Ein", "Viele"]

    @pytest.mark.parametrize(
        ("raw", "decoded"),
        [
            (r"Don\'t send \@home\nnow", "Don't send @home\nnow"),
            ('"Use  two spaces"', "Use  two spaces"),
            ("Use  two\nspaces", "Use two spaces"),
            ("Line\nbreak\tTab", "Line break Tab"),
            ('"  padded  "', "  padded  "),
            ('"unbalanced quote  ', "unbalanced quote "),
            (r"Escaped \" quote", 'Escaped " quote'),
            (r"Path C:\\Users", r"Path C:\Users"),
            (r"\u0041", "A"),
            (r"\u41", "A"),
            (r"Bad \q escape", "Bad  escape"),
            ("5%1$s complete", "5%1$s complete"),
            ("Use <tag> literally", "Use &lt;tag&gt; literally"),
            ("FAQ & Help", "FAQ & Help"),
        ],
    )
    def test_android_unescape_text_matches_android_rules(self, raw, decoded):
        assert android_unescape_text(raw) == decoded

    @pytest.mark.parametrize(
        ("decoded", "formatted"),
        [
            ("Simple text", "Simple text"),
            ("Don't send @home\nnow", r"Don\'t send \@home\nnow"),
            ('Don\'t say "hi" @home', r"Don\'t say \"hi\" \@home"),
            (r"Path C:\Users", r"Path C:\\Users"),
            ("Column\tvalue", r"Column\tvalue"),
            ("Use  two spaces", '"Use  two spaces"'),
            (" leading", r'" leading"'),
            ("trailing ", r'"trailing "'),
            ("Line\nbreak", r"Line\nbreak"),
            ("5%1$s complete", "5%1$s complete"),
            ("Use &lt;tag&gt; literally", "Use <tag> literally"),
            ("FAQ & Help", "FAQ & Help"),
            ("FAQ &amp; Help", "FAQ &amp; Help"),
        ],
    )
    def test_android_format_text_matches_android_rules(self, decoded, formatted):
        assert android_format_text(decoded) == formatted

    def test_set_string_preserves_android_lt_gt_entities(self):
        elem = parse('<string name="x">Use &lt;tag&gt;</string>')
        source = parse('<string name="x">Use &lt;tag&gt;</string>')
        set_element_text(elem, "Use &lt;tag&gt;", source)
        xml = etree.tostring(elem, encoding="unicode")
        assert ">Use &lt;tag&gt;<" in xml
        assert "&amp;lt;" not in xml

    def test_set_string_serializes_raw_ampersand(self):
        elem = parse('<string name="x">FAQ &amp; Help</string>')
        source = parse('<string name="x">FAQ &amp; Help</string>')
        set_element_text(elem, "FAQ & Help", source)
        xml = etree.tostring(elem, encoding="unicode")
        assert ">FAQ &amp; Help<" in xml
        assert "&amp;amp;" not in xml

    def test_set_string_preserves_translator_amp_entity_as_literal_text(self):
        elem = parse('<string name="x">FAQ &amp; Help</string>')
        source = parse('<string name="x">FAQ &amp; Help</string>')
        set_element_text(elem, "FAQ &amp; Help", source)
        xml = etree.tostring(elem, encoding="unicode")
        assert ">FAQ &amp;amp; Help<" in xml

    def test_set_string_uses_android_escaping(self):
        elem = parse('<string name="x">Hello</string>')
        source = parse('<string name="x">Hello</string>')
        set_element_text(elem, 'Don\'t say "hi" @home', source)
        assert elem.text == r"Don\'t say \"hi\" \@home"


# ---------------------------------------------------------------------------
# build_target_index
# ---------------------------------------------------------------------------

class TestBuildTargetIndex:
    def test_indexes_string_elements(self):
        root = resources_elem(
            '<string name="foo">Foo</string>\n'
            '<string name="bar">Bar</string>'
        )
        idx = build_target_index(root)
        assert set(idx.keys()) == {"foo", "bar"}

    def test_ignores_comments(self):
        root = resources_elem('<!-- comment -->\n<string name="foo">Foo</string>')
        idx = build_target_index(root)
        assert list(idx.keys()) == ["foo"]

    def test_ignores_non_translatable_tags(self):
        root = parse(resources_xml('<color name="red">#FF0000</color>'))
        idx = build_target_index(root)
        assert idx == {}


# ---------------------------------------------------------------------------
# ensure_tools_namespace
# ---------------------------------------------------------------------------

class TestEnsureToolsNamespace:
    def test_new_resources_tree_has_tools_namespace(self):
        tree = new_resources_tree()
        root = tree.getroot()
        assert TOOLS_NS in root.nsmap.values()

    def test_adds_namespace_to_root_without_it(self):
        root = parse("<resources><string name='x'>X</string></resources>")
        new_root = ensure_tools_namespace(root)
        assert TOOLS_NS in new_root.nsmap.values()
        assert new_root.find("string[@name='x']") is not None

    def test_noop_when_namespace_already_present(self):
        root = parse(f'<resources xmlns:tools="{TOOLS_NS}"></resources>')
        result = ensure_tools_namespace(root)
        assert result is root

    def test_namespace_present_after_translation(self, tmp_path):
        src = tmp_path / "values" / "strings.xml"
        src.parent.mkdir(parents=True)
        src.write_text(resources_xml('<string name="hello">Hello</string>'), encoding="utf-8")

        tgt = tmp_path / "values-de" / "strings.xml"
        tgt.parent.mkdir(parents=True)

        work = collect_jobs(src, tgt, "German", "de")
        assert work is not None
        for job, _, _ in work.jobs:
            job.result = "Hallo"
        apply_results(work)

        root = load_xml(tgt).getroot()
        assert TOOLS_NS in root.nsmap.values()


# ---------------------------------------------------------------------------
# ---------------------------------------------------------------------------
# strip_outer_quotes
# ---------------------------------------------------------------------------

class TestStripOuterQuotes:
    def test_strips_smart_double_quotes_added_by_llm(self):
        assert strip_outer_quotes("“Hello”", "Hello") == "Hello"

    def test_strips_ascii_double_quotes_added_by_llm(self):
        assert strip_outer_quotes('"Hello"', "Hello") == "Hello"

    def test_strips_ascii_single_quotes_added_by_llm(self):
        assert strip_outer_quotes("'Hello'", "Hello") == "Hello"

    def test_no_quotes_unchanged(self):
        assert strip_outer_quotes("Hello", "Hello") == "Hello"

    def test_mismatched_quotes_unchanged(self):
        assert strip_outer_quotes("“Hello’", "Hello") == "“Hello’"

    def test_source_quoted_preserves_translation_quotes(self):
        # Source has quotes → LLM quoting is intentional, not wrapping
        assert strip_outer_quotes('“Esc” ist ausgewählt', '“Esc” is selected') == '“Esc” ist ausgewählt'

    def test_strips_and_trims_inner_whitespace(self):
        assert strip_outer_quotes('“  Hello  ”', "Hello") == "Hello"

    def test_strips_wrapping_guillemet_and_llm_added_sentence_period(self):
        assert strip_outer_quotes("«Hallo».", "Hello") == "Hallo"


# ---------------------------------------------------------------------------
# collect_jobs + apply_results — integration tests (no LLM)
# ---------------------------------------------------------------------------

def _run(src: Path, tgt: Path, translations: dict[str, str], target_lang="German", tgt_code="de") -> None:
    """Helper: collect jobs, fill results from dict, apply."""
    work = collect_jobs(src, tgt, target_lang, tgt_code)
    if work:
        for job, _, _ in work.jobs:
            job.result = translations.get(job.name, f"[{job.text}]")
        apply_results(work)


class TestTranslationPipeline:
    """Tests that collect_jobs + apply_results correctly handle all cases."""

    def _src(self, tmp_path: Path, content: str) -> Path:
        f = tmp_path / "values" / "strings.xml"
        f.parent.mkdir(parents=True)
        f.write_text(content, encoding="utf-8")
        return f

    def _tgt(self, tmp_path: Path, content: str) -> Path:
        f = tmp_path / "values-de" / "strings.xml"
        f.parent.mkdir(parents=True)
        f.write_text(content, encoding="utf-8")
        return f

    def test_missing_string_is_translated(self, tmp_path):
        src = self._src(tmp_path, resources_xml('<string name="hello">Hello</string>'))
        tgt = tmp_path / "values-de" / "strings.xml"
        tgt.parent.mkdir(parents=True)

        _run(src, tgt, {"hello": "Hallo"})

        root = load_xml(tgt).getroot()
        elem = root.find("string[@name='hello']")
        assert elem is not None
        assert elem.text == "Hallo"
        assert elem.get(TOOLS_MACHINE_TRANSLATED) == "true"

    def test_human_translated_string_is_not_overwritten(self, tmp_path):
        src = self._src(tmp_path, resources_xml('<string name="hello">Hello</string>'))
        tgt = self._tgt(tmp_path, resources_xml('<string name="hello">Hallo</string>'))

        work = collect_jobs(src, tgt, "German", "de")
        assert work is None, "no jobs should be collected for human-translated strings"

        root = load_xml(tgt).getroot()
        assert root.find("string[@name='hello']").text == "Hallo"

    def test_machine_translated_string_is_retranslated(self, tmp_path):
        src = self._src(tmp_path, resources_xml('<string name="hello">Hello</string>'))
        tgt = self._tgt(tmp_path,
            f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<resources xmlns:tools="{TOOLS_NS}">\n'
            f'<string name="hello" tools:machine_translated="true">Alt</string>\n'
            f'</resources>\n'
        )

        _run(src, tgt, {"hello": "Neu"})

        root = load_xml(tgt).getroot()
        elem = root.find("string[@name='hello']")
        assert elem.text == "Neu"
        assert elem.get(TOOLS_MACHINE_TRANSLATED) == "true"

    def test_comment_is_collected_as_context(self, tmp_path):
        src = self._src(tmp_path, resources_xml(
            '<!-- Window title for the main screen -->\n'
            '<string name="title">Hello</string>'
        ))
        tgt = tmp_path / "values-de" / "strings.xml"
        tgt.parent.mkdir(parents=True)

        work = collect_jobs(src, tgt, "German", "de")
        assert work is not None
        assert len(work.jobs) == 1
        job, _, _ = work.jobs[0]
        assert job.context == "Window title for the main screen"

    def test_translatable_false_is_skipped(self, tmp_path):
        src = self._src(tmp_path, resources_xml(
            '<string name="app_name" translatable="false">ConnectBot</string>'
        ))
        tgt = tmp_path / "values-de" / "strings.xml"
        tgt.parent.mkdir(parents=True)

        work = collect_jobs(src, tgt, "German", "de")
        assert work is None
        assert not tgt.exists()

    def test_llm_smart_quotes_stripped(self, tmp_path):
        src = self._src(tmp_path, resources_xml('<string name="desc">Simple app.</string>'))
        tgt = tmp_path / "values-de" / "strings.xml"
        tgt.parent.mkdir(parents=True)

        _run(src, tgt, {"desc": "“Einfache App.”"})

        root = load_xml(tgt).getroot()
        assert root.find("string[@name='desc']").text == "Einfache App."

    def test_empty_array_item_does_not_shift_translations(self, tmp_path):
        src = self._src(
            tmp_path,
            resources_xml(
                "<string-array name='items'>"
                "<item>One</item><item></item><item>Three</item>"
                "</string-array>"
            ),
        )
        tgt = tmp_path / "values-de" / "strings.xml"
        tgt.parent.mkdir(parents=True)

        _run(src, tgt, {"items[0]": "Eins", "items[2]": "Drei"})

        root = load_xml(tgt).getroot()
        items = root.find("string-array[@name='items']").findall("item")
        assert [item.text or "" for item in items] == ["Eins", "", "Drei"]

    def test_array_item_jobs_use_android_decoded_text(self, tmp_path):
        src = self._src(
            tmp_path,
            resources_xml(
                "<string-array name='items'>"
                r"<item>Don\'t send \@home</item>"
                r"<item>&quot;Use  two spaces&quot;</item>"
                "</string-array>"
            ),
        )
        tgt = tmp_path / "values-de" / "strings.xml"
        tgt.parent.mkdir(parents=True)

        work = collect_jobs(src, tgt, "German", "de")

        assert work is not None
        assert [(job.name, job.text) for job, _, _ in work.jobs] == [
            ("items[0]", "Don't send @home"),
            ("items[1]", "Use  two spaces"),
        ]

    def test_output_uses_2space_indent(self, tmp_path):
        src = self._src(tmp_path, resources_xml('<string name="hello">Hello</string>'))
        tgt = tmp_path / "values-de" / "strings.xml"
        tgt.parent.mkdir(parents=True)

        _run(src, tgt, {"hello": "Hallo"})

        raw = tgt.read_text(encoding="utf-8")
        assert "  <string" in raw
        assert "    <string" not in raw
        assert raw.endswith("</resources>\n")


class TestLocaleMap:
    def test_includes_legacy_android_locale_aliases(self):
        assert LOCALE_MAP["in"] == ("Indonesian", "id")
        assert LOCALE_MAP["iw"] == ("Hebrew", "he")


class TestWorkOrdering:
    def _work(self, tmp_path: Path, code: str, job_count: int) -> FileWork:
        target_tree = new_resources_tree()
        return FileWork(
            tmp_path / "values" / "strings.xml",
            tmp_path / f"values-{code}" / "strings.xml",
            code,
            code,
            target_tree,
            target_tree.getroot(),
            [
                (
                    TranslationJob(f"name_{i}", "Text", "", code, code),
                    etree.Element("string", name=f"name_{i}"),
                    None,
                )
                for i in range(job_count)
            ],
        )

    def test_sort_work_by_pending_strings_ascending(self, tmp_path):
        two_jobs = self._work(tmp_path, "two", 2)
        one_job = self._work(tmp_path, "one", 1)
        three_jobs = self._work(tmp_path, "three", 3)
        other_one_job = self._work(tmp_path, "other-one", 1)
        work = [two_jobs, one_job, three_jobs, other_one_job]

        sort_work_by_pending_strings(work)

        assert work == [one_job, other_one_job, two_jobs, three_jobs]
