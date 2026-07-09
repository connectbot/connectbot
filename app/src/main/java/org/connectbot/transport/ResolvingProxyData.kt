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

package org.connectbot.transport

import com.trilead.ssh2.ProxyData
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * ProxyData implementation that resolves the hostname itself and opens a
 * plain TCP socket to the resolved address.
 *
 * Routing the connection through ProxyData keeps sshlib's view of the
 * hostname unchanged: host keys are still verified and stored against the
 * original name rather than a resolved IP that may change over time.
 *
 * @param via short mechanism name used in error messages (e.g. "mDNS")
 * @param ipVersion the host's IP version preference ([org.connectbot.util.HostConstants] value)
 * @param onResolved optional callback invoked with the resolved address, for status output
 * @param resolve resolver invoked as (hostname, ipVersion, timeoutMillis)
 */
open class ResolvingProxyData(
    private val via: String,
    private val ipVersion: String,
    private val onResolved: ((InetAddress) -> Unit)? = null,
    private val resolve: (String, String, Int) -> InetAddress?,
) : ProxyData {
    @Throws(IOException::class)
    override fun openConnection(hostname: String, port: Int, connectTimeout: Int): Socket {
        val timeout = connectTimeout.coerceAtLeast(0)
        val startedAtNanos = System.nanoTime()
        val address = resolve(hostname, ipVersion, timeout)
            ?: throw UnknownHostException("Unable to resolve \"$hostname\" via $via")
        onResolved?.invoke(address)

        val remainingTimeout = remainingTimeoutMillis(timeout, startedAtNanos)
            ?: throw SocketTimeoutException("Timed out resolving \"$hostname\" via $via")
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(address, port), remainingTimeout)
        } catch (e: IOException) {
            try {
                socket.close()
            } catch (_: IOException) {
            }
            throw e
        }
        return socket
    }

    private companion object {
        private const val NANOS_PER_MILLI = 1_000_000L

        fun remainingTimeoutMillis(timeoutMillis: Int, startedAtNanos: Long): Int? {
            if (timeoutMillis == 0) return 0

            val elapsedNanos = System.nanoTime() - startedAtNanos
            val remainingNanos = timeoutMillis.toLong() * NANOS_PER_MILLI - elapsedNanos
            if (remainingNanos <= 0) return null

            return ((remainingNanos + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
                .coerceAtLeast(1)
        }
    }
}
