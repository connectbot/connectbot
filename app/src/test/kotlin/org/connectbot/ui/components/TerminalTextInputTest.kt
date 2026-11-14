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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalTextInputTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun draftCanBeReviewedExplicitlyAndIsHiddenAgainAfterSend() {
        compose.setContent { ConnectBotTheme { TerminalTextInput(onSend = {}) } }
        compose.onNodeWithText("Terminal input").performTextInput("echo 世界")
        compose.onNodeWithText("Terminal input").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("Show input").performClick()
        compose.onNodeWithText("Terminal input").assertTextEquals("Terminal input", "echo 世界")
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("Show input").assertExists()
    }

    @Test
    fun replacingSessionClearsAndHidesTheDraft() {
        var session by mutableStateOf(0)
        val sent = mutableListOf<String>()
        compose.setContent { ConnectBotTheme { TerminalTextInput(onSend = { sent += it }, sessionKey = session) } }
        compose.onNodeWithText("Terminal input").performTextInput("private draft")
        compose.onNodeWithText("Show input").performClick()
        compose.runOnIdle { session = 1 }
        compose.onNodeWithText("Terminal input").assertTextEquals("Terminal input", "")
        compose.onNodeWithText("Show input").assertExists()
        compose.runOnIdle { assertEquals(emptyList<String>(), sent) }
    }

    @Test
    fun hardwareEnterSubmitsWithoutALineFeed() {
        val sent = mutableListOf<String>()
        compose.setContent { ConnectBotTheme { TerminalTextInput(onSend = { sent += it }) } }
        compose.onNodeWithText("Terminal input").performTextInput("pwd")
        compose.onNodeWithText("Terminal input").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(listOf("pwd\r"), sent) }
    }

    @Test
    fun sendButtonSubmitsOnceWithCarriageReturnAndClearsDraft() {
        val sent = mutableListOf<String>()
        compose.setContent { ConnectBotTheme { TerminalTextInput(onSend = { sent += it }) } }
        compose.onNodeWithText("Terminal input").performTextInput("echo hello")
        compose.onNodeWithText("Send").performClick()
        compose.runOnIdle { assertEquals(listOf("echo hello\r"), sent) }
        compose.onNodeWithText("Terminal input").assertTextEquals("Terminal input", "")
    }

    @Test
    fun imeSendSubmitsAnEmptyEnterWithoutInsertingANewline() {
        val sent = mutableListOf<String>()
        compose.setContent { ConnectBotTheme { TerminalTextInput(onSend = { sent += it }) } }
        compose.onNodeWithText("Terminal input").performImeAction()
        compose.runOnIdle { assertEquals(listOf("\r"), sent) }
    }

    @Test
    fun multilinePasteCannotExecuteExtraCommands() {
        val sent = mutableListOf<String>()
        compose.setContent { ConnectBotTheme { TerminalTextInput(onSend = { sent += it }) } }
        compose.onNodeWithText("Terminal input").performTextInput("first\nsecond")
        compose.onNodeWithText("Send").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), sent) }
    }
}
