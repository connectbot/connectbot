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

package org.connectbot.ui.screens.console.tmux

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.systemGestureExclusion
import kotlin.math.abs
import kotlin.math.max
import org.connectbot.R
import org.connectbot.service.tmux.TmuxWindow

/**
 * The window strip: one chip per tmux window in the active session, shown
 * under the tab strip and hidden with the rest of the chrome. Badge dots
 * mark bell (error color) and activity (tertiary color) windows.
 */
@Composable
fun TmuxWindowStrip(
    windows: List<TmuxWindow>,
    activeWindowId: String?,
    onSelectWindow: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLongPressWindow: (String) -> Unit = {},
    onNewWindow: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeWindowId, windows.size) {
        val index = windows.indexOfFirst { it.id == activeWindowId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .testTag("tmux_window_strip"),
    ) {
        itemsIndexed(
            items = windows,
            key = { _, window -> window.id },
        ) { _, window ->
            val selected = window.id == activeWindowId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .combinedClickable(
                        role = Role.Tab,
                        onClick = { onSelectWindow(window.id) },
                        onLongClick = { onLongPressWindow(window.id) },
                    )
                    .semantics { this.selected = selected }
                    .background(
                        if (selected) accentColor.copy(alpha = 0.18f) else Color.Transparent,
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("tmux_window_${window.id}"),
            ) {
                Text(
                    text = "${window.index} ${window.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (window.bell || window.activity) {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(6.dp)
                            .background(
                                if (window.bell) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                                CircleShape,
                            )
                            .testTag("tmux_window_badge_${window.id}"),
                    )
                }
            }
        }

        if (onNewWindow != null) {
            item(key = "tmux_new_window") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(onClick = onNewWindow)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("tmux_new_window"),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.tmux_new_window),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Pager-style dots showing which pane of the current window is on screen.
 * Hidden by the caller when the window has a single pane.
 */
@Composable
fun PaneDotsIndicator(
    count: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.testTag("tmux_pane_dots"),
    ) {
        repeat(count) { index ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (selected) 8.dp else 6.dp)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

/**
 * Decides what a completed horizontal drag on a tmux pane means.
 * Swiping left (negative drag) moves forward (+1), right moves back (-1);
 * drags that are too short or too vertical mean nothing.
 */
internal fun tmuxSwipeDirection(
    dragX: Float,
    dragY: Float,
    viewportWidth: Int,
    touchSlop: Float,
): Int? {
    if (viewportWidth <= 0) return null
    val minimumSwipeDistance = max(touchSlop * 3f, viewportWidth * 0.18f)
    if (abs(dragX) < minimumSwipeDistance || abs(dragX) < abs(dragY) * 1.5f) {
        return null
    }
    return if (dragX < 0f) 1 else -1
}

/**
 * tmux navigation gestures for the terminal surface:
 * - horizontal swipe on the surface = previous/next pane
 * - horizontal swipe starting at a screen edge = previous/next window
 *
 * The edge zones are excluded from the system back gesture (only the
 * terminal's own band) so window swipes don't trigger Android back.
 */
fun Modifier.tmuxSwipeNavigation(
    selectionActive: Boolean,
    onSwipePane: (Int) -> Unit,
    onSwipeWindow: (Int) -> Unit,
    onInteraction: () -> Unit,
    /** Device density (LocalDensity.current.density) for exact edge-zone pixels. */
    density: Float,
    edgeWidthDp: Float = EDGE_ZONE_DP,
): Modifier = this
    .systemGestureExclusion { coordinates ->
        Rect(0f, 0f, edgeWidthDp * density, coordinates.size.height.toFloat())
    }
    .systemGestureExclusion { coordinates ->
        Rect(
            coordinates.size.width - edgeWidthDp * density,
            0f,
            coordinates.size.width.toFloat(),
            coordinates.size.height.toFloat(),
        )
    }
    .pointerInput(selectionActive) {
        if (selectionActive) {
            return@pointerInput
        }
        val edgePx = edgeWidthDp * density

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val fromEdge = down.position.x <= edgePx || down.position.x >= size.width - edgePx
            val pointerId = down.id
            var dragX = 0f
            var dragY = 0f
            var horizontalSwipeLocked = false
            var verticalGestureLocked = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) {
                    break
                }

                val delta = change.positionChange()
                dragX += delta.x
                dragY += delta.y

                if (!horizontalSwipeLocked && !verticalGestureLocked) {
                    val absX = abs(dragX)
                    val absY = abs(dragY)
                    if (absX > viewConfiguration.touchSlop && absX > absY * 1.5f) {
                        horizontalSwipeLocked = true
                    } else if (absY > viewConfiguration.touchSlop && absY > absX) {
                        verticalGestureLocked = true
                    }
                }

                if (horizontalSwipeLocked) {
                    change.consume()
                }
            }

            if (horizontalSwipeLocked) {
                tmuxSwipeDirection(
                    dragX = dragX,
                    dragY = dragY,
                    viewportWidth = size.width,
                    touchSlop = viewConfiguration.touchSlop,
                )?.let { direction ->
                    onInteraction()
                    if (fromEdge) onSwipeWindow(direction) else onSwipePane(direction)
                }
            }
        }
    }

private const val EDGE_ZONE_DP = 24f
