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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.data.keyboard.KeyboardMacro
import org.connectbot.data.keyboard.fittedKeyboardItems
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.util.UUID

/** Blank grid spans preserve physical coordinates without adding visible editor controls. */
internal data class KeyboardEditorCell(val row: Int, val column: Int, val span: Int, val item: KeyboardItem? = null) {
    val key: Any get() = item?.id ?: "gap/$row/$column"
}

internal fun keyboardEditorCells(items: List<KeyboardItem>, layer: Int, gridColumns: Int? = null): List<KeyboardEditorCell> {
    val displayItems = editorItems(items)
    val columns = gridColumns ?: (displayItems.maxOfOrNull { it.column + it.columnSpan } ?: 0)
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
    onPin: (UUID, Boolean) -> Unit = { _, _ -> },
) {
    val displayItems = editorItems(if (layout.fitToWidth) fittedKeyboardItems(items) else items)
    val dataColumns = (displayItems.maxOfOrNull { it.column + it.columnSpan } ?: 1).coerceAtLeast(1)
    val haptic = LocalHapticFeedback.current
    val labelDirection = LocalLayoutDirection.current
    // Both the console and stored coordinates use physical left-to-right columns.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        key(layout.id, activeLayer) {
            val gridState = rememberLazyGridState()
            BoxWithConstraints(modifier) {
                val viewportHeight = maxHeight
                val buttonWidth = layout.buttonWidth.takeIf { it.isFinite() && it > 0 } ?: 45f
                val viewportColumns = (maxWidth.value / buttonWidth).toInt().coerceIn(1, 1000)
                val columns = if (layout.fitToWidth) dataColumns else maxOf(dataColumns, viewportColumns)
                val sectionKinds = if (layout.fitToWidth) listOf(false) else listOf(false, true)
                val sections = sectionKinds.associateWith { pinned -> keyboardEditorCells(displayItems.filter { it.pinned == pinned }, activeLayer, columns) }
                val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
                    val source = from.key as? UUID
                    val destination = to.key as? UUID
                    if (source != null && destination != null) {
                        onReorder(source, destination)
                    } else if (source != null && to.key is String && "/gap/" in (to.key as String)) {
                        val target = sections.entries.firstNotNullOfOrNull { (pinned, cells) ->
                            cells.firstOrNull { it.item == null && "$pinned/${it.key}" == to.key }?.let { pinned to it }
                        }
                        val item = displayItems.find { it.id == source }
                        if (target != null && item != null && item.columnSpan <= target.second.span) {
                            onMove(item.copy(pinned = target.first), target.second.row, target.second.column)
                        }
                    } else if (source != null && to.key == "empty/true") {
                        onPin(source, true)
                    } else if (source != null && to.key == "empty/false") {
                        onPin(source, false)
                    }
                }
                val rows = sections.values.sumOf { cells -> (cells.maxOfOrNull { it.row + 1 } ?: 1) }
                val height = (layout.buttonHeight.takeIf { it.isFinite() && it > 0 } ?: 30f).coerceAtMost(1000f).dp
                val cellHeight = height
                val gridWidth = if (layout.fitToWidth) maxWidth else ((layout.buttonWidth.takeIf { it.isFinite() && it > 0 } ?: 45f) * columns).coerceAtMost(16000f).dp
                val scroll = rememberScrollState()
                Box(Modifier.then(if (layout.fitToWidth) Modifier else Modifier.horizontalScroll(scroll))) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.width(gridWidth).height(minOf(cellHeight * rows + if (layout.fitToWidth) 0.dp else 88.dp, viewportHeight)),
                        userScrollEnabled = true,
                    ) {
                        sectionKinds.forEach { pinned ->
                            if (!layout.fitToWidth) {
                                item(key = "section/$pinned", span = { GridItemSpan(maxLineSpan) }) {
                                    Column {
                                        if (pinned) HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.outline)
                                        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if (pinned) Icons.Default.PushPin else Icons.Default.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary)
                                            Text(stringResource(if (pinned) R.string.keyboard_pinned_section else R.string.keyboard_scrolling_section), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                            val cells = sections.getValue(pinned)
                            if (cells.none { it.item != null }) {
                                item(key = "empty/$pinned", span = { GridItemSpan(maxLineSpan) }) {
                                    ReorderableItem(reorderState, key = "empty/$pinned") {
                                        Box(Modifier.fillMaxWidth().height(cellHeight).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline)), contentAlignment = Alignment.Center) {
                                            Text(stringResource(R.string.keyboard_drop_section), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            } else {
                                items(cells, key = { if (it.item == null) "$pinned/${it.key}" else it.key }, span = { GridItemSpan(it.span) }) { cell ->
                                    val item = cell.item
                                    if (item == null) {
                                        ReorderableItem(reorderState, key = "$pinned/${cell.key}") {
                                            Spacer(Modifier.fillMaxWidth().height(cellHeight).testTag("keyboard_gap_${pinned}_${cell.row}_${cell.column}"))
                                        }
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
    }
}
