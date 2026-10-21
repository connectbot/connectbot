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
    val sourceItem = requireNotNull(items.firstOrNull { it.id == from }) { "Button no longer exists" }
    val layer = sourceItem.layer
    require(items.firstOrNull { it.id == to }?.layer == layer) { "Buttons must belong to the same layer" }
    require(items.first { it.id == to }.pinned == sourceItem.pinned) { "Buttons must belong to the same region" }
    val anchors = items.filter { it.layer == layer && it.pinned == sourceItem.pinned }.sortedWith(compareBy<KeyboardItem> { it.row }.thenBy { it.column }.thenBy { it.id })
    val source = anchors.indexOfFirst { it.id == from }
    val destination = anchors.indexOfFirst { it.id == to }
    require(source >= 0 && destination >= 0) { "Button no longer exists" }
    if (source == destination) return items

    val ordered = anchors.toMutableList().apply { add(destination, removeAt(source)) }
    val columns = items.filter { it.pinned == sourceItem.pinned }.maxOf { it.column + it.columnSpan }
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
    val result = items.map { original -> placed.find { it.id == original.id } ?: original }
    KeyboardDefaults.validate(layout, result)
    return result
}

/** Move a button between regions, closing its old slot and appending it to the target row. */
internal fun pinKeyboardItem(layout: KeyboardLayout, items: List<KeyboardItem>, id: UUID, pinned: Boolean, targetRow: Int? = null): List<KeyboardItem> {
    val item = items.first { it.id == id }
    if (item.pinned == pinned) return items
    val row = targetRow ?: item.row
    val column = items.filter { it.pinned == pinned && it.layer == item.layer && it.row == row }
        .maxOfOrNull { it.column + it.columnSpan } ?: 0
    val result = items.map { other ->
        when {
            other.id == id -> other.copy(pinned = pinned, row = row, column = column)
            other.pinned == item.pinned && other.layer == item.layer && other.row == item.row && other.column > item.column -> other.copy(column = other.column - item.columnSpan)
            else -> other
        }
    }
    KeyboardDefaults.validate(layout, result)
    return result
}

/** Cross-section moves use the same reorder operation after switching the stored region. */
internal fun moveKeyboardItem(layout: KeyboardLayout, items: List<KeyboardItem>, from: UUID, to: UUID): List<KeyboardItem> {
    val source = items.first { it.id == from }
    val destination = items.first { it.id == to }
    require(source.layer == destination.layer) { "Buttons must belong to the same layer" }
    val moved = if (source.pinned == destination.pinned) items else pinKeyboardItem(layout, items, from, destination.pinned, targetRow = destination.row)
    return reorderKeyboardItems(layout, moved, from, to)
}
