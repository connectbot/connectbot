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

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.data.entity.Profile
import org.connectbot.terminal.ImeShortcutInputMode
import org.connectbot.ui.screens.settings.SettingsScreen
import org.connectbot.ui.screens.settings.SettingsScreenContent
import org.connectbot.ui.screens.settings.SettingsUiState
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

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
    @Config(sdk = [26, 34])
    fun notificationSettingsLinkAppearsBelowMainSettingAndOpensSettings() {
        var settingsOpened = false
        setSettingsContent(onNotificationSettingsClick = { settingsOpened = true })

        val main = composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_connection_alerts_title))
        val channels = composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_notification_channels_title))
        val wifi = composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_wifilock_title))
        assertTrue(main.fetchSemanticsNode().boundsInRoot.bottom <= channels.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(channels.fetchSemanticsNode().boundsInRoot.bottom <= wifi.fetchSemanticsNode().boundsInRoot.top)

        channels.performClick()
        assertTrue(settingsOpened)
    }

    @Test
    @Config(sdk = [26, 34])
    fun notificationSettingsLinkRemainsAvailableWhenMainSettingIsOff() {
        setSettingsContent(uiState = SettingsUiState(connectionAlerts = false))

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_connection_alerts_disabled_summary)).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_notification_channels_title)).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun blockedNotificationsExplainBackgroundSupportAndOpenSettings() {
        var settingsOpened = false
        setSettingsContent(
            uiState = SettingsUiState(connectionAlerts = false, notificationsAvailable = false),
            onNotificationSettingsClick = { settingsOpened = true },
        )
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_connection_alerts_disabled_summary)).assertIsDisplayed()
        val notice = composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_notifications_blocked))
        notice.performScrollTo().assertIsDisplayed().performClick()
        assertTrue(settingsOpened)
        val screenshot = File("build/reports/connection-alerts-settings.png")
        screenshot.parentFile!!.mkdirs()
        screenshot.outputStream().use { output ->
            composeTestRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    @Test
    @Config(sdk = [24, 25])
    fun legacyAlertTogglesAppearBelowMainSettingAndDefaultToOn() {
        var bellEnabled: Boolean? = null
        var lossEnabled: Boolean? = null
        setSettingsContent(
            onBellNotificationChange = { bellEnabled = it },
            onConnectionLostNotificationChange = { lossEnabled = it },
        )

        val main = composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_connection_alerts_title))
        val bells = composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_bell_notification_title))
        val losses = composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_connection_lost_notification_title))
        val wifi = composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_wifilock_title))
        assertTrue(main.fetchSemanticsNode().boundsInRoot.bottom <= bells.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(bells.fetchSemanticsNode().boundsInRoot.bottom <= losses.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(losses.fetchSemanticsNode().boundsInRoot.bottom <= wifi.fetchSemanticsNode().boundsInRoot.top)

        bells.performClick()
        losses.performClick()
        assertEquals(false, bellEnabled)
        assertEquals(false, lossEnabled)
    }

    @Test
    @Config(sdk = [24, 25])
    fun legacyAlertTogglesAreHiddenWhenMainSettingIsOff() {
        setSettingsContent(uiState = SettingsUiState(connectionAlerts = false))

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_connection_alerts_disabled_summary)).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_bell_notification_title)).assertDoesNotExist()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pref_connection_lost_notification_title)).assertDoesNotExist()
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
    fun settingsScreen_displaysConnectionAlertsPreference() {
        val connectionAlertsTitle = composeTestRule.activity.getString(R.string.pref_connection_alerts_title)

        composeTestRule.setContent {
            ConnectBotTheme {
                SettingsScreen(onNavigateBack = {})
            }
        }

        composeTestRule
            .onNodeWithText(connectionAlertsTitle)
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreen_withHighlightConnectionAlerts_scrollsToAndDisplaysConnectionAlerts() {
        val connectionAlertsTitle = composeTestRule.activity.getString(R.string.pref_connection_alerts_title)

        composeTestRule.setContent {
            ConnectBotTheme {
                SettingsScreen(
                    onNavigateBack = {},
                    highlightItem = "conn_persist",
                )
            }
        }

        composeTestRule
            .onNodeWithText(connectionAlertsTitle)
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreenContent_whenCanAuthenticate_displaysSecurityPreference() {
        val security = composeTestRule.activity.getString(R.string.pref_security_category)
        val authOnLaunch = composeTestRule.activity.getString(R.string.pref_auth_on_launch_title)

        setSettingsContent(
            uiState = SettingsUiState(canAuthenticate = true),
        )

        composeTestRule
            .onNodeWithText(security)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(authOnLaunch)
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreenContent_clickingMemkeysRowCallsCallback() {
        var memkeysValue: Boolean? = null
        val memkeys = composeTestRule.activity.getString(R.string.pref_memkeys_title)

        setSettingsContent(
            uiState = SettingsUiState(memkeys = true),
            onMemkeysChange = { memkeysValue = it },
        )

        composeTestRule
            .onNodeWithText(memkeys)
            .performClick()

        assertTrue(memkeysValue == false)
    }

    @Test
    fun settingsScreenContent_imeShortcutModeReturnsSelectedValue() {
        var selectedMode: ImeShortcutInputMode? = null
        val title = composeTestRule.activity.getString(R.string.pref_ime_shortcut_mode_title)
        val rawKeyEvents = composeTestRule.activity.getString(R.string.pref_ime_shortcut_mode_type_null)

        setSettingsContent(
            onImeShortcutInputModeChange = { selectedMode = it },
        )

        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(title))
        composeTestRule
            .onNodeWithText(title)
            .performClick()
        composeTestRule
            .onNodeWithText(rawKeyEvents)
            .performClick()

        assertTrue(selectedMode == ImeShortcutInputMode.TYPE_NULL)
    }

    @Test
    fun settingsScreenContent_scrollbackDialogConfirmsUpdatedValue() {
        var scrollbackValue: String? = null
        val scrollback = composeTestRule.activity.getString(R.string.pref_scrollback_title)

        setSettingsContent(
            uiState = SettingsUiState(scrollback = "140"),
            onScrollbackChange = { scrollbackValue = it },
        )

        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(scrollback))
        composeTestRule
            .onNodeWithText(scrollback)
            .performClick()
        composeTestRule
            .onNodeWithText("140")
            .performTextReplacement("200")
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(android.R.string.ok))
            .performClick()

        assertTrue(scrollbackValue == "200")
    }

    @Test
    fun settingsScreenContent_customTerminalTypeCanBeAddedAndRemoved() {
        var addedTerminalType: String? = null
        var removedTerminalType: String? = null
        val customTerminal = composeTestRule.activity.getString(R.string.pref_customterminal_title)
        val add = composeTestRule.activity.getString(R.string.button_add)

        setSettingsContent(
            uiState = SettingsUiState(customTerminalTypes = listOf("tmux-256color")),
            onAddCustomTerminalType = { addedTerminalType = it },
            onRemoveCustomTerminalType = { removedTerminalType = it },
        )

        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(customTerminal))
        composeTestRule
            .onNodeWithText("tmux-256color")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.button_remove))
            .performClick()
        assertTrue(removedTerminalType == "tmux-256color")

        composeTestRule
            .onNodeWithText(customTerminal)
            .performClick()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.dialog_customterminal_hint))
            .performTextInput("vt100")
        composeTestRule
            .onNodeWithText(add)
            .performClick()

        assertTrue(addedTerminalType == "vt100")
    }

    @Test
    fun settingsScreenContent_defaultProfileDialogReturnsSelectedProfile() {
        var selectedProfileId: Long? = null
        val defaultProfile = composeTestRule.activity.getString(R.string.pref_default_profile_title)

        setSettingsContent(
            uiState = SettingsUiState(
                defaultProfileId = 2L,
                availableProfiles = listOf(
                    Profile(id = 1L, name = "Work"),
                    Profile(id = 2L, name = "Home"),
                ),
            ),
            onDefaultProfileChange = { selectedProfileId = it },
        )

        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(defaultProfile))
        composeTestRule
            .onNodeWithText("Home")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(defaultProfile)
            .performClick()
        composeTestRule
            .onNodeWithText("Work")
            .performClick()

        assertTrue(selectedProfileId == 1L)
    }

    private fun setSettingsContent(
        uiState: SettingsUiState = SettingsUiState(),
        onAuthOnLaunchChange: (Boolean) -> Unit = {},
        onMemkeysChange: (Boolean) -> Unit = {},
        onConnectionAlertsChange: (Boolean) -> Unit = {},
        onWifilockChange: (Boolean) -> Unit = {},
        onBackupkeysChange: (Boolean) -> Unit = {},
        onScrollbackChange: (String) -> Unit = {},
        onAddCustomTerminalType: (String) -> Unit = {},
        onRemoveCustomTerminalType: (String) -> Unit = {},
        onDefaultProfileChange: (Long) -> Unit = {},
        onImeShortcutInputModeChange: (ImeShortcutInputMode) -> Unit = {},
        onBellNotificationChange: (Boolean) -> Unit = {},
        onConnectionLostNotificationChange: (Boolean) -> Unit = {},
        onNotificationSettingsClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ConnectBotTheme {
                SettingsScreenContent(
                    uiState = uiState,
                    onNavigateBack = {},
                    onAuthOnLaunchChange = onAuthOnLaunchChange,
                    onMemkeysChange = onMemkeysChange,
                    onConnectionAlertsChange = onConnectionAlertsChange,
                    onWifilockChange = onWifilockChange,
                    onBackupkeysChange = onBackupkeysChange,
                    onScrollbackChange = onScrollbackChange,
                    onAddCustomTerminalType = onAddCustomTerminalType,
                    onRemoveCustomTerminalType = onRemoveCustomTerminalType,
                    onFontFamilyChange = {},
                    onAddCustomFont = {},
                    onRemoveCustomFont = {},
                    onClearFontError = {},
                    onImportLocalFont = { _, _ -> },
                    onDeleteLocalFont = {},
                    onClearImportError = {},
                    onDefaultProfileChange = onDefaultProfileChange,
                    onLanguageChange = {},
                    onThemeModeChange = {},
                    onRotationChange = {},
                    onFullscreenChange = {},
                    onTitleBarHideChange = {},
                    onPgUpDnGestureChange = {},
                    onVolumeFontChange = {},
                    onKeepAliveChange = {},
                    onAlwaysVisibleChange = {},
                    onSwipeSessionsChange = {},
                    onImeToggleKeyChange = {},
                    onImeShortcutInputModeChange = onImeShortcutInputModeChange,
                    onShiftFkeysChange = {},
                    onCtrlFkeysChange = {},
                    onStickyModifiersChange = {},
                    onKeyModeChange = {},
                    onCameraChange = {},
                    onBumpyArrowsChange = {},
                    onBellChange = {},
                    onBellVolumeChange = {},
                    onBellVibrateChange = {},
                    onBellNotificationChange = onBellNotificationChange,
                    onConnectionLostNotificationChange = onConnectionLostNotificationChange,
                    onNotificationSettingsClick = onNotificationSettingsClick,
                )
            }
        }
    }
}
