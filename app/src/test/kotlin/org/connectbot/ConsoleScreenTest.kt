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
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.connectbot.data.entity.Host
import org.connectbot.service.ModifierLevel
import org.connectbot.service.ModifierState
import org.connectbot.service.PromptManager
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalKeyListener
import org.connectbot.service.TerminalManager
import org.connectbot.terminal.DelKeyMode
import org.connectbot.ui.LocalTerminalManager
import org.connectbot.ui.screens.console.ConsoleScreen
import org.connectbot.ui.screens.console.ConsoleUiState
import org.connectbot.ui.screens.console.ConsoleViewModel
import org.connectbot.ui.screens.console.consoleTerminalContentOverride
import org.connectbot.ui.theme.ConnectBotTheme
import org.connectbot.util.PreferenceConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

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
        consoleTerminalContentOverride = null
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    private fun setContent(
        mockTerminalManager: TerminalManager? = null,
        mockConsoleViewModel: ConsoleViewModel? = null,
    ) {
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
                            if (mockConsoleViewModel != null) {
                                ConsoleScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToPortForwards = {},
                                    viewModel = mockConsoleViewModel,
                                )
                            } else {
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
    }

    private fun navigateToConsoleScreen(hostId: Long = -1L) {
        composeTestRule.runOnUiThread {
            navController.navigate("console/$hostId")
        }
    }

    @Test
    fun consoleScreen_showsNotificationWarningSnackbar_whenConnPersistDisabled() {
        val warning = composeTestRule.activity.getString(R.string.notification_permission_console_warning)
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PreferenceConstants.CONNECTION_PERSIST, false)
            .putBoolean(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED, true)
            .commit()

        setContent()
        navigateToConsoleScreen()

        composeTestRule
            .onNodeWithText(warning)
            .assertIsDisplayed()
    }

    @Test
    fun consoleScreen_showsNotificationWarningSnackbar_whenPermissionDeniedOnDevice() {
        // On API 33+, notification permission is denied by default in Robolectric.
        // conn_persist defaults to true, so this exercises the permission-revoked-in-settings path.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return

        val warning = composeTestRule.activity.getString(R.string.notification_permission_console_warning)
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Simulate that the permission flow has run before (key exists) but was then revoked in Settings.
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED, false)
            .commit()

        setContent()
        navigateToConsoleScreen()

        composeTestRule
            .onNodeWithText(warning)
            .assertIsDisplayed()
    }

    @Test
    fun consoleScreen_noNotificationWarningSnackbar_whenConnPersistEnabledAndPermissionGranted() {
        // On API < 33, POST_NOTIFICATIONS doesn't exist so it's considered granted.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) return

        val warning = composeTestRule.activity.getString(R.string.notification_permission_console_warning)
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PreferenceConstants.CONNECTION_PERSIST, true)
            .commit()

        setContent()
        navigateToConsoleScreen()

        composeTestRule
            .onAllNodes(androidx.compose.ui.test.hasText(warning))
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

    @Test
    fun consoleScreen_keepsScreenAwake_whenPreferenceEnabledAndConnectionActive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PreferenceConstants.KEEP_ALIVE, true)
            .commit()

        val mockViewModel = mock(ConsoleViewModel::class.java)
        val mockBridge = mock(TerminalBridge::class.java)
        `when`(mockBridge.isDisconnected).thenReturn(false)

        val uiStateFlow = MutableStateFlow(
            ConsoleUiState(
                bridges = listOf(mockBridge),
                currentBridgeIndex = 0,
                isLoading = true,
            ),
        )
        val networkStatusMessages = MutableSharedFlow<String>()

        `when`(mockViewModel.uiState).thenReturn(uiStateFlow)
        `when`(mockViewModel.networkStatusMessages).thenReturn(networkStatusMessages)
        `when`(mockViewModel.shouldShowNotificationWarning()).thenReturn(false)

        setContent(mockConsoleViewModel = mockViewModel)
        navigateToConsoleScreen(hostId = 1L)

        composeTestRule.runOnIdle {
            val rootLayout = composeTestRule.activity.findViewById<View>(android.R.id.content)
            assertTrue("Screen should be kept awake", hasKeepScreenOn(rootLayout))
        }
    }

    @Test
    fun consoleScreen_doesNotKeepScreenAwake_whenPreferenceDisabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PreferenceConstants.KEEP_ALIVE, false)
            .commit()

        val mockViewModel = mock(ConsoleViewModel::class.java)
        val mockBridge = mock(TerminalBridge::class.java)
        `when`(mockBridge.isDisconnected).thenReturn(false)

        val uiStateFlow = MutableStateFlow(
            ConsoleUiState(
                bridges = listOf(mockBridge),
                currentBridgeIndex = 0,
                isLoading = true,
            ),
        )
        val networkStatusMessages = MutableSharedFlow<String>()

        `when`(mockViewModel.uiState).thenReturn(uiStateFlow)
        `when`(mockViewModel.networkStatusMessages).thenReturn(networkStatusMessages)
        `when`(mockViewModel.shouldShowNotificationWarning()).thenReturn(false)

        setContent(mockConsoleViewModel = mockViewModel)
        navigateToConsoleScreen(hostId = 1L)

        composeTestRule.runOnIdle {
            val rootLayout = composeTestRule.activity.findViewById<View>(android.R.id.content)
            assertFalse("Screen should not be kept awake when preference is disabled", hasKeepScreenOn(rootLayout))
        }
    }

    @Test
    fun consoleScreen_doesNotKeepScreenAwake_whenDisconnected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PreferenceConstants.KEEP_ALIVE, true)
            .commit()

        val mockViewModel = mock(ConsoleViewModel::class.java)
        val mockBridge = mock(TerminalBridge::class.java)
        `when`(mockBridge.isDisconnected).thenReturn(true)

        val uiStateFlow = MutableStateFlow(
            ConsoleUiState(
                bridges = listOf(mockBridge),
                currentBridgeIndex = 0,
                isLoading = true,
            ),
        )
        val networkStatusMessages = MutableSharedFlow<String>()

        `when`(mockViewModel.uiState).thenReturn(uiStateFlow)
        `when`(mockViewModel.networkStatusMessages).thenReturn(networkStatusMessages)
        `when`(mockViewModel.shouldShowNotificationWarning()).thenReturn(false)

        setContent(mockConsoleViewModel = mockViewModel)
        navigateToConsoleScreen(hostId = 1L)

        composeTestRule.runOnIdle {
            val rootLayout = composeTestRule.activity.findViewById<View>(android.R.id.content)
            assertFalse("Screen should not be kept awake when disconnected", hasKeepScreenOn(rootLayout))
        }
    }

    @Test
    fun consoleScreen_showsSessionSwitcherForMultipleSessions() {
        useFakeTerminalPage()
        val bridge1 = createRenderableBridge(1L, "host1")
        val bridge2 = createRenderableBridge(2L, "host2")
        val mockViewModel = createMockViewModel(
            ConsoleUiState(
                bridges = listOf(bridge1, bridge2),
                currentBridgeIndex = 0,
                isLoading = false,
            ),
        )

        setContent(mockConsoleViewModel = mockViewModel)
        navigateToConsoleScreen(hostId = 1L)

        composeTestRule
            .onNodeWithContentDescription("Switch session")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.onNodeWithText("Switch session").assertIsDisplayed()
        composeTestRule.onNodeWithText("\u2022 host1").assertIsDisplayed()
        composeTestRule.onNodeWithText("host2").performClick()

        verify(mockViewModel).selectBridge(1)
    }

    @Test
    fun consoleScreen_sessionMenuNavigatesBetweenSessions() {
        useFakeTerminalPage()
        val bridge1 = createRenderableBridge(1L, "host1")
        val bridge2 = createRenderableBridge(2L, "host2")
        val mockViewModel = createMockViewModel(
            ConsoleUiState(
                bridges = listOf(bridge1, bridge2),
                currentBridgeIndex = 0,
                isLoading = false,
            ),
        )

        setContent(mockConsoleViewModel = mockViewModel)
        navigateToConsoleScreen(hostId = 1L)

        composeTestRule
            .onNodeWithContentDescription("More options")
            .performClick()

        composeTestRule.onNodeWithText("Previous session").assertIsNotEnabled()
        composeTestRule
            .onNodeWithText("Next session")
            .assertIsEnabled()
            .performClick()

        verify(mockViewModel).selectBridge(1)
    }

    @Test
    fun consoleScreen_sessionMenuNavigatesToPreviousSession() {
        useFakeTerminalPage()
        val bridge1 = createRenderableBridge(1L, "host1")
        val bridge2 = createRenderableBridge(2L, "host2")
        val mockViewModel = createMockViewModel(
            ConsoleUiState(
                bridges = listOf(bridge1, bridge2),
                currentBridgeIndex = 1,
                isLoading = false,
            ),
        )

        setContent(mockConsoleViewModel = mockViewModel)
        navigateToConsoleScreen(hostId = 2L)

        composeTestRule
            .onNodeWithContentDescription("More options")
            .performClick()

        composeTestRule
            .onNodeWithText("Previous session")
            .assertIsEnabled()
            .performClick()

        verify(mockViewModel).selectBridge(0)
    }

    @Test
    fun consoleScreen_swipeLeftSelectsNextSession() {
        useFakeTerminalPage()
        enableSessionSwipePreference()
        val bridge1 = createRenderableBridge(1L, "host1")
        val bridge2 = createRenderableBridge(2L, "host2")
        val mockViewModel = createMockViewModel(
            ConsoleUiState(
                bridges = listOf(bridge1, bridge2),
                currentBridgeIndex = 0,
                isLoading = false,
            ),
        )

        setContent(mockConsoleViewModel = mockViewModel)
        navigateToConsoleScreen(hostId = 1L)

        composeTestRule.onNodeWithTag("terminal").performTouchInput {
            swipeLeft()
        }

        verify(mockViewModel).selectBridge(1)
    }

    @Test
    fun consoleScreen_swipeRightSelectsPreviousSession() {
        useFakeTerminalPage()
        enableSessionSwipePreference()
        val bridge1 = createRenderableBridge(1L, "host1")
        val bridge2 = createRenderableBridge(2L, "host2")
        val mockViewModel = createMockViewModel(
            ConsoleUiState(
                bridges = listOf(bridge1, bridge2),
                currentBridgeIndex = 1,
                isLoading = false,
            ),
        )

        setContent(mockConsoleViewModel = mockViewModel)
        navigateToConsoleScreen(hostId = 2L)

        composeTestRule.onNodeWithTag("terminal").performTouchInput {
            swipeRight()
        }

        verify(mockViewModel).selectBridge(0)
    }

    @Test
    fun consoleScreen_disconnectedOverlayReconnectsBridge() {
        useFakeTerminalPage()
        val bridge = createRenderableBridge(
            id = 1L,
            nickname = "host1",
            isDisconnected = true,
            isConnecting = false,
        )
        val mockViewModel = createMockViewModel(
            ConsoleUiState(
                bridges = listOf(bridge),
                currentBridgeIndex = 0,
                isLoading = false,
            ),
        )

        setContent(mockConsoleViewModel = mockViewModel)
        navigateToConsoleScreen(hostId = 1L)

        composeTestRule.onNodeWithText("Connection Lost").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reconnect").performClick()

        verify(mockViewModel).reconnect(bridge)
    }

    @Test
    fun consoleScreen_disconnectedOverlayClosesBridge() {
        useFakeTerminalPage()
        val bridge = createRenderableBridge(
            id = 1L,
            nickname = "host1",
            isDisconnected = true,
            isConnecting = false,
            isSessionOpen = false,
        )
        val mockViewModel = createMockViewModel(
            ConsoleUiState(
                bridges = listOf(bridge),
                currentBridgeIndex = 0,
                isLoading = false,
            ),
        )

        setContent(mockConsoleViewModel = mockViewModel)
        navigateToConsoleScreen(hostId = 1L)

        composeTestRule.onNodeWithText("Connection Lost").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Close")[0].performClick()

        verify(bridge).dispatchDisconnect(org.connectbot.service.DisconnectReason.USER_REQUESTED)
    }

    private fun useFakeTerminalPage() {
        consoleTerminalContentOverride = { modifier ->
            Box(modifier = modifier)
        }
    }

    private fun enableSessionSwipePreference() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(PreferenceConstants.SWIPE_SESSIONS, true)
            .commit()
    }

    private fun createMockViewModel(uiState: ConsoleUiState): ConsoleViewModel {
        val mockViewModel = mock(ConsoleViewModel::class.java)
        `when`(mockViewModel.uiState).thenReturn(MutableStateFlow(uiState))
        `when`(mockViewModel.networkStatusMessages).thenReturn(MutableSharedFlow())
        `when`(mockViewModel.shouldShowNotificationWarning()).thenReturn(false)
        return mockViewModel
    }

    private fun createRenderableBridge(
        id: Long,
        nickname: String,
        isDisconnected: Boolean = false,
        isConnecting: Boolean = false,
        isSessionOpen: Boolean = true,
    ): TerminalBridge {
        val bridge = mock(TerminalBridge::class.java)
        val host = Host(
            id = id,
            nickname = nickname,
            hostname = nickname,
            username = "test",
            protocol = "ssh",
            port = 22,
        )
        val keyHandler = mock(TerminalKeyListener::class.java)
        `when`(keyHandler.modifierState).thenReturn(
            MutableStateFlow(
                ModifierState(
                    ctrlState = ModifierLevel.OFF,
                    altState = ModifierLevel.OFF,
                    shiftState = ModifierLevel.OFF,
                ),
            ),
        )

        `when`(bridge.host).thenReturn(host)
        `when`(bridge.fontFamily).thenReturn(null)
        `when`(bridge.fontSizeFlow).thenReturn(MutableStateFlow(10f))
        `when`(bridge.delKeyModeFlow).thenReturn(MutableStateFlow(DelKeyMode.Delete))
        `when`(bridge.keyHandler).thenReturn(keyHandler)
        `when`(bridge.promptManager).thenReturn(PromptManager())
        `when`(bridge.authBanners).thenReturn(MutableStateFlow(emptyList()))
        `when`(bridge.isSessionOpen).thenReturn(isSessionOpen)
        `when`(bridge.isDisconnected).thenReturn(isDisconnected)
        `when`(bridge.isConnecting).thenReturn(isConnecting)
        `when`(bridge.profileForceSizeRows).thenReturn(null)
        `when`(bridge.profileForceSizeColumns).thenReturn(null)
        `when`(bridge.canFowardPorts()).thenReturn(false)
        `when`(bridge.scanForURLs()).thenReturn(emptyList())
        return bridge
    }

    private fun hasKeepScreenOn(view: View): Boolean {
        if (view.keepScreenOn) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (hasKeepScreenOn(view.getChildAt(i))) return true
            }
        }
        return false
    }
}
