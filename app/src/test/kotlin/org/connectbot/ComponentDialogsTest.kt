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

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.service.AuthBanner
import org.connectbot.ui.components.AuthBannerDialog
import org.connectbot.ui.components.AuthBannerDialogContent
import org.connectbot.ui.components.DisconnectAllDialog
import org.connectbot.ui.components.FocusableAlertDialog
import org.connectbot.ui.components.FontDownloadProgressDialog
import org.connectbot.ui.components.TextInputAlertDialog
import org.connectbot.ui.components.UrlScanDialog
import org.connectbot.ui.screens.console.ConsoleTestTags
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ComponentDialogsTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun disconnectAllDialog_invokesConfirmAndDismiss() {
        var confirmed = false
        var dismissed = false

        composeTestRule.setContent {
            ConnectBotTheme {
                DisconnectAllDialog(
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = true },
                )
            }
        }

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.disconnect_all_message))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.disconnect_all_pos))
            .performClick()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.disconnect_all_neg))
            .performClick()

        assertTrue(confirmed)
        assertTrue(dismissed)
    }

    @Test
    fun urlScanDialog_displaysEmptyStateAndDismisses() {
        var dismissed = false

        composeTestRule.setContent {
            ConnectBotTheme {
                UrlScanDialog(
                    urls = emptyList(),
                    onDismiss = { dismissed = true },
                    onUrlClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.empty_urls_message))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_close))
            .performClick()

        assertTrue(dismissed)
    }

    @Test
    fun urlScanDialog_clickingUrlInvokesCallbackAndDismisses() {
        var clickedUrl: String? = null
        var dismissed = false
        val url = "https://connectbot.org"

        composeTestRule.setContent {
            ConnectBotTheme {
                UrlScanDialog(
                    urls = listOf(url),
                    onDismiss = { dismissed = true },
                    onUrlClick = { clickedUrl = it },
                )
            }
        }

        composeTestRule
            .onNodeWithText(url)
            .performClick()

        assertEquals(url, clickedUrl)
        assertTrue(dismissed)
    }

    @Test
    fun authBannerDialog_displaysSourceMessageAndDismisses() {
        var dismissed = false
        val banner = AuthBanner(
            id = 1,
            sourceName = "jump",
            message = "Visit https://login.tailscale.com/a/123456 to authenticate.",
            urls = listOf("https://login.tailscale.com/a/123456"),
            languageTag = "en",
        )

        composeTestRule.setContent {
            ConnectBotTheme {
                AuthBannerDialog(
                    banner = banner,
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.auth_banner_title, "jump"))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(ConsoleTestTags.AUTH_BANNER_MESSAGE)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_close))
            .performClick()

        assertTrue(dismissed)
    }

    @Test
    fun authBannerDialogContent_withNoUrls() {
        val banner = AuthBanner(
            id = 2,
            sourceName = "jump-no-url",
            message = "Welcome without any link.",
            urls = emptyList(),
            languageTag = "en",
        )

        composeTestRule.setContent {
            ConnectBotTheme {
                AuthBannerDialogContent(
                    banner = banner,
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.auth_banner_title, "jump-no-url"))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Welcome without any link.")
            .assertIsDisplayed()
    }

    @Test
    fun authBannerDialogContent_withUrlNotMatching() {
        val banner = AuthBanner(
            id = 3,
            sourceName = "jump-missing-url",
            message = "Welcome to our server.",
            urls = listOf("https://missing-link.com"),
            languageTag = "en",
        )

        composeTestRule.setContent {
            ConnectBotTheme {
                AuthBannerDialogContent(
                    banner = banner,
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.auth_banner_title, "jump-missing-url"))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Welcome to our server.")
            .assertIsDisplayed()
    }

    @Test
    fun authBannerDialogContent_withMultipleUrls() {
        val banner = AuthBanner(
            id = 4,
            sourceName = "jump-multiple-urls",
            message = "Visit https://link1.com and also https://link2.com.",
            urls = listOf("https://link1.com", "https://link2.com"),
            languageTag = "en",
        )

        composeTestRule.setContent {
            ConnectBotTheme {
                AuthBannerDialogContent(
                    banner = banner,
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.auth_banner_title, "jump-multiple-urls"))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("https://link1.com", substring = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("https://link2.com", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun fontDownloadProgressDialog_displaysProgressText() {
        composeTestRule.setContent {
            ConnectBotTheme {
                FontDownloadProgressDialog()
            }
        }

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.font_downloading))
            .assertIsDisplayed()
    }

    @Test
    fun textInputAlertDialog_focusesAndHandlesActions() {
        var confirmedValue: String? = null
        var dismissed = false

        composeTestRule.setContent {
            ConnectBotTheme {
                var text by remember { mutableStateOf("") }
                TextInputAlertDialog(
                    onDismissRequest = { dismissed = true },
                    onConfirm = { confirmedValue = text },
                    value = text,
                    onValueChange = { text = it },
                    title = { Text("Dialog Title") },
                    message = { Text("Dialog Message") },
                    label = { Text("Input Label") },
                    confirmButtonText = "Confirm",
                    dismissButtonText = "Cancel",
                    confirmButtonTestTag = "confirm_btn",
                    dismissButtonTestTag = "dismiss_btn",
                    textFieldModifier = Modifier.testTag("text_input"),
                )
            }
        }

        composeTestRule.onNodeWithText("Dialog Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dialog Message").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("text_input")
            .assertIsDisplayed()
            .assertIsFocused()
            .performTextInput("typed text")

        composeTestRule.onNodeWithTag("confirm_btn").performClick()
        assertEquals("typed text", confirmedValue)

        composeTestRule.onNodeWithTag("dismiss_btn").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun textInputAlertDialog_imeDoneTriggersConfirm() {
        var confirmedValue: String? = null

        composeTestRule.setContent {
            ConnectBotTheme {
                var text by remember { mutableStateOf("") }
                TextInputAlertDialog(
                    onDismissRequest = {},
                    onConfirm = { confirmedValue = text },
                    value = text,
                    onValueChange = { text = it },
                    textFieldModifier = Modifier.testTag("text_input"),
                )
            }
        }

        val inputNode = composeTestRule.onNodeWithTag("text_input")
        inputNode.performTextInput("submitted via ime")
        inputNode.performImeAction()

        assertEquals("submitted via ime", confirmedValue)
    }

    @Test
    fun focusableAlertDialog_providesFocusRequester() {
        composeTestRule.setContent {
            ConnectBotTheme {
                var text by remember { mutableStateOf("") }
                FocusableAlertDialog(
                    onDismissRequest = {},
                    confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                ) { focusRequester ->
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .testTag("focused_field")
                            .focusRequester(focusRequester),
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("focused_field")
            .assertIsDisplayed()
            .assertIsFocused()
    }
}
