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

package org.connectbot.ui.screens.console

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import org.connectbot.service.TerminalBridge
import kotlin.math.abs
import kotlin.math.floor

// libvterm button numbering: 1-3 are left/middle/right, 4/5 are wheel up/down.
private const val MOUSE_BUTTON_LEFT = 1
private const val MOUSE_WHEEL_UP = 4
private const val MOUSE_WHEEL_DOWN = 5

/**
 * Maps a pixel coordinate within a composable of the given size to a 1-based
 * terminal cell coordinate, clamped to the terminal grid bounds.
 */
internal fun pixelToCell(
    x: Float,
    y: Float,
    widthPx: Int,
    heightPx: Int,
    cols: Int,
    rows: Int,
): Pair<Int, Int> {
    val col = if (widthPx <= 0 || cols <= 0) {
        1
    } else {
        val cellWidth = widthPx.toFloat() / cols
        (floor(x / cellWidth).toInt() + 1).coerceIn(1, cols)
    }
    val row = if (heightPx <= 0 || rows <= 0) {
        1
    } else {
        val cellHeight = heightPx.toFloat() / rows
        (floor(y / cellHeight).toInt() + 1).coerceIn(1, rows)
    }
    return col to row
}

/**
 * The states of the per-gesture state machine driven by [terminalMouseReporting].
 */
private enum class MouseGestureState {
    /** No decision made yet: waiting to see if this becomes a tap, scroll, or long-press. */
    UNDETERMINED,

    /** Vertical drag beyond touch slop: reporting mouse wheel events for the rest of the gesture. */
    SCROLL,

    /** Stationary long-press: handing the gesture over to termlib's own text selection. */
    SELECTION,

    /** Pinch (second pointer) or horizontal drag: falling through to termlib/session-swipe. */
    ABORTED,
}

/**
 * Translates touch gestures into xterm mouse escape sequences sent to [bridge] when the
 * remote program has enabled mouse reporting.
 *
 * When [enabled] is false, this is a no-op that returns the receiver unchanged so that
 * termlib's own gesture handling (tap-to-focus, scrollback drag, long-press selection,
 * pinch-to-zoom) is completely unaffected.
 *
 * When enabled, gestures are observed on [PointerEventPass.Initial], i.e. before termlib's
 * own gesture detectors see them. Crucially, none of the pointer changes are consumed: that
 * lets termlib's own gesture detector (which runs afterwards, on [PointerEventPass.Main])
 * still see the real movement, so it classifies a drag as its own scroll gesture and cancels
 * its long-press timer. If we consumed instead, [androidx.compose.ui.input.pointer.PointerInputChange.positionChange]
 * would report zero to termlib, its long-press would fire mid-drag, and a magnifier loupe and
 * text selection would appear on top of the scroll. Leaving events unconsumed avoids that; the
 * only cost is that termlib also scrolls its local scrollback, which is a no-op on the
 * alternate screen where mouse-reporting programs (tmux, vim, htop, less, …) run.
 *
 * The gesture is classified from the unconsumed movement:
 * - A second pointer going down aborts mouse-reporting handling entirely so pinch-to-zoom and
 *   other multi-touch gestures still work.
 * - Movement beyond touch slop that is mostly horizontal aborts so session-swipe navigation
 *   still works.
 * - Movement beyond touch slop that is mostly vertical is reported as mouse wheel events, one
 *   per cell-height of accumulated movement, at the cell under the current pointer position.
 *   Moving the finger down sends wheel-up events. termlib concurrently treats the same drag as
 *   a scroll gesture (so no long-press, magnifier, or selection is started).
 * - Holding still past the long-press timeout without exceeding touch slop hands the gesture to
 *   termlib: nothing is reported for the remainder of the gesture, so termlib can start and own
 *   its own text selection, including drag-to-extend.
 * - Otherwise, releasing the pointer before the long-press timeout is a tap: a mouse press
 *   and release are sent for the cell under the down position.
 */
internal fun Modifier.terminalMouseReporting(
    bridge: TerminalBridge,
    enabled: Boolean,
): Modifier {
    if (!enabled) {
        return this
    }

    return pointerInput(bridge, enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val pointerId = down.id
            val downPosition = down.position
            val downTimeMillis = down.uptimeMillis
            var dragX = 0f
            var dragY = 0f
            var state = MouseGestureState.UNDETERMINED

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)

                if (event.changes.any { it.id != pointerId && it.changedToDown() }) {
                    state = MouseGestureState.ABORTED
                }

                val change = event.changes.firstOrNull { it.id == pointerId }

                if (state == MouseGestureState.ABORTED) {
                    if (event.changes.none { it.pressed }) {
                        break
                    }
                    continue
                }

                if (change == null || !change.pressed) {
                    if (state == MouseGestureState.UNDETERMINED) {
                        val elapsed = (change?.uptimeMillis ?: downTimeMillis) - downTimeMillis
                        if (
                            elapsed < viewConfiguration.longPressTimeoutMillis &&
                            abs(dragX) <= viewConfiguration.touchSlop &&
                            abs(dragY) <= viewConfiguration.touchSlop
                        ) {
                            val emulator = bridge.terminalEmulator
                            val dimensions = emulator.dimensions
                            val (col, row) = pixelToCell(
                                downPosition.x,
                                downPosition.y,
                                size.width,
                                size.height,
                                dimensions.columns,
                                dimensions.rows,
                            )
                            // pixelToCell is 1-based; libvterm wants 0-based (row, col).
                            emulator.dispatchMouseMove(row - 1, col - 1)
                            emulator.dispatchMouseButton(MOUSE_BUTTON_LEFT, pressed = true)
                            emulator.dispatchMouseButton(MOUSE_BUTTON_LEFT, pressed = false)
                        }
                    }
                    break
                }

                val delta = change.positionChange()
                dragX += delta.x
                dragY += delta.y

                if (state == MouseGestureState.UNDETERMINED) {
                    val elapsed = change.uptimeMillis - downTimeMillis
                    val absX = abs(dragX)
                    val absY = abs(dragY)

                    if (
                        elapsed >= viewConfiguration.longPressTimeoutMillis &&
                        absX <= viewConfiguration.touchSlop &&
                        absY <= viewConfiguration.touchSlop
                    ) {
                        state = MouseGestureState.SELECTION
                    } else if (absX > viewConfiguration.touchSlop || absY > viewConfiguration.touchSlop) {
                        state = if (absY > absX) {
                            MouseGestureState.SCROLL
                        } else {
                            MouseGestureState.ABORTED
                        }
                    }
                }

                if (state == MouseGestureState.SCROLL) {
                    val emulator = bridge.terminalEmulator
                    val dimensions = emulator.dimensions
                    val cellHeightPx = size.height.toFloat() / dimensions.rows.coerceAtLeast(1)
                    while (cellHeightPx > 0f && abs(dragY) >= cellHeightPx) {
                        val up = dragY > 0f
                        dragY -= if (up) cellHeightPx else -cellHeightPx
                        val (col, row) = pixelToCell(
                            change.position.x,
                            change.position.y,
                            size.width,
                            size.height,
                            dimensions.columns,
                            dimensions.rows,
                        )
                        // pixelToCell is 1-based; libvterm wants 0-based (row, col).
                        emulator.dispatchMouseMove(row - 1, col - 1)
                        emulator.dispatchMouseButton(if (up) MOUSE_WHEEL_UP else MOUSE_WHEEL_DOWN, pressed = true)
                    }
                }
            }
        }
    }
}
