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

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.ui.screens.console.ConsoleScreen
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ConsoleScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        hiltRule.inject()
        composeTestRule.setContent {
            val context = LocalContext.current
            navController = TestNavHostController(context)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            ConnectBotTheme {
                NavHost(navController = navController, startDestination = "start") {
                    composable("start") {}
                    composable(
                        route = "console/{hostId}",
                        arguments = listOf(navArgument("hostId") { type = NavType.LongType }),
                    ) {
                        ConsoleScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToPortForwards = {},
                        )
                    }
                }
            }
        }
    }

    private fun navigateToConsoleScreen(hostId: Long = -1L) {
        composeTestRule.runOnUiThread {
            navController.navigate("console/$hostId")
        }
    }

    @Test
    fun consoleScreen_displaysBackButton() {
        navigateToConsoleScreen()

        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
    }

    @Test
    fun consoleScreen_backButtonNavigatesUp() {
        navigateToConsoleScreen()

        composeTestRule
            .onNodeWithContentDescription("Back")
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(navController.currentBackStackEntry?.destination?.route == "start")
        }
    }

    @Test
    fun consoleScreen_displaysTextInputButton() {
        navigateToConsoleScreen()

        composeTestRule
            .onNodeWithContentDescription("Text input")
            .assertIsDisplayed()
    }

    @Test
    fun consoleScreen_displaysPasteButton() {
        navigateToConsoleScreen()

        composeTestRule
            .onNodeWithContentDescription("Paste")
            .assertIsDisplayed()
    }
}
