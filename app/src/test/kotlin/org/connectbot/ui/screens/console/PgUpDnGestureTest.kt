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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PgUpDnGestureTest {
    @Test
    fun isInPgUpDnZone_acceptsLeftThird() {
        assertTrue(isInPgUpDnZone(x = 0f, width = 900))
        assertTrue(isInPgUpDnZone(x = 300f, width = 900))
    }

    @Test
    fun isInPgUpDnZone_rejectsRestOfTerminal() {
        assertFalse(isInPgUpDnZone(x = 301f, width = 900))
        assertFalse(isInPgUpDnZone(x = 899f, width = 900))
    }

    @Test
    fun isInPgUpDnZone_rejectsUnmeasuredTerminal() {
        assertFalse(isInPgUpDnZone(x = 0f, width = 0))
    }

    @Test
    fun pgUpDnStepPx_usesMeasuredRowHeight() {
        assertEquals(100f, pgUpDnStepPx(rowHeightPx = 20, fallbackRowHeightPx = 60f), 0f)
    }

    @Test
    fun pgUpDnStepPx_fallsBackWhileRowHeightIsUnmeasured() {
        // TerminalBridge.charHeight is -1 until the font has been measured.
        assertEquals(300f, pgUpDnStepPx(rowHeightPx = -1, fallbackRowHeightPx = 60f), 0f)
    }

    @Test
    fun pgUpDnPageCount_pagesForwardWhenDraggingUp() {
        assertEquals(1, pgUpDnPageCount(accumulatedUpPx = 100f, stepPx = 100f))
        assertEquals(2, pgUpDnPageCount(accumulatedUpPx = 250f, stepPx = 100f))
    }

    @Test
    fun pgUpDnPageCount_pagesBackWhenDraggingDown() {
        assertEquals(-1, pgUpDnPageCount(accumulatedUpPx = -100f, stepPx = 100f))
        assertEquals(-2, pgUpDnPageCount(accumulatedUpPx = -250f, stepPx = 100f))
    }

    @Test
    fun pgUpDnPageCount_ignoresDragShorterThanOnePage() {
        assertEquals(0, pgUpDnPageCount(accumulatedUpPx = 99f, stepPx = 100f))
        assertEquals(0, pgUpDnPageCount(accumulatedUpPx = -99f, stepPx = 100f))
    }

    @Test
    fun pgUpDnPageCount_ignoresDegenerateStep() {
        assertEquals(0, pgUpDnPageCount(accumulatedUpPx = 500f, stepPx = 0f))
    }

    @Test
    fun pgUpDnPageCount_leavesRemainderForTheNextDrag() {
        val stepPx = 100f
        var pending = 250f

        val pages = pgUpDnPageCount(pending, stepPx)
        pending -= pages * stepPx

        assertEquals(2, pages)
        assertEquals(50f, pending, 0f)
        // The leftover alone must not page again.
        assertEquals(0, pgUpDnPageCount(pending, stepPx))
    }
}
