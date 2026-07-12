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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.keyboard.TmuxAction
import org.connectbot.ui.screens.console.tmux.TmuxActionDrawerSheet
import org.connectbot.ui.screens.console.tmux.TmuxCommandPaletteSheet
import org.connectbot.ui.screens.console.tmux.TmuxKillConfirmDialog
import org.connectbot.ui.screens.console.tmux.TmuxPaletteEntry
import org.connectbot.ui.screens.console.tmux.TmuxRenameDialog
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TmuxManagementUiTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun renameDialog_confirmsTrimmedName() {
        var confirmed = ""
        composeTestRule.setContent {
            ConnectBotTheme {
                TmuxRenameDialog(
                    title = "Rename session",
                    initialName = "main",
                    onConfirm = { confirmed = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("tmux_rename_field").performTextReplacement("  deploy  ")
        composeTestRule.onNodeWithTag("tmux_rename_confirm").performClick()

        composeTestRule.runOnIdle {
            assertEquals("deploy", confirmed)
        }
    }

    @Test
    fun renameDialog_disablesConfirmWhenBlank() {
        composeTestRule.setContent {
            ConnectBotTheme {
                TmuxRenameDialog(
                    title = "Rename window",
                    initialName = "",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("tmux_rename_confirm").assertIsNotEnabled()
    }

    @Test
    fun killDialog_confirms() {
        var killed = false
        composeTestRule.setContent {
            ConnectBotTheme {
                TmuxKillConfirmDialog(
                    message = "Kill session “main”?",
                    onConfirm = { killed = true },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("tmux_kill_confirm").performClick()
        composeTestRule.runOnIdle { assertTrue(killed) }
    }

    @Test
    fun palette_runsCommandAndShowsHistory() {
        var ran = ""
        composeTestRule.setContent {
            ConnectBotTheme {
                TmuxCommandPaletteSheet(
                    history = listOf(
                        TmuxPaletteEntry("list-windows", "@0 shell", isError = false),
                        TmuxPaletteEntry("bogus", "parse error: unknown command", isError = true),
                    ),
                    onRunCommand = { ran = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("› list-windows").assertIsDisplayed()
        composeTestRule.onNodeWithText("parse error: unknown command").assertIsDisplayed()

        composeTestRule.onNodeWithTag("tmux_palette_input").performTextInput("kill-window -t @2")
        composeTestRule.onNodeWithTag("tmux_palette_run").performClick()

        composeTestRule.runOnIdle {
            assertEquals("kill-window -t @2", ran)
        }
    }

    @Test
    fun actionDrawer_dispatchesSemanticAction() {
        var action: TmuxAction? = null
        composeTestRule.setContent {
            ConnectBotTheme {
                TmuxActionDrawerSheet(
                    onAction = { action = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("tmux_action_SPLIT_H").performClick()
        composeTestRule.runOnIdle { assertEquals(TmuxAction.SPLIT_H, action) }
    }
}
