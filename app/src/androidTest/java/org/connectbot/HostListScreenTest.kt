/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.ui.screens.hostlist.HostListScreen
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HostListScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun hostListScreen_displaysTitle() {
        composeTestRule.setContent {
            ConnectBotTheme {
                HostListScreen(
                    onNavigateToConsole = {},
                    onNavigateToEditHost = {},
                    onNavigateToSettings = {},
                    onNavigateToColors = {},
                    onNavigateToPubkeys = {},
                    onNavigateToPortForwards = {},
                    onNavigateToProfiles = {},
                    onNavigateToHelp = {},
                    onNavigateToSftp = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("ConnectBot")
            .assertIsDisplayed()
    }

    @Test
    fun hostListScreen_hasAddButton() {
        composeTestRule.setContent {
            ConnectBotTheme {
                HostListScreen(
                    onNavigateToConsole = {},
                    onNavigateToEditHost = {},
                    onNavigateToSettings = {},
                    onNavigateToColors = {},
                    onNavigateToPubkeys = {},
                    onNavigateToPortForwards = {},
                    onNavigateToProfiles = {},
                    onNavigateToHelp = {},
                    onNavigateToSftp = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Add host", ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun hostListScreen_menuOpensOnClick() {
        composeTestRule.setContent {
            ConnectBotTheme {
                HostListScreen(
                    onNavigateToConsole = {},
                    onNavigateToEditHost = {},
                    onNavigateToSettings = {},
                    onNavigateToColors = {},
                    onNavigateToPubkeys = {},
                    onNavigateToPortForwards = {},
                    onNavigateToProfiles = {},
                    onNavigateToHelp = {},
                    onNavigateToSftp = {}
                )
            }
        }

        // Click the menu button (MoreVert icon)
        composeTestRule
            .onNodeWithContentDescription("More options")
            .performClick()

        // Verify menu items are displayed
        composeTestRule
            .onNodeWithText("Manage pubkeys")
            .assertIsDisplayed()
    }

    @Test
    fun hostListScreen_addButtonCallsCallback() {
        var addHostCalled = false

        composeTestRule.setContent {
            ConnectBotTheme {
                HostListScreen(
                    onNavigateToConsole = {},
                    onNavigateToEditHost = { addHostCalled = true },
                    onNavigateToSettings = {},
                    onNavigateToColors = {},
                    onNavigateToPubkeys = {},
                    onNavigateToPortForwards = {},
                    onNavigateToProfiles = {},
                    onNavigateToHelp = {},
                    onNavigateToSftp = {}
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Add host", ignoreCase = true)
            .performClick()

        assert(addHostCalled)
    }
}
