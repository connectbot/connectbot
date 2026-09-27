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

package org.connectbot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.terminal.TerminalKey
import org.connectbot.ui.components.TerminalKeyButtonDialog
import org.connectbot.ui.theme.ConnectBotTheme
import org.connectbot.util.TerminalKeyModifiers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TerminalKeyPickerTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun controlCharacterHasCaretLabelAndUserCanRenameIt() {
        var savedKey: TerminalKey? = null
        var savedModifiers = 0
        var savedLabel = ""
        compose.setContent {
            ConnectBotTheme {
                TerminalKeyButtonDialog(
                    initialTarget = "enter",
                    initialModifiers = TerminalKeyModifiers.CTRL,
                    onDismiss = {},
                    onConfirm = { key, modifiers, label ->
                        savedKey = key
                        savedModifiers = modifiers
                        savedLabel = label
                    },
                )
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.automation_key) + ": " + compose.activity.getString(R.string.automation_key_enter)).performClick()
        compose.onNodeWithText("a").performScrollTo().performClick()
        compose.onNodeWithText("^A").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.keyboard_button_label)).performTextReplacement("Select all")
        compose.onNodeWithText(compose.activity.getString(R.string.keyboard_save)).performClick()
        assertEquals(-'a'.code, savedKey?.value)
        assertEquals(TerminalKeyModifiers.CTRL, savedModifiers)
        assertEquals("Select all", savedLabel)
    }

    @Test
    fun changingModifiersPreservesCustomLabel() {
        var savedModifiers = 0
        var savedLabel = ""
        compose.setContent {
            ConnectBotTheme {
                TerminalKeyButtonDialog(
                    initialTarget = "character_97",
                    initialLabel = "Shortcut",
                    onDismiss = {},
                    onConfirm = { _, modifiers, label ->
                        savedModifiers = modifiers
                        savedLabel = label
                    },
                )
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.button_key_ctrl)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.button_key_alt)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.keyboard_save)).performClick()
        assertEquals(TerminalKeyModifiers.CTRL or TerminalKeyModifiers.ALT, savedModifiers)
        assertEquals("Shortcut", savedLabel)
    }
}
