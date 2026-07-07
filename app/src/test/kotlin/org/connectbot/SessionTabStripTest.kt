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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.ui.screens.console.SessionTabData
import org.connectbot.ui.screens.console.SessionTabStrip
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SessionTabStripTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private val tabs = listOf(
        SessionTabData(hostId = 1L, nickname = "web-01", color = "#F44336", isDisconnected = false),
        SessionTabData(hostId = 2L, nickname = "db-primary", color = "#4CAF50", isDisconnected = false),
        SessionTabData(hostId = 3L, nickname = "staging", color = null, isDisconnected = true),
    )

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun setContent(
        selectedIndex: Int = 0,
        onSelectTab: (Int) -> Unit = {},
    ) {
        composeTestRule.setContent {
            ConnectBotTheme {
                SessionTabStrip(
                    tabs = tabs,
                    selectedIndex = selectedIndex,
                    onSelectTab = onSelectTab,
                )
            }
        }
    }

    @Test
    fun sessionTabStrip_displaysAllSessionNicknames() {
        setContent()

        composeTestRule.onNodeWithText("web-01").assertIsDisplayed()
        composeTestRule.onNodeWithText("db-primary").assertIsDisplayed()
        composeTestRule.onNodeWithText("staging").assertIsDisplayed()
    }

    @Test
    fun sessionTabStrip_marksOnlySelectedTabAsSelected() {
        setContent(selectedIndex = 1)

        composeTestRule.onNodeWithTag("session_tab_1").assertIsNotSelected()
        composeTestRule.onNodeWithTag("session_tab_2").assertIsSelected()
        composeTestRule.onNodeWithTag("session_tab_3").assertIsNotSelected()
    }

    @Test
    fun sessionTabStrip_clickingTabReportsItsIndex() {
        var selected = -1
        setContent(selectedIndex = 0, onSelectTab = { selected = it })

        composeTestRule.onNodeWithText("staging").performClick()

        composeTestRule.runOnIdle {
            assertEquals(2, selected)
        }
    }
}
