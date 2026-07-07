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
        SessionTabData(key = "host:1", nickname = "web-01", color = "#F44336", isDisconnected = false),
        SessionTabData(key = "host:2", nickname = "db-primary", color = "#4CAF50", isDisconnected = false),
        SessionTabData(key = "host:3", nickname = "staging", color = null, isDisconnected = true),
        SessionTabData(
            key = "tmux:1:\$0",
            nickname = "main",
            color = "#F44336",
            isDisconnected = false,
            isTmux = true,
            bellBadge = true,
        ),
        SessionTabData(
            key = "tmux:1:\$1",
            nickname = "deploy",
            color = "#F44336",
            isDisconnected = true,
            isTmux = true,
            isAttaching = true,
        ),
    )

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun setContent(
        selectedKey: String = "host:1",
        onSelectTab: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            ConnectBotTheme {
                SessionTabStrip(
                    tabs = tabs,
                    selectedKey = selectedKey,
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
        setContent(selectedKey = "host:2")

        composeTestRule.onNodeWithTag("session_tab_host:1").assertIsNotSelected()
        composeTestRule.onNodeWithTag("session_tab_host:2").assertIsSelected()
        composeTestRule.onNodeWithTag("session_tab_host:3").assertIsNotSelected()
    }

    @Test
    fun sessionTabStrip_clickingTabReportsItsKey() {
        var selected = ""
        setContent(onSelectTab = { selected = it })

        composeTestRule.onNodeWithText("staging").performClick()

        composeTestRule.runOnIdle {
            assertEquals("host:3", selected)
        }
    }

    @Test
    fun sessionTabStrip_showsTmuxSessionTabsWithBadgesAndSpinner() {
        setContent()

        composeTestRule.onNodeWithText("main").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_badge_tmux:1:\$0", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("tab_attaching_tmux:1:\$1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun sessionTabStrip_clickingTmuxTabReportsItsKey() {
        var selected = ""
        setContent(onSelectTab = { selected = it })

        composeTestRule.onNodeWithText("main").performClick()

        composeTestRule.runOnIdle {
            assertEquals("tmux:1:\$0", selected)
        }
    }
}
