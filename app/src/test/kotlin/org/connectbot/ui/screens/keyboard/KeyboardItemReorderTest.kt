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
}
