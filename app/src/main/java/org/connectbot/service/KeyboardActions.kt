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

package org.connectbot.service

import org.connectbot.terminal.VTermKey

/** Runtime-only mapping; these library constants never enter persisted configuration. */
object KeyboardActions {
    fun key(target: String): Int = when (target) {
        "escape" -> VTermKey.ESCAPE

        "tab" -> VTermKey.TAB

        "enter" -> VTermKey.ENTER

        "backspace" -> VTermKey.BACKSPACE

        "delete" -> VTermKey.DEL

        "insert" -> VTermKey.INS

        "arrow_up" -> VTermKey.UP

        "arrow_down" -> VTermKey.DOWN

        "arrow_left" -> VTermKey.LEFT

        "arrow_right" -> VTermKey.RIGHT

        "home" -> VTermKey.HOME

        "end" -> VTermKey.END

        "page_up" -> VTermKey.PAGEUP

        "page_down" -> VTermKey.PAGEDOWN

        else -> {
            val number = target.removePrefix("f").toIntOrNull()
            require(target.startsWith("f") && number != null && number in 1..12) { "Unsupported key" }
            VTermKey.FUNCTION_1 + number - 1
        }
    }
    fun modifier(target: String): Int = when (target) {
        "ctrl" -> TerminalKeyListener.CTRL_ON
        "alt" -> TerminalKeyListener.ALT_ON
        "shift" -> TerminalKeyListener.SHIFT_ON
        else -> error("Unsupported modifier")
    }
}
