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
import org.connectbot.terminal.VTermKey
import org.connectbot.util.TerminalKeyModifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardActionsTest {
    @Test
    fun presetAndTransientModifiersCombineForTheSelectedKey() {
        val document = MacroFormat.encode(listOf(MacroAction.Key(key = "character_97", modifiers = listOf("ctrl"))))
        val preset = KeyboardActions.presetModifiers("character_97", document)
        assertEquals(-'a'.code, KeyboardActions.key("character_97"))
        assertEquals(TerminalKeyModifiers.CTRL, preset)
        assertEquals(TerminalKeyModifiers.CTRL or TerminalKeyModifiers.ALT, preset or TerminalKeyModifiers.ALT)
        assertEquals(VTermKey.ESCAPE, KeyboardActions.key("escape"))
        assertEquals(TerminalKeyModifiers.NONE, KeyboardActions.presetModifiers("escape", null))
    }

    @Test
    fun mismatchedOrMultiStepDefinitionCannotSupplyKeyModifiers() {
        val mismatched = MacroFormat.encode(listOf(MacroAction.Key(key = "enter", modifiers = listOf("alt"))))
        val multiStep = MacroFormat.encode(listOf(MacroAction.Key(key = "enter"), MacroAction.Text("extra")))
        assertTrue(runCatching { KeyboardActions.presetModifiers("character_97", mismatched) }.isFailure)
        assertTrue(runCatching { KeyboardActions.presetModifiers("enter", multiStep) }.isFailure)
    }
}
