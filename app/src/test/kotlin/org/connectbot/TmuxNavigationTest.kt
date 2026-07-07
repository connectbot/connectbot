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
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.service.tmux.TmuxPaneRef
import org.connectbot.service.tmux.TmuxWindow
import org.connectbot.ui.screens.console.tmux.PaneDotsIndicator
import org.connectbot.ui.screens.console.tmux.TmuxWindowStrip
import org.connectbot.ui.screens.console.tmux.tmuxSwipeDirection
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TmuxNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private val windows = listOf(
        TmuxWindow(id = "@0", index = 0, name = "vim", active = true, panes = listOf(TmuxPaneRef("%0", 0, 80, 24))),
        TmuxWindow(id = "@1", index = 1, name = "logs", bell = true, panes = listOf(TmuxPaneRef("%1", 0, 80, 24))),
        TmuxWindow(id = "@2", index = 2, name = "build", activity = true, panes = listOf(TmuxPaneRef("%2", 0, 80, 24))),
    )

    @Test
    fun windowStrip_showsWindowsWithBadges() {
        composeTestRule.setContent {
            ConnectBotTheme {
                TmuxWindowStrip(windows = windows, activeWindowId = "@0", onSelectWindow = {})
            }
        }

        composeTestRule.onNodeWithText("0 vim").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 logs").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tmux_window_badge_@1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("tmux_window_badge_@2", useUnmergedTree = true).assertExists()
    }

    @Test
    fun windowStrip_reportsTappedWindowId() {
        var selected = ""
        composeTestRule.setContent {
            ConnectBotTheme {
                TmuxWindowStrip(windows = windows, activeWindowId = "@0", onSelectWindow = { selected = it })
            }
        }

        composeTestRule.onNodeWithText("2 build").performClick()

        composeTestRule.runOnIdle {
            assertEquals("@2", selected)
        }
    }

    @Test
    fun paneDots_renderRequestedCount() {
        composeTestRule.setContent {
            ConnectBotTheme {
                PaneDotsIndicator(count = 3, selectedIndex = 1)
            }
        }
        composeTestRule.onNodeWithTag("tmux_pane_dots").assertExists()
    }

    @Test
    fun swipeDirection_math() {
        val width = 1080
        val slop = 24f
        // Long left swipe → forward
        assertThat(tmuxSwipeDirection(dragX = -400f, dragY = 10f, viewportWidth = width, touchSlop = slop))
            .isEqualTo(1)
        // Long right swipe → back
        assertThat(tmuxSwipeDirection(dragX = 400f, dragY = -20f, viewportWidth = width, touchSlop = slop))
            .isEqualTo(-1)
        // Too short
        assertThat(tmuxSwipeDirection(dragX = -60f, dragY = 0f, viewportWidth = width, touchSlop = slop))
            .isNull()
        // Too diagonal
        assertThat(tmuxSwipeDirection(dragX = -400f, dragY = 350f, viewportWidth = width, touchSlop = slop))
            .isNull()
        // Degenerate viewport
        assertThat(tmuxSwipeDirection(dragX = -400f, dragY = 0f, viewportWidth = 0, touchSlop = slop))
            .isNull()
    }
}
