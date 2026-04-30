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

import android.content.Context
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_BEHIND
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.testing.TestNavHostController
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.service.TerminalManager
import org.connectbot.ui.LocalTerminalManager
import org.connectbot.ui.screens.console.ConsoleScreen
import org.connectbot.ui.theme.ConnectBotTheme
import org.connectbot.util.PreferenceConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
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
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    private fun setContent(mockTerminalManager: TerminalManager? = null) {
        composeTestRule.setContent {
            val context = LocalContext.current
            navController = TestNavHostController(context)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            ConnectBotTheme {
                CompositionLocalProvider(LocalTerminalManager provides mockTerminalManager) {
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
    }

    private fun navigateToConsoleScreen(hostId: Long = -1L) {
        composeTestRule.runOnUiThread {
            navController.navigate("console/$hostId")
        }
    }

    @Test
    fun consoleScreen_showsNotificationWarningSnackbar_whenConnPersistDisabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PreferenceConstants.CONNECTION_PERSIST, false)
            .putBoolean(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED, true)
            .commit()

        setContent()
        navigateToConsoleScreen()

        composeTestRule
            .onNodeWithText("Connection may drop: notification permission denied")
            .assertIsDisplayed()
    }

    @Test
    fun consoleScreen_showsNotificationWarningSnackbar_whenPermissionDeniedOnDevice() {
        // On API 33+, notification permission is denied by default in Robolectric.
        // conn_persist defaults to true, so this exercises the permission-revoked-in-settings path.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return

        val context = ApplicationProvider.getApplicationContext<Context>()
        // Simulate that the permission flow has run before (key exists) but was then revoked in Settings.
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED, false)
            .commit()

        setContent()
        navigateToConsoleScreen()

        composeTestRule
            .onNodeWithText("Connection may drop: notification permission denied")
            .assertIsDisplayed()
    }

    @Test
    fun consoleScreen_noNotificationWarningSnackbar_whenConnPersistEnabledAndPermissionGranted() {
        // On API < 33, POST_NOTIFICATIONS doesn't exist so it's considered granted.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) return

        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PreferenceConstants.CONNECTION_PERSIST, true)
            .commit()

        setContent()
        navigateToConsoleScreen()

        composeTestRule
            .onAllNodes(androidx.compose.ui.test.hasText("Connection may drop: notification permission denied"))
            .fetchSemanticsNodes()
            .let { nodes -> assertEquals("Snackbar should not be shown when both conditions are clear", 0, nodes.size) }
    }

    @Test
    fun consoleScreen_notificationWarningSettingsAction_navigatesToSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PreferenceConstants.CONNECTION_PERSIST, false)
            .putBoolean(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED, true)
            .commit()

        var navigatedToSettings = false

        composeTestRule.setContent {
            val ctx = LocalContext.current
            navController = TestNavHostController(ctx)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            ConnectBotTheme {
                CompositionLocalProvider(LocalTerminalManager provides null) {
                    NavHost(navController = navController, startDestination = "start") {
                        composable("start") {}
                        composable(
                            route = "console/{hostId}",
                            arguments = listOf(navArgument("hostId") { type = NavType.LongType }),
                        ) {
                            ConsoleScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToPortForwards = {},
                                onNavigateToSettings = { navigatedToSettings = true },
                            )
                        }
                    }
                }
            }
        }
        navigateToConsoleScreen()

        composeTestRule
            .onNodeWithText("Settings")
            .performClick()

        assertTrue(navigatedToSettings)
    }

    @Test
    fun consoleScreen_hidesTopAppBar_whenPreferenceSet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PreferenceConstants.TITLEBARHIDE, true)
            .commit()

        setContent()
        navigateToConsoleScreen()

        composeTestRule.onNodeWithTag("top_app_bar").assertIsNotDisplayed()
    }

    @Test
    fun consoleScreen_displaysTopAppBarByDefault() {
        setContent()
        navigateToConsoleScreen()

        composeTestRule
            .onNodeWithTag("top_app_bar")
            .assertIsDisplayed()
    }

    @Test
    fun consoleScreen_displaysBackButton() {
        setContent()
        navigateToConsoleScreen()

        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
    }

    @Test
    fun consoleScreen_backButtonNavigatesUp() {
        setContent()
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
        setContent()
        navigateToConsoleScreen()

        composeTestRule
            .onNodeWithContentDescription("Text input")
            .assertIsDisplayed()
    }

    @Test
    fun consoleScreen_displaysPasteButton() {
        setContent()
        navigateToConsoleScreen()

        composeTestRule
            .onNodeWithContentDescription("Paste")
            .assertIsDisplayed()
    }

    @Test
    @Ignore("rotation is disabled until stability is addressed")
    fun consoleScreen_setsLandscapeOrientation_whenPreferenceSet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
            PreferenceConstants.ROTATION,
            PreferenceConstants.ROTATION_LANDSCAPE,
        ).commit()

        setContent()
        navigateToConsoleScreen()

        composeTestRule.runOnIdle {
            assertEquals(SCREEN_ORIENTATION_LANDSCAPE, composeTestRule.activity.requestedOrientation)
        }
    }

    @Test
    @Ignore("rotation is disabled until stability is addressed")
    fun consoleScreen_setsPortraitOrientation_whenPreferenceSet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
            PreferenceConstants.ROTATION,
            PreferenceConstants.ROTATION_PORTRAIT,
        ).commit()

        setContent()
        navigateToConsoleScreen()

        composeTestRule.runOnIdle {
            assertEquals(SCREEN_ORIENTATION_PORTRAIT, composeTestRule.activity.requestedOrientation)
        }
    }

    @Test
    @Ignore("rotation is disabled until stability is addressed")
    fun consoleScreen_setsSensorOrientation_whenAutoPreferenceSet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
            PreferenceConstants.ROTATION,
            PreferenceConstants.ROTATION_AUTO,
        ).commit()

        setContent()
        navigateToConsoleScreen()

        composeTestRule.runOnIdle {
            assertEquals(SCREEN_ORIENTATION_SENSOR, composeTestRule.activity.requestedOrientation)
        }
    }

    @Test
    @Ignore("rotation is disabled until stability is addressed")
    fun consoleScreen_setsPortraitOrientation_whenDefaultPreferenceAndNoHardwareKeyboard() {
        setContent()
        navigateToConsoleScreen()

        composeTestRule.runOnIdle {
            assertEquals(SCREEN_ORIENTATION_PORTRAIT, composeTestRule.activity.requestedOrientation)
        }
    }

    @Test
    @Ignore("rotation is disabled until stability is addressed")
    fun consoleScreen_restoresOriginalOrientation_onNavigateBack() {
        // Set an initial orientation that's different from what ConsoleScreen might set
        composeTestRule.runOnUiThread {
            composeTestRule.activity.requestedOrientation = SCREEN_ORIENTATION_BEHIND
        }

        setContent()
        navigateToConsoleScreen()

        // Verify orientation changed
        composeTestRule.runOnIdle {
            // Default rotation is portrait (without keyboard in tests)
            assertEquals(SCREEN_ORIENTATION_PORTRAIT, composeTestRule.activity.requestedOrientation)
        }

        // Navigate back
        composeTestRule
            .onNodeWithContentDescription("Back")
            .performClick()

        // Verify orientation restored
        composeTestRule.runOnIdle {
            assertEquals(SCREEN_ORIENTATION_BEHIND, composeTestRule.activity.requestedOrientation)
        }
    }
}
