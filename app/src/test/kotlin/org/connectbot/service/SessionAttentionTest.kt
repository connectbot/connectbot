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

class SessionAttentionTest {
    @Test
    fun openingClearsUnreadButLeavingClearsEndedNotice() {
        val attention = SessionAttention()
        attention.onOutput()
        attention.onEnded(DisconnectReason.NETWORK_LOST)
        assertTrue(attention.snapshot().unreadOutput)

        attention.onOpened()
        assertFalse(attention.snapshot().unreadOutput)
        assertFalse(attention.snapshot().acknowledged)

        attention.onHidden()
        attention.onConsoleClosed()
        assertTrue(attention.snapshot().acknowledged)
    }

    @Test
    fun backgroundingConsoleDoesNotAcknowledgeDisconnect() {
        val attention = SessionAttention()
        attention.onOpened()
        attention.onHidden()
        attention.onOutput()
        attention.onEnded(DisconnectReason.REMOTE_EOF)
        attention.onConsoleClosed()
        assertTrue(attention.snapshot().unreadOutput)
        assertFalse(attention.snapshot().acknowledged)
    }

    @Test
    fun pausingAfterSeeingDisconnectDoesNotClearEndedNotice() {
        val attention = SessionAttention()
        attention.onOpened()
        attention.onEnded(DisconnectReason.IO_ERROR)
        attention.onHidden()
        assertFalse(attention.snapshot().acknowledged)
        attention.onConsoleClosed()
        assertTrue(attention.snapshot().acknowledged)
    }

    @Test
    fun outputInVisibleConsoleIsAlreadyRead() {
        val attention = SessionAttention()
        attention.onOpened()
        attention.onOutput()
        attention.onEnded(DisconnectReason.REMOTE_EOF)
        assertFalse(attention.snapshot().unreadOutput)
        attention.onConsoleClosed()
        assertTrue(attention.snapshot().acknowledged)
    }

    @Test
    fun closingFailedConsolePreservesOriginalReason() {
        val attention = SessionAttention()
        attention.onEnded(DisconnectReason.AUTH_FAIL)
        attention.onOpened()
        attention.onEnded(DisconnectReason.USER_REQUESTED)
        attention.onConsoleClosed()
        assertEquals(DisconnectReason.AUTH_FAIL, attention.snapshot().reason)
    }

    @Test
    fun reconnectClearsPreviousSessionAttention() {
        val attention = SessionAttention()
        attention.onOutput()
        attention.onEnded(DisconnectReason.AUTH_FAIL)
        attention.onReconnected()
        assertEquals(SessionAttentionState(), attention.snapshot())
        attention.onEnded(DisconnectReason.NETWORK_LOST)
        assertEquals(DisconnectReason.NETWORK_LOST, attention.snapshot().reason)
    }
}
