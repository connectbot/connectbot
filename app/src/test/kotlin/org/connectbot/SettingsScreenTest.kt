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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.ui.screens.settings.SettingsScreen
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun settingsScreen_displaysTitle() {
        val title = composeTestRule.activity.getString(R.string.title_settings)

        composeTestRule.setContent {
            ConnectBotTheme {
                SettingsScreen(onNavigateBack = {})
            }
        }

        composeTestRule
            .onNodeWithText(title)
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreen_hasBackButton() {
        var backCalled = false
        val navigateUp = composeTestRule.activity.getString(R.string.button_navigate_up)

        composeTestRule.setContent {
            ConnectBotTheme {
                SettingsScreen(onNavigateBack = { backCalled = true })
            }
        }

        composeTestRule
            .onNodeWithContentDescription(navigateUp)
            .performClick()

        assertTrue(backCalled)
    }

    @Test
    fun settingsScreen_displaysConnPersistPreference() {
        val connPersistTitle = composeTestRule.activity.getString(R.string.pref_conn_persist_title)

        composeTestRule.setContent {
            ConnectBotTheme {
                SettingsScreen(onNavigateBack = {})
            }
        }

        composeTestRule
            .onNodeWithText(connPersistTitle)
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreen_withHighlightConnPersist_scrollsToAndDisplaysConnPersist() {
        val connPersistTitle = composeTestRule.activity.getString(R.string.pref_conn_persist_title)

        composeTestRule.setContent {
            ConnectBotTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    highlightItem = "conn_persist",
                )
            }
        }

        composeTestRule
            .onNodeWithText(connPersistTitle)
            .assertIsDisplayed()
    }
}
