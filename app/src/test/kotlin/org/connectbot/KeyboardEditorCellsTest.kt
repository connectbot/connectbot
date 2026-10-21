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

package org.connectbot

import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.ui.components.keyboardEditorCells
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardEditorCellsTest {
    @Test fun invalidCoordinateDraftsDoNotCreateAnUnboundedGrid() {
        val layout = KeyboardLayout(name = "Draft")
        val valid = KeyboardItem(layoutId = layout.id)
        val invalid = listOf(valid.copy(row = Int.MAX_VALUE), valid.copy(column = Int.MAX_VALUE), valid.copy(columnSpan = Int.MAX_VALUE))
        assertEquals(listOf(valid), keyboardEditorCells(listOf(valid) + invalid, 0).mapNotNull { it.item })
    }

    @Test fun spansAndGapsPreserveCoordinatesAndOtherLayerDimensions() {
        val layout = KeyboardLayout(name = "Grid")
        val wide = KeyboardItem(layoutId = layout.id, column = 1, columnSpan = 2)
        val lower = KeyboardItem(layoutId = layout.id, row = 1, column = 3)
        val other = KeyboardItem(layoutId = layout.id, layer = 1, row = 2, column = 4)
        val cells = keyboardEditorCells(listOf(wide, lower, other), 0)
        assertEquals(listOf(wide, lower), cells.mapNotNull { it.item })
        assertEquals(listOf(1, 2, 2), cells.filter { it.row == 0 }.map { it.span })
        assertEquals(listOf(3, 1, 1), cells.filter { it.row == 1 }.map { it.span })
        assertEquals(5, cells.single { it.row == 2 }.span)
        assertEquals(cells.size, cells.map { it.key }.distinct().size)
    }
}
