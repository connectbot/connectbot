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

package org.connectbot.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisconnectPolicyTest {

    private fun decide(
        reason: DisconnectReason,
        quickDisconnect: Boolean = false,
        stayConnected: Boolean = false,
    ) = DisconnectPolicy.decide(reason, quickDisconnect, stayConnected)

    // USER_REQUESTED always closes immediately regardless of flags

    @Test
    fun userRequested_defaultFlags_closesImmediately() {
        assertTrue(decide(DisconnectReason.USER_REQUESTED) is DisconnectAction.CloseImmediately)
    }

    @Test
    fun userRequested_quickDisconnect_closesImmediately() {
        assertTrue(decide(DisconnectReason.USER_REQUESTED, quickDisconnect = true) is DisconnectAction.CloseImmediately)
    }

    @Test
    fun userRequested_stayConnected_closesImmediately() {
        assertTrue(decide(DisconnectReason.USER_REQUESTED, stayConnected = true) is DisconnectAction.CloseImmediately)
    }

    @Test
    fun userRequested_bothFlags_closesImmediately() {
        assertTrue(decide(DisconnectReason.USER_REQUESTED, quickDisconnect = true, stayConnected = true) is DisconnectAction.CloseImmediately)
    }

    // quickDisconnect=true closes immediately for all non-user reasons

    @Test
    fun remoteEof_quickDisconnect_closesImmediately() {
        assertTrue(decide(DisconnectReason.REMOTE_EOF, quickDisconnect = true) is DisconnectAction.CloseImmediately)
    }

    @Test
    fun ioError_quickDisconnect_closesImmediately() {
        assertTrue(decide(DisconnectReason.IO_ERROR, quickDisconnect = true) is DisconnectAction.CloseImmediately)
    }

    @Test
    fun networkLost_quickDisconnect_closesImmediately() {
        assertTrue(decide(DisconnectReason.NETWORK_LOST, quickDisconnect = true) is DisconnectAction.CloseImmediately)
    }

    @Test
    fun hostUnresolved_quickDisconnect_closesImmediately() {
        assertTrue(decide(DisconnectReason.HOST_UNRESOLVED, quickDisconnect = true) is DisconnectAction.CloseImmediately)
    }

    @Test
    fun authFail_quickDisconnect_closesImmediately() {
        assertTrue(decide(DisconnectReason.AUTH_FAIL, quickDisconnect = true) is DisconnectAction.CloseImmediately)
    }

    @Test
    fun unknown_quickDisconnect_closesImmediately() {
        assertTrue(decide(DisconnectReason.UNKNOWN, quickDisconnect = true) is DisconnectAction.CloseImmediately)
    }

    // quickDisconnect wins even when stayConnected is also true

    @Test
    fun remoteEof_bothFlags_closesImmediately() {
        assertTrue(decide(DisconnectReason.REMOTE_EOF, quickDisconnect = true, stayConnected = true) is DisconnectAction.CloseImmediately)
    }

    @Test
    fun ioError_bothFlags_closesImmediately() {
        assertTrue(decide(DisconnectReason.IO_ERROR, quickDisconnect = true, stayConnected = true) is DisconnectAction.CloseImmediately)
    }

    @Test
    fun networkLost_bothFlags_closesImmediately() {
        assertTrue(decide(DisconnectReason.NETWORK_LOST, quickDisconnect = true, stayConnected = true) is DisconnectAction.CloseImmediately)
    }

    @Test
    fun unknown_bothFlags_closesImmediately() {
        assertTrue(decide(DisconnectReason.UNKNOWN, quickDisconnect = true, stayConnected = true) is DisconnectAction.CloseImmediately)
    }

    // Default flags (both false) — show overlay so user can read terminal output

    @Test
    fun remoteEof_defaultFlags_showsReconnectOverlay() {
        // Key regression test: REMOTE_EOF must NOT close immediately by default
        assertTrue(decide(DisconnectReason.REMOTE_EOF) is DisconnectAction.ShowReconnectOverlay)
    }

    @Test
    fun ioError_defaultFlags_showsReconnectOverlay() {
        assertTrue(decide(DisconnectReason.IO_ERROR) is DisconnectAction.ShowReconnectOverlay)
    }

    @Test
    fun networkLost_defaultFlags_showsReconnectOverlay() {
        assertTrue(decide(DisconnectReason.NETWORK_LOST) is DisconnectAction.ShowReconnectOverlay)
    }

    @Test
    fun hostUnresolved_defaultFlags_showsReconnectOverlay() {
        assertTrue(decide(DisconnectReason.HOST_UNRESOLVED) is DisconnectAction.ShowReconnectOverlay)
    }

    @Test
    fun authFail_defaultFlags_showsReconnectOverlay() {
        assertTrue(decide(DisconnectReason.AUTH_FAIL) is DisconnectAction.ShowReconnectOverlay)
    }

    @Test
    fun unknown_defaultFlags_showsReconnectOverlay() {
        assertTrue(decide(DisconnectReason.UNKNOWN) is DisconnectAction.ShowReconnectOverlay)
    }

    // stayConnected=true auto-reconnects (except AUTH_FAIL)

    @Test
    fun remoteEof_stayConnected_autoReconnects() {
        assertTrue(decide(DisconnectReason.REMOTE_EOF, stayConnected = true) is DisconnectAction.AutoReconnect)
    }

    @Test
    fun ioError_stayConnected_autoReconnects() {
        assertTrue(decide(DisconnectReason.IO_ERROR, stayConnected = true) is DisconnectAction.AutoReconnect)
    }

    @Test
    fun networkLost_stayConnected_autoReconnects() {
        assertTrue(decide(DisconnectReason.NETWORK_LOST, stayConnected = true) is DisconnectAction.AutoReconnect)
    }

    @Test
    fun hostUnresolved_stayConnected_autoReconnects() {
        // A DNS failure (EAI_NODATA) after a network switch must keep the
        // stay-connected retry loop going rather than giving up.
        // https://github.com/connectbot/connectbot/issues/2297
        assertTrue(decide(DisconnectReason.HOST_UNRESOLVED, stayConnected = true) is DisconnectAction.AutoReconnect)
    }

    @Test
    fun unknown_stayConnected_autoReconnects() {
        assertTrue(decide(DisconnectReason.UNKNOWN, stayConnected = true) is DisconnectAction.AutoReconnect)
    }

    // AUTH_FAIL never auto-reconnects even with stayConnected — would lock accounts

    @Test
    fun authFail_stayConnected_showsReconnectOverlay() {
        assertTrue(decide(DisconnectReason.AUTH_FAIL, stayConnected = true) is DisconnectAction.ShowReconnectOverlay)
    }

    @Test
    fun authFail_bothFlags_closesImmediately() {
        // quickDisconnect wins over AUTH_FAIL special case
        assertTrue(decide(DisconnectReason.AUTH_FAIL, quickDisconnect = true, stayConnected = true) is DisconnectAction.CloseImmediately)
    }

    // Reconnect backoff schedule

    @Test
    fun reconnectDelay_firstAttempt_isImmediate() {
        assertEquals(0L, DisconnectPolicy.reconnectDelayMs(0))
        assertEquals(0L, DisconnectPolicy.reconnectDelayMs(1))
    }

    @Test
    fun reconnectDelay_backsOffExponentially() {
        assertEquals(2_000L, DisconnectPolicy.reconnectDelayMs(2))
        assertEquals(4_000L, DisconnectPolicy.reconnectDelayMs(3))
        assertEquals(8_000L, DisconnectPolicy.reconnectDelayMs(4))
        assertEquals(16_000L, DisconnectPolicy.reconnectDelayMs(5))
        assertEquals(32_000L, DisconnectPolicy.reconnectDelayMs(6))
    }

    @Test
    fun reconnectDelay_isCapped() {
        assertEquals(DisconnectPolicy.RECONNECT_MAX_DELAY_MS, DisconnectPolicy.reconnectDelayMs(7))
        assertEquals(DisconnectPolicy.RECONNECT_MAX_DELAY_MS, DisconnectPolicy.reconnectDelayMs(100))
    }

    @Test
    fun reconnectDelay_doesNotOverflowForHugeAttemptCounts() {
        assertEquals(DisconnectPolicy.RECONNECT_MAX_DELAY_MS, DisconnectPolicy.reconnectDelayMs(Int.MAX_VALUE))
    }

    @Test
    fun reconnectDelay_negativeAttemptsAreImmediate() {
        assertEquals(0L, DisconnectPolicy.reconnectDelayMs(-1))
    }

    // Reconnect-on-open: bringing a dropped session back into view retries it

    private fun shouldReconnectOnOpen(
        reason: DisconnectReason,
        isDisconnected: Boolean = true,
        isConnecting: Boolean = false,
        awaitingClose: Boolean = false,
    ) = DisconnectPolicy.shouldReconnectOnOpen(isDisconnected, isConnecting, awaitingClose, reason)

    @Test
    fun reconnectOnOpen_remoteEof_reconnects() {
        assertTrue(shouldReconnectOnOpen(DisconnectReason.REMOTE_EOF))
    }

    @Test
    fun reconnectOnOpen_ioError_reconnects() {
        assertTrue(shouldReconnectOnOpen(DisconnectReason.IO_ERROR))
    }

    @Test
    fun reconnectOnOpen_hostUnresolved_reconnects() {
        assertTrue(shouldReconnectOnOpen(DisconnectReason.HOST_UNRESOLVED))
    }

    @Test
    fun reconnectOnOpen_networkLost_reconnects() {
        assertTrue(shouldReconnectOnOpen(DisconnectReason.NETWORK_LOST))
    }

    @Test
    fun reconnectOnOpen_unknown_reconnects() {
        assertTrue(shouldReconnectOnOpen(DisconnectReason.UNKNOWN))
    }

    @Test
    fun reconnectOnOpen_authFail_doesNotReconnect() {
        // Retrying bad credentials automatically could lock the account
        assertFalse(shouldReconnectOnOpen(DisconnectReason.AUTH_FAIL))
    }

    @Test
    fun reconnectOnOpen_userRequested_doesNotReconnect() {
        // An explicit disconnect must stay disconnected
        assertFalse(shouldReconnectOnOpen(DisconnectReason.USER_REQUESTED))
    }

    @Test
    fun reconnectOnOpen_stillConnected_doesNotReconnect() {
        assertFalse(shouldReconnectOnOpen(DisconnectReason.IO_ERROR, isDisconnected = false))
    }

    @Test
    fun reconnectOnOpen_alreadyConnecting_doesNotReconnect() {
        assertFalse(shouldReconnectOnOpen(DisconnectReason.IO_ERROR, isConnecting = true))
    }

    @Test
    fun reconnectOnOpen_awaitingClose_doesNotReconnect() {
        assertFalse(shouldReconnectOnOpen(DisconnectReason.IO_ERROR, awaitingClose = true))
    }
}
