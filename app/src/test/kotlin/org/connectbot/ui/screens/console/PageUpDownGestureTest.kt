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

import org.connectbot.terminal.VTermKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageUpDownGestureTest {
    @Test
    fun pageKeyForDrag_upwardDragSendsPageDown() {
        assertEquals(
            VTermKey.PAGEDOWN,
            pageKeyForDrag(
                accumulatedDragY = -300f,
                viewportHeight = 2000,
                touchSlop = 20f,
            ),
        )
    }

    @Test
    fun pageKeyForDrag_downwardDragSendsPageUp() {
        assertEquals(
            VTermKey.PAGEUP,
            pageKeyForDrag(
                accumulatedDragY = 300f,
                viewportHeight = 2000,
                touchSlop = 20f,
            ),
        )
    }

    @Test
    fun pageKeyForDrag_ignoresDragsShorterThanPageDistance() {
        assertNull(
            pageKeyForDrag(
                accumulatedDragY = 200f,
                viewportHeight = 2000,
                touchSlop = 20f,
            ),
        )

        assertNull(
            pageKeyForDrag(
                accumulatedDragY = -200f,
                viewportHeight = 2000,
                touchSlop = 20f,
            ),
        )
    }

    @Test
    fun pageKeyForDrag_usesTouchSlopFloorOnShortViewports() {
        // Viewport of 400px gives a 50px page distance, below the 4x slop
        // floor of 80px, so an intermediate drag must not page.
        assertNull(
            pageKeyForDrag(
                accumulatedDragY = 60f,
                viewportHeight = 400,
                touchSlop = 20f,
            ),
        )

        assertEquals(
            VTermKey.PAGEUP,
            pageKeyForDrag(
                accumulatedDragY = 90f,
                viewportHeight = 400,
                touchSlop = 20f,
            ),
        )
    }

    @Test
    fun pageKeyForDrag_ignoresDragsWhileSelectionActive() {
        assertNull(
            pageKeyForDrag(
                accumulatedDragY = -300f,
                viewportHeight = 2000,
                touchSlop = 20f,
                selectionActive = true,
            ),
        )
    }

    @Test
    fun pageKeyForDrag_ignoresEmptyViewport() {
        assertNull(
            pageKeyForDrag(
                accumulatedDragY = -300f,
                viewportHeight = 0,
                touchSlop = 20f,
            ),
        )
    }
}
