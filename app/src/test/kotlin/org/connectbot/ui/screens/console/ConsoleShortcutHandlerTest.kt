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

import androidx.compose.ui.input.key.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.view.KeyEvent as AndroidKeyEvent

@RunWith(AndroidJUnit4::class)
class ConsoleShortcutHandlerTest {
    @Test
    fun handleConsoleShortcut_invokesCtrlShiftActions() {
        val callbacks = ShortcutCallbacks()

        assertTrue(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_C, CTRL_SHIFT_META_STATE)))
        assertEquals(1, callbacks.copyCount)

        assertTrue(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_V, CTRL_SHIFT_META_STATE)))
        assertEquals(1, callbacks.pasteCount)

        assertTrue(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_EQUALS, CTRL_SHIFT_META_STATE)))
        assertEquals(1, callbacks.increaseCount)

        assertTrue(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_MINUS, CTRL_SHIFT_META_STATE)))
        assertEquals(1, callbacks.decreaseCount)
    }

    @Test
    fun handleConsoleShortcut_invokesEnabledVolumeKeyActions() {
        val callbacks = ShortcutCallbacks(volumeKeysChangeFontSize = true)

        assertTrue(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_VOLUME_UP)))
        assertEquals(1, callbacks.increaseCount)

        assertTrue(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_VOLUME_DOWN)))
        assertEquals(1, callbacks.decreaseCount)
    }

    @Test
    fun handleConsoleShortcut_ignoresKeyUpEvents() {
        val callbacks = ShortcutCallbacks()

        assertFalse(callbacks.handle(keyUp(AndroidKeyEvent.KEYCODE_C, CTRL_SHIFT_META_STATE)))
        callbacks.assertNoActions()
    }

    @Test
    fun handleConsoleShortcut_ignoresIncompleteModifiersAndUnrelatedKeys() {
        val callbacks = ShortcutCallbacks()

        assertFalse(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_C, AndroidKeyEvent.META_CTRL_ON)))
        assertFalse(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_V, AndroidKeyEvent.META_SHIFT_ON)))
        assertFalse(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_A, CTRL_SHIFT_META_STATE)))
        callbacks.assertNoActions()
    }

    @Test
    fun handleConsoleShortcut_ignoresVolumeKeysWhenPreferenceDisabled() {
        val callbacks = ShortcutCallbacks(volumeKeysChangeFontSize = false)

        assertFalse(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_VOLUME_UP)))
        assertFalse(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_VOLUME_DOWN)))
        callbacks.assertNoActions()
    }

    @Test
    fun handleConsoleShortcut_tmuxPaneNavigationOwnsVolumeKeys() {
        val callbacks = ShortcutCallbacks(volumeKeysChangeFontSize = true, tmuxPaneNavigation = true)

        assertTrue(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_VOLUME_UP)))
        assertEquals(1, callbacks.nextPaneCount)
        assertEquals(0, callbacks.increaseCount)

        assertTrue(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_VOLUME_DOWN)))
        assertEquals(1, callbacks.previousPaneCount)
        assertEquals(0, callbacks.decreaseCount)
    }

    @Test
    fun handleConsoleShortcut_tmuxNavigationOffFallsBackToFontSize() {
        val callbacks = ShortcutCallbacks(volumeKeysChangeFontSize = true, tmuxPaneNavigation = false)

        assertTrue(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_VOLUME_UP)))
        assertEquals(1, callbacks.increaseCount)
        assertEquals(0, callbacks.nextPaneCount)
    }

    @Test
    fun handleConsoleShortcut_enterWithActiveSelectionClearsItWithoutConsuming() {
        // Regression test for https://github.com/connectbot/connectbot/issues/2252:
        // a stale terminal selection must not leave the Enter key permanently dead.
        val callbacks = ShortcutCallbacks(selectionActive = true)

        assertFalse(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_ENTER)))
        assertEquals(1, callbacks.clearSelectionCount)
    }

    @Test
    fun handleConsoleShortcut_enterWithoutSelectionIsUntouched() {
        val callbacks = ShortcutCallbacks(selectionActive = false)

        assertFalse(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_ENTER)))
        assertEquals(0, callbacks.clearSelectionCount)
    }

    @Test
    fun handleConsoleShortcut_nonEnterKeysDoNotClearSelection() {
        val callbacks = ShortcutCallbacks(selectionActive = true)

        assertFalse(callbacks.handle(keyDown(AndroidKeyEvent.KEYCODE_A)))
        assertEquals(0, callbacks.clearSelectionCount)
    }

    private class ShortcutCallbacks(
        private val volumeKeysChangeFontSize: Boolean = false,
        private val tmuxPaneNavigation: Boolean = false,
        private val selectionActive: Boolean = false,
    ) {
        var copyCount = 0
        var pasteCount = 0
        var increaseCount = 0
        var decreaseCount = 0
        var nextPaneCount = 0
        var previousPaneCount = 0
        var clearSelectionCount = 0

        fun handle(keyEvent: KeyEvent): Boolean = handleConsoleShortcut(
            keyEvent = keyEvent,
            volumeKeysChangeFontSize = volumeKeysChangeFontSize,
            copySelection = { copyCount++ },
            pasteClipboardContents = { pasteCount++ },
            increaseFontSize = { increaseCount++ },
            decreaseFontSize = { decreaseCount++ },
            tmuxPaneNavigation = tmuxPaneNavigation,
            nextPane = { nextPaneCount++ },
            previousPane = { previousPaneCount++ },
            isSelectionActive = { selectionActive },
            clearSelection = { clearSelectionCount++ },
        )

        fun assertNoActions() {
            assertEquals(0, copyCount)
            assertEquals(0, pasteCount)
            assertEquals(0, increaseCount)
            assertEquals(0, decreaseCount)
            assertEquals(0, nextPaneCount)
            assertEquals(0, previousPaneCount)
            assertEquals(0, clearSelectionCount)
        }
    }

    private companion object {
        const val CTRL_SHIFT_META_STATE = AndroidKeyEvent.META_CTRL_ON or AndroidKeyEvent.META_SHIFT_ON

        fun keyDown(keyCode: Int, metaState: Int = 0): KeyEvent = composeKeyEvent(AndroidKeyEvent.ACTION_DOWN, keyCode, metaState)

        fun keyUp(keyCode: Int, metaState: Int = 0): KeyEvent = composeKeyEvent(AndroidKeyEvent.ACTION_UP, keyCode, metaState)

        fun composeKeyEvent(action: Int, keyCode: Int, metaState: Int): KeyEvent = KeyEvent(AndroidKeyEvent(0L, 0L, action, keyCode, 0, metaState))
    }
}
