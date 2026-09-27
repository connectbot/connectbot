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

package org.connectbot.service.automation

import org.connectbot.terminal.TerminalKeyCatalog
import org.connectbot.terminal.VTermKey
import org.connectbot.util.TerminalKeyModifiers

/** Shared by the editor and runner so their supported key encodings cannot diverge. */
object AutomationKeySupport {
    val functionNumbers = 1..12
    const val DELETE_CONTROL_CODE = '\u007f'.code

    // VTermKey values are nonnegative; negative values identify printable keys.
    val characterKeys = TerminalKeyCatalog.characterKeys.map { it.value }
    private const val ALL_MODIFIERS = TerminalKeyModifiers.SHIFT or TerminalKeyModifiers.CTRL or TerminalKeyModifiers.ALT

    private val terminalKeys = TerminalKeyCatalog.namedKeys.map { it.value }.toSet()

    fun isCharacterKey(key: Int): Boolean = key in characterKeys

    fun characterCodePoint(key: Int): Int {
        require(isCharacterKey(key))
        return -key
    }

    fun hasControlCode(key: Int): Boolean = isCharacterKey(key) &&
        (characterCodePoint(key).toChar().uppercaseChar() in '@'..'_' || characterCodePoint(key) == '?'.code)

    fun controlCodeForCharacter(key: Int): Int {
        require(hasControlCode(key))
        val codePoint = characterCodePoint(key).toChar().uppercaseChar().code
        return if (codePoint == '?'.code) DELETE_CONTROL_CODE else codePoint and CONTROL_CODE_MASK
    }

    private const val CONTROL_CODE_MASK = 0x1f

    fun isSupported(key: Int, modifiers: Int): Boolean = modifiers and ALL_MODIFIERS == modifiers &&
        (key in terminalKeys || key - VTermKey.FUNCTION_0 in functionNumbers || isCharacterKey(key))
}
