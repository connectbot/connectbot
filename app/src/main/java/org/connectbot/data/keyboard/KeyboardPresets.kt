/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.data.keyboard

import androidx.annotation.StringRes
import org.connectbot.R
import org.connectbot.terminal.TerminalKeyCatalog
import java.util.UUID

enum class KeyboardPreset(val id: String, @param:StringRes val nameRes: Int, @param:StringRes val subtitleRes: Int) {
    CITRUS("citrus", R.string.keyboard_preset_citrus, R.string.keyboard_preset_citrus_subtitle),
    LAST_STOP("last_stop", R.string.keyboard_preset_last_stop, R.string.keyboard_preset_last_stop_subtitle),
    CLAW("claw", R.string.keyboard_preset_claw, R.string.keyboard_preset_claw_subtitle),
    SCRIBE("scribe", R.string.keyboard_preset_scribe, R.string.keyboard_preset_scribe_subtitle),
}

data class KeyboardPresetDraft(val layout: KeyboardLayout, val items: List<KeyboardItem>, val macros: List<KeyboardMacro>)

object KeyboardPresets {
    fun find(id: String?): KeyboardPreset? = KeyboardPreset.entries.find { it.id == id }

    /** Definitions are copied into an unsaved draft, including fresh shortcut and button IDs. */
    fun create(preset: KeyboardPreset, name: String, label: (Int) -> String, id: UUID = UUID.randomUUID()): KeyboardPresetDraft {
        val layout = KeyboardLayout(id = id, name = name, buttonHeight = if (preset == KeyboardPreset.CITRUS) 36f else 44f, fitToWidth = true, presetKey = preset.id)
        val macros = mutableListOf<KeyboardMacro>()
        fun key(target: String) = KeyboardItem(layoutId = id, target = target)
        fun character(value: Char) = key(TerminalKeyCatalog.characterKey(value).target)
        fun modifier(target: String) = KeyboardItem(layoutId = id, kind = "modifier", target = target)
        fun app(target: String) = KeyboardItem(layoutId = id, kind = "app", target = target)
        fun fn() = KeyboardItem(layoutId = id, kind = "layer", target = "next")
        fun shortcut(target: String, name: Int, modifier: String = "ctrl"): KeyboardItem {
            val macro = KeyboardMacro(name = label(name), document = MacroFormat.encode(listOf(MacroAction.Key(key = target, modifiers = listOf(modifier)))))
            macros += macro
            return key(target).copy(macroId = macro.id)
        }
        fun ctrl(value: Char, name: Int) = shortcut(TerminalKeyCatalog.characterKey(value).target, name)
        fun stop() = ctrl('c', R.string.keyboard_action_stop)
        fun history() = ctrl('r', R.string.keyboard_action_history)
        val arrows = listOf(key("arrow_left"), key("arrow_down"), key("arrow_right"))
        val lower = listOf(key("tab"), modifier("ctrl"), modifier("alt"))
        val rows = when (preset) {
            KeyboardPreset.CITRUS -> listOf(
                listOf(key("escape"), character('/'), character('|'), character('-'), key("home"), key("arrow_up"), key("end"), key("page_up"), fn()),
                listOf(key("tab"), modifier("ctrl").copy(columnSpan = 2), modifier("alt")) + arrows + key("page_down") + app("toggle_ime"),
            )

            KeyboardPreset.LAST_STOP -> listOf(
                listOf(key("escape"), key("enter"), stop(), key("home"), key("arrow_up"), key("end"), app("paste"), fn()),
                lower + arrows + key("backspace") + app("toggle_ime"),
            )

            KeyboardPreset.CLAW -> listOf(
                listOf(key("escape"), stop(), shortcut("tab", R.string.keyboard_action_mode, "shift"), ctrl('j', R.string.keyboard_action_newline), ctrl('o', R.string.keyboard_action_log), key("arrow_up"), history(), fn()),
                lower + character('/') + arrows + key("enter"),
            )

            KeyboardPreset.SCRIBE -> listOf(
                listOf(key("escape"), stop(), ctrl('g', R.string.keyboard_action_edit), ctrl('o', R.string.keyboard_action_copy), history(), key("arrow_up"), character('@'), fn()),
                lower + character('/') + arrows + key("enter"),
            )
        }
        val functionRows = listOf(
            listOf(key("escape")) + (1..6).map { key("f$it") } + (if (preset == KeyboardPreset.CITRUS) listOf(key("page_up")) else emptyList()) + fn(),
            (if (preset == KeyboardPreset.CITRUS) listOf(key("tab")) else emptyList()) + listOf(modifier("ctrl"), modifier("alt")) + (7..12).map { key("f$it") },
        )
        val items = listOf(rows, functionRows).flatMapIndexed { layer, layerRows ->
            layerRows.flatMapIndexed { row, buttons ->
                var column = 0
                buttons.map { button -> button.copy(layer = layer, row = row, column = column).also { column += it.columnSpan } }
            }
        }
        KeyboardDefaults.validate(layout, items)
        return KeyboardPresetDraft(layout, items, macros)
    }
}
