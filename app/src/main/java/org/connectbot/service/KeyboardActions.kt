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

import org.connectbot.data.keyboard.MacroAction
import org.connectbot.data.keyboard.MacroFormat
import org.connectbot.terminal.TerminalKeyCatalog
import org.connectbot.util.TerminalKeyModifiers

/** Runtime-only mapping; these library constants never enter persisted configuration. */
object KeyboardActions {
    fun key(target: String): Int = requireNotNull(TerminalKeyCatalog.byTarget(target)) { "Unsupported key" }.value
    fun presetModifiers(target: String, definition: String?): Int {
        if (definition == null) return TerminalKeyModifiers.NONE
        val action = MacroFormat.decode(definition).single() as MacroAction.Key
        require(action.key == target) { "Key definition does not match button" }
        return terminalModifiers(action.modifiers)
    }

    fun terminalModifiers(names: List<String>): Int = names.fold(TerminalKeyModifiers.NONE) { mask, name ->
        mask or when (name) {
            "ctrl" -> TerminalKeyModifiers.CTRL
            "alt" -> TerminalKeyModifiers.ALT
            "shift" -> TerminalKeyModifiers.SHIFT
            else -> error("Unsupported modifier")
        }
    }

    fun modifier(target: String): Int = when (target) {
        "ctrl" -> TerminalKeyListener.CTRL_ON
        "alt" -> TerminalKeyListener.ALT_ON
        "shift" -> TerminalKeyListener.SHIFT_ON
        else -> error("Unsupported modifier")
    }
}
