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

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.IOException

/**
 * Periodically probes an established connection so a silently dead link
 * (e.g. a NAT or firewall dropping an idle TCP connection while the app is
 * in the background) is detected within roughly [intervalMs] + [timeoutMs]
 * instead of whenever the next read happens to fail — possibly hours later.
 * The probes also refresh NAT/firewall idle timers, preventing many of
 * those drops in the first place.
 *
 * A probe that throws [IOException] or does not complete within [timeoutMs]
 * marks the connection dead: [onDead] is invoked once and the loop exits.
 * Cycles where [isEligible] returns false (e.g. during the network-loss
 * grace period) are skipped without probing.
 *
 * Timing relies on coroutine [delay]. While the app holds a foreground
 * service the delays fire reliably; under deep Doze a deferred probe simply
 * means later detection, after which the regular reconnect queue — which
 * already waits for connectivity — takes over.
 */
class KeepaliveMonitor(
    private val intervalMs: Long,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val isEligible: () -> Boolean,
    private val sendKeepalive: suspend () -> Unit,
    private val onDead: () -> Unit,
) {
    init {
        require(intervalMs > 0) { "intervalMs must be positive" }
    }

    suspend fun run() {
        while (true) {
            delay(intervalMs)

            if (!isEligible()) {
                continue
            }

            try {
                withTimeoutOrNull(timeoutMs) { sendKeepalive() } ?: run {
                    Timber.w("Keepalive probe timed out after ${timeoutMs}ms")
                    onDead()
                    return
                }
            } catch (e: IOException) {
                Timber.w(e, "Keepalive probe failed")
                onDead()
                return
            }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
    }
}
