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
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.data.entity.Profile
import org.connectbot.ui.screens.settings.SettingsScreen
import org.connectbot.ui.screens.settings.SettingsScreenContent
import org.connectbot.ui.screens.settings.SettingsUiState
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
        onConnPersistChange: (Boolean) -> Unit = {},
        onWifilockChange: (Boolean) -> Unit = {},
        onReconnectMaxAttemptsChange: (String) -> Unit = {},
        onReconnectIntervalChange: (String) -> Unit = {},
        onReconnectBackoffChange: (Boolean) -> Unit = {},
        onBackupkeysChange: (Boolean) -> Unit = {},
        onScrollbackChange: (String) -> Unit = {},
        onAddCustomTerminalType: (String) -> Unit = {},
        onRemoveCustomTerminalType: (String) -> Unit = {},
        onDefaultProfileChange: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            ConnectBotTheme {
                SettingsScreenContent(
                    uiState = uiState,
                    onNavigateBack = {},
                    onAuthOnLaunchChange = onAuthOnLaunchChange,
                    onMemkeysChange = onMemkeysChange,
                    onConnPersistChange = onConnPersistChange,
                    onWifilockChange = onWifilockChange,
                    onReconnectMaxAttemptsChange = onReconnectMaxAttemptsChange,
                    onReconnectIntervalChange = onReconnectIntervalChange,
                    onReconnectBackoffChange = onReconnectBackoffChange,
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
                    onEinkModeChange = {},
                    onRotationChange = {},
                    onFullscreenChange = {},
                    onTitleBarHideChange = {},
                    onPgUpDnGestureChange = {},
                    onVolumeFontChange = {},
                    onVolumeTmuxPanesChange = {},
                    onKeepAliveChange = {},
                    onAlwaysVisibleChange = {},
                    onSwipeSessionsChange = {},
                    onShiftFkeysChange = {},
                    onCtrlFkeysChange = {},
                    onStickyModifiersChange = {},
                    onKeyModeChange = {},
                    onKeyboardKeySizeChange = {},
                    onCameraChange = {},
                    onBumpyArrowsChange = {},
                    onBellChange = {},
                    onBellVolumeChange = {},
                    onBellVibrateChange = {},
                    onBellNotificationChange = {},
                    onSshKeepaliveIntervalChange = {},
                )
            }
        }
    }
}
