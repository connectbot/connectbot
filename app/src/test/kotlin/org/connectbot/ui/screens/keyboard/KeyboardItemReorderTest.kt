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

package org.connectbot.ui.screens.keyboard

import org.connectbot.data.keyboard.KeyboardDefaults
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardItemReorderTest {
    @Test fun reorderUsesExistingAnchorsForEqualSizeButtons() {
        val layout = KeyboardLayout(name = "Arrows")
        val buttons = (0..2).map { KeyboardItem(layoutId = layout.id, column = it) }
        val reordered = reorderKeyboardItems(layout, buttons, buttons[0].id, buttons[2].id)
        assertEquals(listOf(2, 0, 1), reordered.map { it.column })
        assertEquals(buttons.map { it.id }, reordered.map { it.id })
    }

    @Test fun spansArePreservedAndDisplacedButtonsFindFreeCells() {
        val layout = KeyboardLayout(name = "Spans")
        val wide = KeyboardItem(layoutId = layout.id, columnSpan = 2)
        val right = KeyboardItem(layoutId = layout.id, column = 2)
        val lower = KeyboardItem(layoutId = layout.id, row = 1)
        val reordered = reorderKeyboardItems(layout, listOf(wide, right, lower), lower.id, wide.id)
        KeyboardDefaults.validate(layout, reordered)
        assertEquals(0, reordered.single { it.id == lower.id }.row)
        assertEquals(0, reordered.single { it.id == lower.id }.column)
        assertEquals(2, reordered.single { it.id == wide.id }.columnSpan)
        assertEquals(1, reordered.single { it.id == wide.id }.row)
    }

    @Test fun reorderingLeavesOtherLayersUntouched() {
        val layout = KeyboardLayout(name = "Layers")
        val first = KeyboardItem(layoutId = layout.id)
        val second = KeyboardItem(layoutId = layout.id, column = 1)
        val other = KeyboardItem(layoutId = layout.id, layer = 4)
        val items = listOf(first, second, other)
        val reordered = reorderKeyboardItems(layout, items, first.id, second.id)
        assertEquals(other, reordered.single { it.id == other.id })
        assertEquals(1, reordered.single { it.id == first.id }.column)
        org.junit.Assert.assertTrue(runCatching { reorderKeyboardItems(layout, items, first.id, other.id) }.isFailure)
    }

    @Test fun crossSectionMovesPreserveIdentityAndCloseTheOldGap() {
        val layout = KeyboardLayout(name = "Pinned")
        val first = KeyboardItem(layoutId = layout.id)
        val second = KeyboardItem(layoutId = layout.id, column = 1)
        val dock = KeyboardItem(layoutId = layout.id, pinned = true)
        val items = listOf(first, second, dock)
        KeyboardDefaults.validate(layout, items)
        val moved = moveKeyboardItem(layout, items, first.id, dock.id)
        assertEquals(true, moved.single { it.id == first.id }.pinned)
        assertEquals(0, moved.single { it.id == first.id }.column)
        assertEquals(1, moved.single { it.id == dock.id }.column)
        assertEquals(0, moved.single { it.id == second.id }.column)
        val unpinned = pinKeyboardItem(layout, moved, first.id, false)
        assertEquals(false, unpinned.single { it.id == first.id }.pinned)
        assertEquals(1, unpinned.single { it.id == first.id }.column)
        assertEquals(0, unpinned.single { it.id == dock.id }.column)
        KeyboardDefaults.validate(layout, unpinned)
    }

    @Test fun canPinIntoAnEmptyRegionAndPinAnyAction() {
        val layout = KeyboardLayout(name = "Any action")
        listOf("key", "modifier", "app", "macro", "layer").forEach { kind ->
            val item = KeyboardItem(layoutId = layout.id, kind = kind, target = if (kind == "layer") "0" else "escape")
            val moved = pinKeyboardItem(layout, listOf(item), item.id, true).single()
            assertEquals(item.copy(pinned = true), moved)
        }
    }

    @Test fun draggingIntoAnotherRegionUsesTheDestinationRow() {
        val layout = KeyboardLayout(name = "Rows")
        val source = KeyboardItem(layoutId = layout.id, row = 4)
        val destination = KeyboardItem(layoutId = layout.id, pinned = true)
        val moved = moveKeyboardItem(layout, listOf(source, destination), source.id, destination.id)
        assertEquals(listOf(0, 0), moved.map { it.row })
        assertEquals(listOf(0, 1), moved.map { it.column })
    }
}
