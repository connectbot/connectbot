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

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.connectbot.R
import org.connectbot.service.TerminalBridge
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.VTermKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(AndroidJUnit4::class)
class FloatingTextInputDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    // TerminalEmulator is sealed and its native lib isn't on the host JVM, so mock its internal implementation
    private val emulator = mock(Class.forName("org.connectbot.terminal.TerminalEmulatorImpl")) as TerminalEmulator
    private val bridge = mock(TerminalBridge::class.java).also { `when`(it.terminalEmulator).thenReturn(emulator) }

    @Test
    fun trailingNewlineGivesOneEnterNotTwo() {
        composeTestRule.setContent { FloatingTextInputDialog(bridge = bridge, onDismiss = {}) }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("echo hello\n")
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.button_send)).performClick()
        val order = inOrder(emulator)
        order.verify(emulator).pasteText("echo hello")
        order.verify(emulator).dispatchKey(0, VTermKey.ENTER)
    }
}
