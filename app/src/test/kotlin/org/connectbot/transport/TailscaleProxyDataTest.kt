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

import org.assertj.core.api.Assertions.assertThat
import org.connectbot.util.HostConstants
import org.connectbot.util.TailscaleResolver
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class TailscaleProxyDataTest {
    @Test
    fun openConnection_passesConnectTimeoutToResolver() {
        val resolver = mock(TailscaleResolver::class.java)
        val loopback = InetAddress.getLoopbackAddress()
        val server = ServerSocket(0, 1, loopback)
        val acceptThread = thread {
            try {
                server.accept().use { }
            } catch (_: Exception) {
            }
        }

        try {
            `when`(resolver.resolve("pi.tailnet.ts.net", HostConstants.IPVERSION_IPV4_ONLY, CONNECT_TIMEOUT_MS))
                .thenReturn(loopback)
            val proxyData = TailscaleProxyData(resolver, HostConstants.IPVERSION_IPV4_ONLY)

            proxyData.openConnection("pi.tailnet.ts.net", server.localPort, CONNECT_TIMEOUT_MS).use { socket ->
                assertThat(socket.isConnected).isTrue()
            }

            verify(resolver).resolve("pi.tailnet.ts.net", HostConstants.IPVERSION_IPV4_ONLY, CONNECT_TIMEOUT_MS)
        } finally {
            server.close()
            acceptThread.join(1000)
        }
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 250
    }
}
