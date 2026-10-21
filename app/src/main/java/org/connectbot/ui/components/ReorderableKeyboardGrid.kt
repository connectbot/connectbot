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

package org.connectbot.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.data.keyboard.KeyboardMacro
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.util.UUID

/** Blank grid spans preserve physical coordinates without adding visible editor controls. */
internal data class KeyboardEditorCell(val row: Int, val column: Int, val span: Int, val item: KeyboardItem? = null) {
    val key: Any get() = item?.id ?: "gap/$row/$column"
}

internal fun keyboardEditorCells(items: List<KeyboardItem>, layer: Int): List<KeyboardEditorCell> {
    val displayItems = editorItems(items)
    val columns = displayItems.maxOfOrNull { it.column + it.columnSpan } ?: 0
    val rows = displayItems.maxOfOrNull { it.row + 1 } ?: 0
    val byRow = displayItems.filter { it.layer == layer }.groupBy { it.row }
    return buildList {
        repeat(rows) { row ->
            var column = 0
            byRow[row].orEmpty().sortedBy { it.column }.forEach { item ->
                if (item.column > column) add(KeyboardEditorCell(row, column, item.column - column))
                add(KeyboardEditorCell(row, item.column, item.columnSpan, item))
                column = item.column + item.columnSpan
            }
            if (column < columns) add(KeyboardEditorCell(row, column, columns - column))
        }
    }
}

// Invalid coordinate drafts still show a validation error, but must not create unbounded grid work.
private fun editorItems(items: List<KeyboardItem>) = items.filter {
    it.row in 0 until 1000 && it.column in 0 until 1000 && it.rowSpan == 1 && it.columnSpan in 1..(1000 - it.column)
}

@Composable
internal fun ReorderableKeyboardGrid(
    layout: KeyboardLayout,
    items: List<KeyboardItem>,
    macros: List<KeyboardMacro>,
    activeLayer: Int,
    onPress: (KeyboardItem) -> Unit,
    onReorder: (UUID, UUID) -> Unit,
    onMove: (KeyboardItem, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayItems = editorItems(items)
    val columns = (displayItems.maxOfOrNull { it.column + it.columnSpan } ?: 1).coerceAtLeast(1)
    val rows = (displayItems.maxOfOrNull { it.row + 1 } ?: 0).coerceAtLeast(1)
    val cells = keyboardEditorCells(displayItems, activeLayer)
    val haptic = LocalHapticFeedback.current
    val labelDirection = LocalLayoutDirection.current
    // Both the console and stored coordinates use physical left-to-right columns.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        key(layout.id, activeLayer) {
            val gridState = rememberLazyGridState()
            val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
                val source = from.key as? UUID
                val destination = to.key as? UUID
                if (source != null && destination != null) onReorder(source, destination)
            }
            BoxWithConstraints(modifier) {
                val viewportHeight = maxHeight
                val height = (layout.buttonHeight.takeIf { it.isFinite() && it > 0 } ?: 30f).coerceAtMost(1000f).dp
                val cellHeight = if (layout.fitToWidth) minOf(height, viewportHeight / rows) else height
                val gridWidth = if (layout.fitToWidth) maxWidth else ((layout.buttonWidth.takeIf { it.isFinite() && it > 0 } ?: 45f) * columns).coerceAtMost(16000f).dp
                val scroll = rememberScrollState()
                Box(Modifier.then(if (layout.fitToWidth) Modifier else Modifier.horizontalScroll(scroll))) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.width(gridWidth).height(minOf(cellHeight * rows, viewportHeight)),
                        userScrollEnabled = !layout.fitToWidth,
                    ) {
                        items(cells, key = { it.key }, span = { GridItemSpan(it.span) }) { cell ->
                            val item = cell.item
                            if (item == null) {
                                Spacer(Modifier.height(cellHeight))
                            } else {
                                ReorderableItem(reorderState, key = item.id) {
                                    CompositionLocalProvider(LocalLayoutDirection provides labelDirection) {
                                        KeyboardGridButton(
                                            layout = layout, item = item, macros = macros, items = items, layer = activeLayer,
                                            onPress = onPress, editing = true, onMove = onMove,
                                            modifier = Modifier.fillMaxWidth().height(cellHeight).longPressDraggableHandle(
                                                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                                onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
