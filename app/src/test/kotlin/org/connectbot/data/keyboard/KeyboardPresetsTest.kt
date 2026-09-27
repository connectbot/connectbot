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

import org.connectbot.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardPresetsTest {
    private fun draft(preset: KeyboardPreset) = KeyboardPresets.create(preset, preset.id, Int::toString)

    @Test fun allPresetsHaveTwoFittedRowsAndEditableFunctionKeys() {
        KeyboardPreset.entries.forEach { preset ->
            val draft = draft(preset)
            assertTrue(draft.layout.fitToWidth)
            assertEquals(preset.id, draft.layout.presetKey)
            assertEquals(listOf(0, 1), KeyboardLayers.populated(draft.items))
            (0..1).forEach { layer ->
                val items = draft.items.filter { it.layer == layer }
                assertEquals(setOf(0, 1), items.map { it.row }.toSet())
                assertEquals(2, items.maxOf { it.row + it.rowSpan })
                assertEquals(1, items.count { it.kind == "layer" && it.target == "next" })
            }
            assertEquals((1..12).map { "f$it" }.toSet(), draft.items.filter { it.layer == 1 && it.target.startsWith("f") }.map { it.target }.toSet())
            assertEquals(0, KeyboardLayers.initial(draft.items))
            assertEquals(1, KeyboardLayers.destination(draft.items, 0, "next"))
            assertEquals(0, KeyboardLayers.destination(draft.items, 1, "next"))
            KeyboardDefaults.validate(draft.layout, draft.items)
        }
    }

    @Test fun citrusAndLastStopPreserveReferenceKeyOrder() {
        val citrus = draft(KeyboardPreset.CITRUS)
        assertEquals(listOf("escape", "character_47", "character_124", "character_45", "home", "arrow_up", "end", "page_up", "next"), citrus.items.filter { it.layer == 0 && it.row == 0 }.map { it.target })
        assertEquals(2, citrus.items.single { it.layer == 0 && it.target == "ctrl" }.columnSpan)
        val roomy = draft(KeyboardPreset.LAST_STOP)
        assertEquals(listOf("escape", "enter", "character_99", "home", "arrow_up", "end", "paste", "next"), roomy.items.filter { it.layer == 0 && it.row == 0 }.map { it.target })
    }

    @Test fun cliActionLabelsHaveExplicitSemanticKeyBindings() {
        val claw = draft(KeyboardPreset.CLAW)
        fun action(draft: KeyboardPresetDraft, name: Int) = MacroFormat.decode(draft.macros.single { it.name == name.toString() }.document).single()
        assertEquals(MacroAction.Key(key = "tab", modifiers = listOf("shift")), action(claw, R.string.keyboard_action_mode))
        assertEquals(MacroAction.Key(key = "character_106", modifiers = listOf("ctrl")), action(claw, R.string.keyboard_action_newline))
        assertEquals(MacroAction.Key(key = "character_111", modifiers = listOf("ctrl")), action(claw, R.string.keyboard_action_log))
        val scribe = draft(KeyboardPreset.SCRIBE)
        assertEquals(MacroAction.Key(key = "character_103", modifiers = listOf("ctrl")), action(scribe, R.string.keyboard_action_edit))
        assertEquals(MacroAction.Key(key = "character_111", modifiers = listOf("ctrl")), action(scribe, R.string.keyboard_action_copy))
        listOf(claw, scribe).forEach {
            assertEquals(MacroAction.Key(key = "character_99", modifiers = listOf("ctrl")), action(it, R.string.keyboard_action_stop))
            assertEquals(MacroAction.Key(key = "character_114", modifiers = listOf("ctrl")), action(it, R.string.keyboard_action_history))
        }
    }

    @Test fun copiesAndResetHaveFreshButtonsAndMacrosWithoutChangingLayoutIdentity() {
        val first = draft(KeyboardPreset.CLAW)
        val copy = draft(KeyboardPreset.CLAW)
        val reset = KeyboardPresets.create(KeyboardPreset.CLAW, "My renamed keyboard", Int::toString, first.layout.id)
        assertNotEquals(first.layout.id, copy.layout.id)
        assertEquals(first.layout.id, reset.layout.id)
        assertEquals("My renamed keyboard", reset.layout.name)
        assertTrue(first.items.map { it.id }.intersect((copy.items + reset.items).map { it.id }.toSet()).isEmpty())
        assertTrue(first.macros.map { it.id }.intersect((copy.macros + reset.macros).map { it.id }.toSet()).isEmpty())
        assertTrue(reset.items.all { it.layoutId == first.layout.id })
    }

    @Test fun everyPresetPlacesUpDirectlyAboveDown() {
        KeyboardPreset.entries.forEach { preset ->
            val keys = draft(preset).items.filter { it.layer == 0 }
            val up = keys.single { it.target == "arrow_up" }
            val down = keys.single { it.target == "arrow_down" }
            assertEquals(up.column, down.column)
            assertEquals(up.row + 1, down.row)
        }
    }
}
