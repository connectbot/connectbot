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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.ui.screens.pubkeylist.PubkeyListScreen
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PubkeyListScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun pubkeyListScreen_displaysTitle() {
        composeTestRule.setContent {
            ConnectBotTheme {
                PubkeyListScreen(
                    onNavigateBack = {},
                    onNavigateToGenerate = {},
                    onNavigateToImportFido2 = {},
                    onNavigateToEdit = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Pubkeys")
            .assertIsDisplayed()
    }

    @Test
    fun pubkeyListScreen_hasBackButton() {
        var backCalled = false

        composeTestRule.setContent {
            ConnectBotTheme {
                PubkeyListScreen(
                    onNavigateBack = { backCalled = true },
                    onNavigateToGenerate = {},
                    onNavigateToImportFido2 = {},
                    onNavigateToEdit = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Navigate up")
            .performClick()

        assertTrue(backCalled)
    }

    @Test
    fun pubkeyListScreen_emptyState_displaysMessage() {
        composeTestRule.setContent {
            ConnectBotTheme {
                PubkeyListScreen(
                    onNavigateBack = {},
                    onNavigateToGenerate = {},
                    onNavigateToImportFido2 = {},
                    onNavigateToEdit = {},
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("No keys found.", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule
            .onNodeWithText("No keys found.", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun pubkeyListScreen_fabNavigatesToGenerate() {
        var generateCalled = false

        composeTestRule.setContent {
            ConnectBotTheme {
                PubkeyListScreen(
                    onNavigateBack = {},
                    onNavigateToGenerate = { generateCalled = true },
                    onNavigateToImportFido2 = {},
                    onNavigateToEdit = {},
                )
            }
        }

        // Expand the FAB menu first, then click the Generate menu item
        composeTestRule
            .onNodeWithContentDescription("Generate")
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Generate", useUnmergedTree = true)
            .performClick()

        assertTrue(generateCalled)
    }
}
