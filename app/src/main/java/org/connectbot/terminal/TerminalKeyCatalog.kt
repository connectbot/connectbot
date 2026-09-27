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

package org.connectbot.terminal

/** A terminal key and its stable ID for configurable keyboard layouts. */
data class TerminalKey(val target: String, val value: Int, val character: Char? = null)

/** Key definitions shared by the configurable keyboard and automation editor. */
object TerminalKeyCatalog {
    val namedKeys: List<TerminalKey> = listOf(
        TerminalKey("enter", VTermKey.ENTER),
        TerminalKey("tab", VTermKey.TAB),
        TerminalKey("escape", VTermKey.ESCAPE),
        TerminalKey("backspace", VTermKey.BACKSPACE),
        TerminalKey("delete", VTermKey.DEL),
        TerminalKey("arrow_up", VTermKey.UP),
        TerminalKey("arrow_down", VTermKey.DOWN),
        TerminalKey("arrow_left", VTermKey.LEFT),
        TerminalKey("arrow_right", VTermKey.RIGHT),
        TerminalKey("home", VTermKey.HOME),
        TerminalKey("end", VTermKey.END),
        TerminalKey("insert", VTermKey.INS),
        TerminalKey("page_up", VTermKey.PAGEUP),
        TerminalKey("page_down", VTermKey.PAGEDOWN),
    ) + (1..12).map { TerminalKey("f$it", VTermKey.FUNCTION_0 + it) } + listOf(
        TerminalKey("keypad_0", VTermKey.KP_0),
        TerminalKey("keypad_1", VTermKey.KP_1),
        TerminalKey("keypad_2", VTermKey.KP_2),
        TerminalKey("keypad_3", VTermKey.KP_3),
        TerminalKey("keypad_4", VTermKey.KP_4),
        TerminalKey("keypad_5", VTermKey.KP_5),
        TerminalKey("keypad_6", VTermKey.KP_6),
        TerminalKey("keypad_7", VTermKey.KP_7),
        TerminalKey("keypad_8", VTermKey.KP_8),
        TerminalKey("keypad_9", VTermKey.KP_9),
        TerminalKey("keypad_multiply", VTermKey.KP_MULT),
        TerminalKey("keypad_plus", VTermKey.KP_PLUS),
        TerminalKey("keypad_comma", VTermKey.KP_COMMA),
        TerminalKey("keypad_minus", VTermKey.KP_MINUS),
        TerminalKey("keypad_period", VTermKey.KP_PERIOD),
        TerminalKey("keypad_divide", VTermKey.KP_DIVIDE),
        TerminalKey("keypad_enter", VTermKey.KP_ENTER),
        TerminalKey("keypad_equal", VTermKey.KP_EQUAL),
    )

    val characterKeys: List<TerminalKey> = (' '..'~').map(::characterKey)

    val entries: List<TerminalKey> = namedKeys + characterKeys

    val targets: List<String> = entries.map(TerminalKey::target)

    fun byTarget(target: String): TerminalKey? = entries.find { it.target == target }

    fun byValue(value: Int): TerminalKey? = entries.find { it.value == value }

    fun characterKey(character: Char): TerminalKey = TerminalKey("character_${character.code}", -character.code, character)
}
