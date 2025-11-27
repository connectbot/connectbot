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

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.ui.screens.hosteditor.HostEditorScreen
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HostEditorScreenTest {
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
                // Define a test route for HostEditorScreen
                NavHost(navController = navController, startDestination = "start") {
                    composable("start") {
                        // Empty start destination - tests will navigate to hostEditor
                    }
                    composable(
                        route = "hostEditor/{hostId}",
                        arguments = listOf(navArgument("hostId") { type = NavType.LongType })
                    ) {
                        HostEditorScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    // Helper function to navigate to HostEditorScreen with a specific hostId
    private fun navigateToHostEditorScreen(hostId: Long) {
        composeTestRule.runOnUiThread {
            navController.navigate("hostEditor/$hostId")
        }
    }

    @Test
    fun hostEditorScreen_newHost_displaysAddTitle() {
        navigateToHostEditorScreen(-1L)

        composeTestRule
            .onNodeWithTag("add_host_button")
            .assertIsDisplayed()
    }

    @Test
    fun hostEditorScreen_newHost_saveButtonDisabledByDefault() {
        navigateToHostEditorScreen(-1L)

        // The save button should be disabled when quick connect is empty
        // There are two "Add Host" texts - one in title, one in button
        // We need to find the button specifically
        composeTestRule
            .onNodeWithTag("add_host_button")
            .assertIsNotEnabled()
    }

    @Test
    fun hostEditorScreen_newHost_saveButtonEnabledAfterInput() {
        navigateToHostEditorScreen(-1L)

        // Enter text in quick connect field
        composeTestRule
            .onNodeWithText("Quick connect")
            .performClick()
            .performTextInput("test@example.com")

        // Wait for the state to update
        composeTestRule.waitForIdle()

        // The save button should now be enabled
        composeTestRule
            .onNodeWithTag("add_host_button")
            .assertIsEnabled()
    }

    @Test
    fun hostEditorScreen_newHost_callsNavigateBackOnSave() {
        navigateToHostEditorScreen(-1L)

        // Enter text in quick connect field
        composeTestRule
            .onNodeWithText("Quick connect")
            .performClick()
            .performTextInput("test@example.com")

        composeTestRule.waitForIdle()

        // Click save button (the second "Add Host" text)
        composeTestRule
            .onNodeWithTag("add_host_button")
            .performClick()

        // Verify navigateBack was called (by checking if the navController popped back to start)
        composeTestRule.runOnIdle {
            assertTrue(navController.currentBackStackEntry?.destination?.route == "start")
        }
    }

    @Test
    fun hostEditorScreen_hasBackButton() {
        navigateToHostEditorScreen(-1L)

        // Click back button
        composeTestRule
            .onNodeWithContentDescription("Navigate up")
            .performClick()

        // Verify navigateBack was called (by checking if the navController popped back to start)
        composeTestRule.runOnIdle {
            assertTrue(navController.currentBackStackEntry?.destination?.route == "start")
        }
    }

    @Test
    fun hostEditorScreen_localProtocol_hidesUserHostPortFields() {
        navigateToHostEditorScreen(-1L)

        // Click "Show advanced options" to enter expanded mode
        composeTestRule
            .onNodeWithText("Show advanced options")
            .performClick()

        composeTestRule.waitForIdle()

        // Initially, with default protocol (ssh), fields should be visible
        composeTestRule
            .onNodeWithText("Username")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Host")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Port")
            .assertIsDisplayed()

        // Click on protocol dropdown
        composeTestRule
            .onNodeWithText("ssh")
            .performClick()

        composeTestRule.waitForIdle()

        // Select "local" protocol
        composeTestRule
            .onNodeWithText("local")
            .performClick()

        composeTestRule.waitForIdle()

        // Now username, hostname, and port fields should be hidden
        composeTestRule
            .onNodeWithText("Username")
            .assertIsNotDisplayed()
        composeTestRule
            .onNodeWithText("Host")
            .assertIsNotDisplayed()
        composeTestRule
            .onNodeWithText("Port")
            .assertIsNotDisplayed()
    }

    @Test
    fun hostEditorScreen_localProtocol_saveButtonEnabled() {
        navigateToHostEditorScreen(-1L)

        // Click "Show advanced options" to enter expanded mode
        composeTestRule
            .onNodeWithText("Show advanced options")
            .performClick()

        composeTestRule.waitForIdle()

        // Save button should be disabled initially (no hostname entered)
        composeTestRule
            .onNodeWithTag("add_host_button")
            .assertIsNotEnabled()

        // Click on protocol dropdown
        composeTestRule
            .onNodeWithText("ssh")
            .performClick()

        composeTestRule.waitForIdle()

        // Select "local" protocol
        composeTestRule
            .onNodeWithText("local")
            .performClick()

        composeTestRule.waitForIdle()

        // Now save button should be enabled even without hostname
        // because local protocol doesn't require hostname
        composeTestRule
            .onNodeWithTag("add_host_button")
            .assertIsEnabled()
    }
}
