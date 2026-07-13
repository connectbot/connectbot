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

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.connectbot.service.BridgeConnectionPhase
import org.connectbot.service.BridgeConnectionState
import org.connectbot.service.DisconnectReason
import org.connectbot.ui.screens.console.ConnectionStatusOverlays
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ConnectionStatusOverlaysTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun setOverlays(
        isConnecting: Boolean = false,
        isDisconnected: Boolean = false,
        hasPrompt: Boolean = false,
        reconnectAttempts: Int = 0,
        disconnectReason: DisconnectReason = DisconnectReason.UNKNOWN,
        onClose: () -> Unit = {},
        onReconnect: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ConnectBotTheme {
                ConnectionStatusOverlays(
                    isConnecting = isConnecting,
                    isDisconnected = isDisconnected,
                    hasPrompt = hasPrompt,
                    hostNickname = HOST_NICKNAME,
                    reconnectAttempts = reconnectAttempts,
                    disconnectReason = disconnectReason,
                    onClose = onClose,
                    onReconnect = onReconnect,
                )
            }
        }
    }

    private fun string(resId: Int, vararg args: Any) = composeTestRule.activity.getString(resId, *args)

    @Test
    fun connecting_showsConnectingStatus() {
        setOverlays(isConnecting = true)

        composeTestRule
            .onNodeWithText(string(R.string.console_connecting_status, HOST_NICKNAME))
            .assertIsDisplayed()
    }

    @Test
    fun replayedConnectedState_removesConnectingStatus() {
        val connection = MutableStateFlow(
            BridgeConnectionState(phase = BridgeConnectionPhase.CONNECTED),
        )
        composeTestRule.setContent {
            val state by connection.collectAsState()
            ConnectBotTheme {
                ConnectionStatusOverlays(
                    isConnecting = state.phase == BridgeConnectionPhase.CONNECTING,
                    isDisconnected = state.phase == BridgeConnectionPhase.DISCONNECTED,
                    hasPrompt = false,
                    hostNickname = HOST_NICKNAME,
                    reconnectAttempts = state.reconnectAttempts,
                    disconnectReason = state.disconnectReason,
                    onClose = {},
                    onReconnect = {},
                )
            }
        }
        composeTestRule
            .onAllNodes(androidx.compose.ui.test.hasText(string(R.string.console_connecting_status, HOST_NICKNAME)))
            .fetchSemanticsNodes()
            .let { nodes -> assertEquals(0, nodes.size) }
    }

    @Test
    fun reconnecting_showsAttemptCount() {
        setOverlays(isConnecting = true, reconnectAttempts = 2)

        composeTestRule
            .onNodeWithText(string(R.string.console_reconnecting_status, HOST_NICKNAME, 2))
            .assertIsDisplayed()
    }

    @Test
    fun connecting_withPrompt_hidesStatus() {
        setOverlays(isConnecting = true, hasPrompt = true)

        composeTestRule
            .onAllNodes(androidx.compose.ui.test.hasText(string(R.string.console_connecting_status, HOST_NICKNAME)))
            .fetchSemanticsNodes()
            .let { nodes -> assertEquals(0, nodes.size) }
    }

    @Test
    fun disconnected_showsDisconnectReason() {
        setOverlays(isDisconnected = true, disconnectReason = DisconnectReason.IO_ERROR)

        composeTestRule
            .onNodeWithText(string(R.string.alert_disconnect_msg))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.disconnect_reason_io_error))
            .assertIsDisplayed()
    }

    @Test
    fun disconnected_remoteEof_showsSessionEndedInsteadOfConnectionLost() {
        // A clean remote exit (Ctrl+D) is presented as a session that ended,
        // not a lost connection. https://github.com/connectbot/connectbot/issues/2214
        setOverlays(isDisconnected = true, disconnectReason = DisconnectReason.REMOTE_EOF)

        composeTestRule
            .onNodeWithText(string(R.string.console_session_ended))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.disconnect_reason_remote_eof))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodes(androidx.compose.ui.test.hasText(string(R.string.alert_disconnect_msg)))
            .fetchSemanticsNodes()
            .let { nodes -> assertEquals(0, nodes.size) }
    }

    @Test
    fun disconnected_remoteEof_closeButton_invokesCallback() {
        var closeRequested = false
        setOverlays(
            isDisconnected = true,
            disconnectReason = DisconnectReason.REMOTE_EOF,
            onClose = { closeRequested = true },
        )

        composeTestRule
            .onNodeWithText(string(R.string.console_menu_close))
            .performClick()

        assertTrue(closeRequested)
    }

    @Test
    fun disconnected_userRequested_showsNoReasonLine() {
        setOverlays(isDisconnected = true, disconnectReason = DisconnectReason.USER_REQUESTED)

        composeTestRule
            .onNodeWithText(string(R.string.alert_disconnect_msg))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodes(androidx.compose.ui.test.hasText(string(R.string.disconnect_reason_remote_eof)))
            .fetchSemanticsNodes()
            .let { nodes -> assertEquals(0, nodes.size) }
    }

    @Test
    fun disconnected_whileConnecting_showsConnectingInstead() {
        setOverlays(isDisconnected = true, isConnecting = true, reconnectAttempts = 1)

        composeTestRule
            .onNodeWithText(string(R.string.console_reconnecting_status, HOST_NICKNAME, 1))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodes(androidx.compose.ui.test.hasText(string(R.string.alert_disconnect_msg)))
            .fetchSemanticsNodes()
            .let { nodes -> assertEquals(0, nodes.size) }
    }

    @Test
    fun disconnected_reconnectButton_invokesCallback() {
        var reconnectRequested = false
        setOverlays(
            isDisconnected = true,
            disconnectReason = DisconnectReason.IO_ERROR,
            onReconnect = { reconnectRequested = true },
        )

        composeTestRule
            .onNodeWithText(string(R.string.console_menu_reconnect))
            .performClick()

        assertTrue(reconnectRequested)
    }

    companion object {
        private const val HOST_NICKNAME = "testhost"
    }
}
