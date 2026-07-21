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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.data.entity.KnownHost
import org.connectbot.ui.screens.knownhostlist.KnownHostListItem
import org.connectbot.ui.screens.knownhostlist.KnownHostListScreenContent
import org.connectbot.ui.screens.knownhostlist.KnownHostListTestTags
import org.connectbot.ui.screens.knownhostlist.KnownHostListUiState
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class KnownHostListScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun knownHostListDisplaysEndpointAlgorithmAndFingerprint() {
        setContent()

        composeTestRule.onNodeWithText("example.com:22").assertIsDisplayed()
        composeTestRule.onNodeWithText("Algorithm: ssh-ed25519", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("SHA256:testFingerprint", substring = true).assertIsDisplayed()
    }

    @Test
    fun deletingKnownHostRequiresConfirmation() {
        var deletedKnownHost: KnownHost? = null
        setContent(onDeleteKnownHost = { deletedKnownHost = it })

        composeTestRule
            .onNodeWithTag(KnownHostListTestTags.deleteButton(TEST_KNOWN_HOST.id))
            .performClick()
        composeTestRule.onNodeWithText("Delete known host?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").performClick()

        composeTestRule.runOnIdle {
            assertEquals(TEST_KNOWN_HOST, deletedKnownHost)
        }
    }

    @Test
    fun importingKnownHostAcceptsPastedPublicKey() {
        var importedValue: String? = null
        setContent(
            onImportKnownHost = {
                importedValue = it
                true
            },
        )

        composeTestRule.onNodeWithContentDescription("Import host key").performClick()
        composeTestRule.onNodeWithText("OpenSSH public key").performTextInput("ssh-ed25519 AAAA")
        composeTestRule.onNodeWithText("Import").performClick()

        composeTestRule.runOnIdle {
            assertEquals("ssh-ed25519 AAAA", importedValue)
        }
    }

    private fun setContent(
        onDeleteKnownHost: (KnownHost) -> Unit = {},
        onImportKnownHost: (String) -> Boolean = { true },
    ) {
        composeTestRule.setContent {
            ConnectBotTheme {
                KnownHostListScreenContent(
                    uiState = KnownHostListUiState(
                        knownHosts = listOf(
                            KnownHostListItem(
                                knownHost = TEST_KNOWN_HOST,
                                fingerprint = "SHA256:testFingerprint",
                            ),
                        ),
                        isLoading = false,
                    ),
                    onNavigateBack = {},
                    onDeleteKnownHost = onDeleteKnownHost,
                    onImportKnownHost = onImportKnownHost,
                )
            }
        }
    }

    private companion object {
        val TEST_KNOWN_HOST = KnownHost(
            id = 7,
            hostId = 3,
            hostname = "example.com",
            port = 22,
            hostKeyAlgo = "ssh-ed25519",
            hostKey = "test-host-key".toByteArray(),
        )
    }
}
