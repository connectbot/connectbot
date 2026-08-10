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

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalMouseInputTest {
    @Test
    fun pixelToCell_topLeftPixel_mapsToFirstCell() {
        assertEquals(
            Pair(1, 1),
            pixelToCell(x = 0f, y = 0f, widthPx = 800, heightPx = 400, cols = 80, rows = 40),
        )
    }

    @Test
    fun pixelToCell_midCellPixel_roundsDownToContainingCell() {
        // Each cell is 10px wide and 10px tall; (25, 15) falls inside the third
        // column and second row (0-indexed: col 2, row 1).
        assertEquals(
            Pair(3, 2),
            pixelToCell(x = 25f, y = 15f, widthPx = 800, heightPx = 400, cols = 80, rows = 40),
        )
    }

    @Test
    fun pixelToCell_lastPixel_mapsToLastCell() {
        assertEquals(
            Pair(80, 40),
            pixelToCell(x = 795f, y = 395f, widthPx = 800, heightPx = 400, cols = 80, rows = 40),
        )
    }

    @Test
    fun pixelToCell_pixelBeyondBounds_clampsToLastCell() {
        assertEquals(
            Pair(80, 40),
            pixelToCell(x = 10000f, y = 10000f, widthPx = 800, heightPx = 400, cols = 80, rows = 40),
        )
    }

    @Test
    fun pixelToCell_negativePixel_clampsToFirstCell() {
        assertEquals(
            Pair(1, 1),
            pixelToCell(x = -50f, y = -50f, widthPx = 800, heightPx = 400, cols = 80, rows = 40),
        )
    }

    @Test
    fun pixelToCell_zeroWidthOrHeight_doesNotDivideByZero() {
        assertEquals(
            Pair(1, 1),
            pixelToCell(x = 25f, y = 15f, widthPx = 0, heightPx = 0, cols = 80, rows = 40),
        )
        assertEquals(
            Pair(1, 2),
            pixelToCell(x = 25f, y = 15f, widthPx = 0, heightPx = 400, cols = 80, rows = 40),
        )
        assertEquals(
            Pair(3, 1),
            pixelToCell(x = 25f, y = 15f, widthPx = 800, heightPx = 0, cols = 80, rows = 40),
        )
    }

    @Test
    fun pixelToCell_zeroColsOrRows_doesNotDivideByZero() {
        assertEquals(
            Pair(1, 1),
            pixelToCell(x = 25f, y = 15f, widthPx = 800, heightPx = 400, cols = 0, rows = 0),
        )
        assertEquals(
            Pair(1, 2),
            pixelToCell(x = 25f, y = 15f, widthPx = 800, heightPx = 400, cols = 0, rows = 40),
        )
        assertEquals(
            Pair(3, 1),
            pixelToCell(x = 25f, y = 15f, widthPx = 800, heightPx = 400, cols = 80, rows = 0),
        )
    }
}
