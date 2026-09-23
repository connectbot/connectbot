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
import java.util.UUID

/** Reassign ordered buttons to existing grid anchors, leaving spans and IDs intact. */
internal fun reorderKeyboardItems(layout: KeyboardLayout, items: List<KeyboardItem>, from: UUID, to: UUID): List<KeyboardItem> {
    KeyboardDefaults.validate(layout, items)
    val anchors = items.sortedWith(compareBy<KeyboardItem> { it.row }.thenBy { it.column }.thenBy { it.id })
    val source = anchors.indexOfFirst { it.id == from }
    val destination = anchors.indexOfFirst { it.id == to }
    require(source >= 0 && destination >= 0) { "Button no longer exists" }
    if (source == destination) return items

    val ordered = anchors.toMutableList().apply { add(destination, removeAt(source)) }
    val columns = items.maxOf { it.column + it.columnSpan }
    val placed = mutableListOf<KeyboardItem>()
    ordered.forEachIndexed { index, item ->
        val anchor = anchors[index]
        var row = anchor.row
        var column = anchor.column
        while (row + item.rowSpan <= 1000 && (row + item.rowSpan) * layout.buttonHeight <= 3000) {
            if (column + item.columnSpan <= columns) {
                val candidate = item.copy(row = row, column = column)
                val overlaps = placed.any { other ->
                    candidate.row < other.row + other.rowSpan && other.row < candidate.row + candidate.rowSpan &&
                        candidate.column < other.column + other.columnSpan && other.column < candidate.column + candidate.columnSpan
                }
                if (!overlaps) {
                    placed += candidate
                    break
                }
            }
            column++
            if (column >= columns) {
                row++
                column = 0
            }
        }
        require(placed.size == index + 1) { "No space for this button in the grid" }
    }
    KeyboardDefaults.validate(layout, placed)
    return items.map { original -> placed.first { it.id == original.id } }
}
