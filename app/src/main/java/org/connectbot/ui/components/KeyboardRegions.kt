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

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.data.keyboard.fittedKeyboardItems

/** Reserve a fixed trailing-edge dock while the remaining keyboard can scroll independently. */
@Composable
internal fun KeyboardRegions(
    layout: KeyboardLayout,
    items: List<KeyboardItem>,
    modifier: Modifier = Modifier,
    content: @Composable (KeyboardLayout, List<KeyboardItem>, Modifier) -> Unit,
) {
    val pinned = if (layout.fitToWidth) emptyList() else items.filter { it.pinned }
    val scrolling = if (layout.fitToWidth) fittedKeyboardItems(items) else items.filterNot { it.pinned }
    val direction = LocalLayoutDirection.current
    BoxWithConstraints(modifier) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        val columns = (pinned.maxOfOrNull { it.column + it.columnSpan } ?: 0).coerceIn(0, 1000)
        val width = layout.buttonWidth.takeIf { it.isFinite() && it > 0 } ?: 45f
        val height = layout.buttonHeight.takeIf { it.isFinite() && it > 0 } ?: 30f
        val requestedWidth = (columns * width).coerceIn(0f, 16000f).dp
        val dockWidth = minOf(requestedWidth, if (scrolling.isEmpty()) maxWidth else (maxWidth - 3.dp).coerceAtLeast(0.dp) / 2)
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Row {
                listOf(false to scrolling, true to pinned).filter { it.second.isNotEmpty() }.forEach { (isPinned, regionItems) ->
                    key(isPinned) {
                        if (isPinned && scrolling.isNotEmpty()) {
                            val rows = items.maxOf { it.row + it.rowSpan }.coerceIn(1, 1000)
                            VerticalDivider(
                                thickness = 3.dp,
                                modifier = Modifier.width(3.dp).height(minOf(viewportHeight, (rows * height).coerceIn(1f, 16000f).dp)),
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        val regionModifier = if (isPinned) Modifier.width(if (scrolling.isEmpty()) viewportWidth else dockWidth) else Modifier.weight(1f)
                        CompositionLocalProvider(LocalLayoutDirection provides direction) {
                            content(
                                if (isPinned) layout.copy(fitToWidth = true) else layout,
                                regionItems,
                                regionModifier.testTag(if (isPinned) "keyboard_pinned_region" else "keyboard_scrolling_region"),
                            )
                        }
                    }
                }
            }
        }
    }
}
