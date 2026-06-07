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

    private class ShortcutCallbacks(
        private val volumeKeysChangeFontSize: Boolean = false,
    ) {
        var copyCount = 0
        var pasteCount = 0
        var increaseCount = 0
        var decreaseCount = 0

        fun handle(keyEvent: KeyEvent): Boolean = handleConsoleShortcut(
            keyEvent = keyEvent,
            volumeKeysChangeFontSize = volumeKeysChangeFontSize,
            copySelection = { copyCount++ },
            pasteClipboardContents = { pasteCount++ },
            increaseFontSize = { increaseCount++ },
            decreaseFontSize = { decreaseCount++ },
        )

        fun assertNoActions() {
            assertEquals(0, copyCount)
            assertEquals(0, pasteCount)
            assertEquals(0, increaseCount)
            assertEquals(0, decreaseCount)
        }
    }

    private companion object {
        const val CTRL_SHIFT_META_STATE = AndroidKeyEvent.META_CTRL_ON or AndroidKeyEvent.META_SHIFT_ON

        fun keyDown(keyCode: Int, metaState: Int = 0): KeyEvent = composeKeyEvent(AndroidKeyEvent.ACTION_DOWN, keyCode, metaState)

        fun keyUp(keyCode: Int, metaState: Int = 0): KeyEvent = composeKeyEvent(AndroidKeyEvent.ACTION_UP, keyCode, metaState)

        fun composeKeyEvent(action: Int, keyCode: Int, metaState: Int): KeyEvent = KeyEvent(AndroidKeyEvent(0L, 0L, action, keyCode, 0, metaState))
    }
}
