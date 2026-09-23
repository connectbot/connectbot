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

import org.connectbot.terminal.VTermKey
import org.connectbot.util.TerminalKeyModifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationKeySupportTest {
    @Test
    fun acceptsSupportedKeysAndRejectsUnknownKeysOrModifierCombinations() {
        assertTrue(AutomationKeySupport.isSupported(VTermKey.ENTER, TerminalKeyModifiers.NONE))
        assertTrue(AutomationKeySupport.isSupported(VTermKey.ENTER, TerminalKeyModifiers.SHIFT or TerminalKeyModifiers.CTRL or TerminalKeyModifiers.ALT))
        assertTrue(AutomationKeySupport.isSupported(VTermKey.BACKSPACE, TerminalKeyModifiers.CTRL))
        for (number in AutomationKeySupport.functionNumbers) {
            assertTrue(AutomationKeySupport.isSupported(VTermKey.FUNCTION_0 + number, TerminalKeyModifiers.NONE))
        }
        for (key in AutomationKeySupport.characterKeys) {
            assertTrue(AutomationKeySupport.isSupported(key, TerminalKeyModifiers.SHIFT or TerminalKeyModifiers.CTRL or TerminalKeyModifiers.ALT))
        }
        assertEquals('\u0003'.code, AutomationKeySupport.controlCodeForCharacter(-'C'.code))
        assertEquals(AutomationKeySupport.DELETE_CONTROL_CODE, AutomationKeySupport.controlCodeForCharacter(-'?'.code))
        assertFalse(AutomationKeySupport.isSupported(VTermKey.FUNCTION_0, TerminalKeyModifiers.NONE))
        assertFalse(AutomationKeySupport.isSupported(VTermKey.FUNCTION_0 + AutomationKeySupport.functionNumbers.last + 1, TerminalKeyModifiers.NONE))
        assertFalse(AutomationKeySupport.isSupported('A'.code, TerminalKeyModifiers.CTRL))
        assertFalse(AutomationKeySupport.isSupported(-'a'.code, TerminalKeyModifiers.CTRL))
        assertFalse(AutomationKeySupport.isSupported(-'A'.code, 1 shl 4))
    }
}
