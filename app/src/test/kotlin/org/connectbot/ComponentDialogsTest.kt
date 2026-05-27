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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.ui.components.DisconnectAllDialog
import org.connectbot.ui.components.FontDownloadProgressDialog
import org.connectbot.ui.components.UrlScanDialog
import org.connectbot.ui.screens.portforwardlist.PortForwardEditorDialog
import org.connectbot.ui.screens.portforwardlist.PortForwardEditorTestTags
import org.connectbot.ui.screens.portforwardlist.SourceAddressOption
import org.connectbot.ui.theme.ConnectBotTheme
import org.connectbot.util.HostConstants
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
    fun sourceAddressOption_mapsSshValues() {
        assertEquals(SourceAddressOption.LOCALHOST, SourceAddressOption.fromSshValue("localhost"))
        assertEquals(SourceAddressOption.LOCALHOST_IPV4, SourceAddressOption.fromSshValue("127.0.0.1"))
        assertEquals(SourceAddressOption.LOCALHOST_IPV6, SourceAddressOption.fromSshValue("::1"))
        assertEquals(SourceAddressOption.ALL, SourceAddressOption.fromSshValue(""))
        assertEquals(SourceAddressOption.ALL_IPV4, SourceAddressOption.fromSshValue("0.0.0.0"))
        assertEquals(SourceAddressOption.ALL_IPV6, SourceAddressOption.fromSshValue("::"))
        assertEquals(SourceAddressOption.LOCALHOST, SourceAddressOption.fromSshValue(null))
        assertEquals(SourceAddressOption.SPECIFIC, SourceAddressOption.fromSshValue("192.168.1.10"))
    }

    @Test
    fun portForwardEditorDialog_savesDefaultLocalForwardValues() {
        var saved: SavedPortForward? = null

        composeTestRule.setContent {
            ConnectBotTheme {
                PortForwardEditorDialog(
                    onDismiss = {},
                    onSave = { nickname, type, sourcePort, sourceAddr, destination ->
                        saved = SavedPortForward(nickname, type, sourcePort, sourceAddr, destination)
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.portforward_edit))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.portforward_pos))
            .assertIsEnabled()
            .performClick()

        assertEquals(
            SavedPortForward(
                nickname = "",
                type = HostConstants.PORTFORWARD_LOCAL,
                sourcePort = "8080",
                sourceAddr = "localhost",
                destination = "localhost:80",
            ),
            saved,
        )
    }

    @Test
    fun portForwardEditorDialog_disablesSaveForInvalidPortAndDestination() {
        composeTestRule.setContent {
            ConnectBotTheme {
                PortForwardEditorDialog(
                    onDismiss = {},
                    onSave = { _, _, _, _, _ -> },
                    initialSourcePort = "0",
                    initialDestination = "missing-port",
                )
            }
        }

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.portforward_port_range_error))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.portforward_destination_format_error))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.portforward_pos))
            .assertIsNotEnabled()
    }

    @Test
    fun portForwardEditorDialog_savesRemoteSpecificAddress() {
        var saved: SavedPortForward? = null

        composeTestRule.setContent {
            ConnectBotTheme {
                PortForwardEditorDialog(
                    onDismiss = {},
                    onSave = { nickname, type, sourcePort, sourceAddr, destination ->
                        saved = SavedPortForward(nickname, type, sourcePort, sourceAddr, destination)
                    },
                    initialNickname = "admin",
                    initialType = HostConstants.PORTFORWARD_REMOTE,
                    initialSourcePort = "2200",
                    initialSourceAddr = "192.168.1.10",
                    initialDestination = "server.example:22",
                    isEditing = true,
                )
            }
        }

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.portforward_source_addr_specific))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("192.168.1.10")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.portforward_save))
            .performClick()

        assertEquals(
            SavedPortForward(
                nickname = "admin",
                type = HostConstants.PORTFORWARD_REMOTE,
                sourcePort = "2200",
                sourceAddr = "192.168.1.10",
                destination = "server.example:22",
            ),
            saved,
        )
    }

    @Test
    fun portForwardEditorDialog_acceptsTypedForwardValues() {
        var saved: SavedPortForward? = null

        composeTestRule.setContent {
            ConnectBotTheme {
                PortForwardEditorDialog(
                    onDismiss = {},
                    onSave = { nickname, type, sourcePort, sourceAddr, destination ->
                        saved = SavedPortForward(nickname, type, sourcePort, sourceAddr, destination)
                    },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PortForwardEditorTestTags.NICKNAME_FIELD)
            .performTextInput("web")
        composeTestRule
            .onNodeWithTag(PortForwardEditorTestTags.SOURCE_PORT_FIELD)
            .performTextInput("8081")
        composeTestRule
            .onNodeWithTag(PortForwardEditorTestTags.DESTINATION_FIELD)
            .performTextInput("example.com:443")
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.portforward_pos))
            .performClick()

        assertEquals(
            SavedPortForward(
                nickname = "web",
                type = HostConstants.PORTFORWARD_LOCAL,
                sourcePort = "8081",
                sourceAddr = "localhost",
                destination = "example.com:443",
            ),
            saved,
        )
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

    private data class SavedPortForward(
        val nickname: String,
        val type: String,
        val sourcePort: String,
        val sourceAddr: String,
        val destination: String,
    )
}
