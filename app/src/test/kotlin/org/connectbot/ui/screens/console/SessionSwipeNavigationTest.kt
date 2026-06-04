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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSwipeNavigationTest {
    @Test
    fun sessionSwipeTarget_leftSwipe_selectsNextSession() {
        assertEquals(
            1,
            sessionSwipeTarget(
                currentIndex = 0,
                sessionCount = 3,
                dragX = -220f,
                dragY = 20f,
                viewportWidth = 1000,
                touchSlop = 20f,
            ),
        )
    }

    @Test
    fun sessionSwipeTarget_rightSwipe_selectsPreviousSession() {
        assertEquals(
            1,
            sessionSwipeTarget(
                currentIndex = 2,
                sessionCount = 3,
                dragX = 220f,
                dragY = 20f,
                viewportWidth = 1000,
                touchSlop = 20f,
            ),
        )
    }

    @Test
    fun sessionSwipeTarget_doesNotMovePastBounds() {
        assertNull(
            sessionSwipeTarget(
                currentIndex = 0,
                sessionCount = 3,
                dragX = 220f,
                dragY = 20f,
                viewportWidth = 1000,
                touchSlop = 20f,
            ),
        )

        assertNull(
            sessionSwipeTarget(
                currentIndex = 2,
                sessionCount = 3,
                dragX = -220f,
                dragY = 20f,
                viewportWidth = 1000,
                touchSlop = 20f,
            ),
        )
    }

    @Test
    fun sessionSwipeTarget_ignoresSmallOrVerticalDrags() {
        assertNull(
            sessionSwipeTarget(
                currentIndex = 1,
                sessionCount = 3,
                dragX = -100f,
                dragY = 0f,
                viewportWidth = 1000,
                touchSlop = 20f,
            ),
        )

        assertNull(
            sessionSwipeTarget(
                currentIndex = 1,
                sessionCount = 3,
                dragX = -220f,
                dragY = 180f,
                viewportWidth = 1000,
                touchSlop = 20f,
            ),
        )
    }

    @Test
    fun sessionSwipeTarget_ignoresDragsWhileSelectionActive() {
        assertNull(
            sessionSwipeTarget(
                currentIndex = 1,
                sessionCount = 3,
                dragX = -220f,
                dragY = 20f,
                viewportWidth = 1000,
                touchSlop = 20f,
                selectionActive = true,
            ),
        )
    }

    @Test
    fun shouldShowSoftwareKeyboardForSessionOpen_showsOnlyForSameBridgeOpening() {
        assertTrue(
            shouldShowSoftwareKeyboardForSessionOpen(
                previousBridgeId = 1L,
                previousSessionOpen = false,
                currentBridgeId = 1L,
                sessionOpen = true,
                hasHardwareKeyboard = false,
            ),
        )

        assertFalse(
            shouldShowSoftwareKeyboardForSessionOpen(
                previousBridgeId = 1L,
                previousSessionOpen = true,
                currentBridgeId = 2L,
                sessionOpen = true,
                hasHardwareKeyboard = false,
            ),
        )
    }

    @Test
    fun shouldShowSoftwareKeyboardForSessionOpen_ignoresHardwareKeyboard() {
        assertFalse(
            shouldShowSoftwareKeyboardForSessionOpen(
                previousBridgeId = 1L,
                previousSessionOpen = false,
                currentBridgeId = 1L,
                sessionOpen = true,
                hasHardwareKeyboard = true,
            ),
        )
    }

    @Test
    fun shouldPreserveSoftwareKeyboardForBridgeChange_preservesOnlyVisibleSoftKeyboard() {
        assertTrue(
            shouldPreserveSoftwareKeyboardForBridgeChange(
                previousBridgeId = 1L,
                currentBridgeId = 2L,
                showSoftwareKeyboard = true,
                hasHardwareKeyboard = false,
            ),
        )

        assertFalse(
            shouldPreserveSoftwareKeyboardForBridgeChange(
                previousBridgeId = 1L,
                currentBridgeId = 2L,
                showSoftwareKeyboard = false,
                hasHardwareKeyboard = false,
            ),
        )

        assertFalse(
            shouldPreserveSoftwareKeyboardForBridgeChange(
                previousBridgeId = 1L,
                currentBridgeId = 2L,
                showSoftwareKeyboard = true,
                hasHardwareKeyboard = true,
            ),
        )
    }
}
