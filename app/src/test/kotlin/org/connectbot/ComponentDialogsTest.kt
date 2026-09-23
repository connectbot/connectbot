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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.service.AuthBanner
import org.connectbot.ui.components.AuthBannerDialog
import org.connectbot.ui.components.AuthBannerDialogContent
import org.connectbot.ui.components.DisconnectAllDialog
import org.connectbot.ui.components.FontDownloadProgressDialog
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
}
