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
        reconnectAttempts: Int = 0,
        maxReconnectAttempts: Int = 0,
    ): DisconnectAction {
        if (reason == DisconnectReason.USER_REQUESTED) return DisconnectAction.CloseImmediately
        if (quickDisconnect) return DisconnectAction.CloseImmediately
        // Never auto-reconnect on auth failures — looping would lock accounts
        if (reason == DisconnectReason.AUTH_FAIL) return DisconnectAction.ShowReconnectOverlay
        if (stayConnected) {
            return if (maxReconnectAttempts > 0 && reconnectAttempts >= maxReconnectAttempts) {
                DisconnectAction.GiveUpReconnect
            } else {
                DisconnectAction.AutoReconnect
            }
        }
        return DisconnectAction.ShowReconnectOverlay
    }

    /**
     * Whether bringing a disconnected session back into view (tapping the
     * host, returning to its console) should trigger a reconnect on its own.
     * This restores the pre-rewrite behavior where reopening the app revived
     * dropped sessions, even for hosts without stay-connected set.
     * AUTH_FAIL is excluded so a bad credential can't be retried into an
     * account lockout, and USER_REQUESTED respects an explicit disconnect;
     * for those the overlay's Reconnect button remains the explicit path.
     * REMOTE_EOF is excluded because the remote end closed the session
     * cleanly (e.g. the user exited the shell with Ctrl+D) — silently
     * reopening a shell the user just ended would fight their intent.
     * https://github.com/connectbot/connectbot/issues/2214
     */
    fun shouldReconnectOnOpen(
        isDisconnected: Boolean,
        isConnecting: Boolean,
        awaitingClose: Boolean,
        reason: DisconnectReason,
    ): Boolean = isDisconnected &&
        !isConnecting &&
        !awaitingClose &&
        reason != DisconnectReason.AUTH_FAIL &&
        reason != DisconnectReason.USER_REQUESTED &&
        reason != DisconnectReason.REMOTE_EOF

    /**
     * Delay before the next automatic reconnect attempt given how many
     * consecutive attempts have already failed since the last successful
     * connection. The first retry is immediate (a dropped connection is
     * usually recoverable right away); subsequent retries back off
     * exponentially, capped at [RECONNECT_MAX_DELAY_MS], so an unreachable
     * server is not hammered in a tight loop. Connectivity-restored events
     * and the user reopening the app bypass this delay entirely.
     */
    fun reconnectDelayMs(failedAttempts: Int): Long = reconnectDelayMs(
        attempt = failedAttempts,
        intervalSeconds = (RECONNECT_BASE_DELAY_MS / 1000L).toInt(),
        exponentialBackoff = true,
    )

    /**
     * Delay before automatic reconnect attempt number [attempt] (1-based).
     * The first retry is immediate. Later retries either use a fixed interval
     * or exponential backoff capped at [RECONNECT_MAX_DELAY_MS].
     */
    fun reconnectDelayMs(
        attempt: Int,
        intervalSeconds: Int,
        exponentialBackoff: Boolean,
    ): Long {
        if (attempt <= 1) return 0L
        val baseMs = intervalSeconds.coerceAtLeast(0).toLong() * 1000L
        if (!exponentialBackoff || baseMs <= 0L) return baseMs
        val shift = (attempt - 2).coerceAtMost(MAX_BACKOFF_SHIFT)
        return (baseMs shl shift).coerceAtMost(maxOf(baseMs, RECONNECT_MAX_DELAY_MS))
    }

    private const val MAX_BACKOFF_SHIFT = 16
}
