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

object DisconnectPolicy {
    const val RECONNECT_BASE_DELAY_MS = 2_000L
    const val RECONNECT_MAX_DELAY_MS = 60_000L

    fun decide(
        reason: DisconnectReason,
        quickDisconnect: Boolean,
        stayConnected: Boolean,
    ): DisconnectAction {
        if (reason == DisconnectReason.USER_REQUESTED) return DisconnectAction.CloseImmediately
        if (quickDisconnect) return DisconnectAction.CloseImmediately
        // Never auto-reconnect on auth failures — looping would lock accounts
        if (reason == DisconnectReason.AUTH_FAIL) return DisconnectAction.ShowReconnectOverlay
        if (stayConnected) return DisconnectAction.AutoReconnect
        return DisconnectAction.ShowReconnectOverlay
    }

    /**
     * Delay before the next automatic reconnect attempt given how many
     * consecutive attempts have already failed since the last successful
     * connection. The first retry is immediate (a dropped connection is
     * usually recoverable right away); subsequent retries back off
     * exponentially, capped at [RECONNECT_MAX_DELAY_MS], so an unreachable
     * server is not hammered in a tight loop. Connectivity-restored events
     * and the user reopening the app bypass this delay entirely.
     */
    fun reconnectDelayMs(failedAttempts: Int): Long {
        if (failedAttempts <= 1) return 0L
        val shift = (failedAttempts - 2).coerceAtMost(MAX_BACKOFF_SHIFT)
        return (RECONNECT_BASE_DELAY_MS shl shift).coerceAtMost(RECONNECT_MAX_DELAY_MS)
    }

    private const val MAX_BACKOFF_SHIFT = 16
}
