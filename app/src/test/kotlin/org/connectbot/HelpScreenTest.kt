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
import org.connectbot.ui.screens.help.HelpScreen
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HelpScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun helpScreen_displaysTitle() {
        composeTestRule.setContent {
            ConnectBotTheme {
                HelpScreen(
                    onNavigateBack = {},
                    onNavigateToHints = {},
                    onNavigateToEula = {},
                    onNavigateToContact = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Help")
            .assertIsDisplayed()
    }

    @Test
    fun helpScreen_hasBackButton() {
        var backCalled = false

        composeTestRule.setContent {
            ConnectBotTheme {
                HelpScreen(
                    onNavigateBack = { backCalled = true },
                    onNavigateToHints = {},
                    onNavigateToEula = {},
                    onNavigateToContact = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Navigate up")
            .performClick()

        assertTrue(backCalled)
    }

    @Test
    fun helpScreen_displaysAboutSection() {
        composeTestRule.setContent {
            ConnectBotTheme {
                HelpScreen(
                    onNavigateBack = {},
                    onNavigateToHints = {},
                    onNavigateToEula = {},
                    onNavigateToContact = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("About")
            .assertIsDisplayed()
    }

    @Test
    fun helpScreen_hintsItemNavigates() {
        var hintsCalled = false

        composeTestRule.setContent {
            ConnectBotTheme {
                HelpScreen(
                    onNavigateBack = {},
                    onNavigateToHints = { hintsCalled = true },
                    onNavigateToEula = {},
                    onNavigateToContact = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Hints")
            .performClick()

        assertTrue(hintsCalled)
    }

    @Test
    fun helpScreen_eulaItemNavigates() {
        var eulaCalled = false

        composeTestRule.setContent {
            ConnectBotTheme {
                HelpScreen(
                    onNavigateBack = {},
                    onNavigateToHints = {},
                    onNavigateToEula = { eulaCalled = true },
                    onNavigateToContact = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Terms & Conditions")
            .performClick()

        assertTrue(eulaCalled)
    }

    @Test
    fun helpScreen_contactItemNavigates() {
        var contactCalled = false

        composeTestRule.setContent {
            ConnectBotTheme {
                HelpScreen(
                    onNavigateBack = {},
                    onNavigateToHints = {},
                    onNavigateToEula = {},
                    onNavigateToContact = { contactCalled = true },
                )
            }
        }

        composeTestRule
            .onNodeWithText("Contact & Support")
            .performClick()

        assertTrue(contactCalled)
    }
}
