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
import org.connectbot.util.MdnsResolver
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException

/**
 * ProxyData implementation that resolves `.local` hostnames via mDNS and
 * opens a plain TCP socket to the resolved address.
 *
 * Routing the connection through ProxyData keeps sshlib's view of the
 * hostname unchanged: host keys are still verified and stored against the
 * `.local` name rather than an mDNS-assigned IP that may change with every
 * DHCP lease.
 *
 * https://github.com/connectbot/connectbot/issues/396
 *
 * @param resolver resolver used to look up the `.local` name
 * @param ipVersion the host's IP version preference ([org.connectbot.util.HostConstants] value)
 * @param onResolved optional callback invoked with the resolved address, for status output
 */
class MdnsProxyData(
    private val resolver: MdnsResolver,
    private val ipVersion: String,
    private val onResolved: ((InetAddress) -> Unit)? = null,
) : ProxyData {
    @Throws(IOException::class)
    override fun openConnection(hostname: String, port: Int, connectTimeout: Int): Socket {
        val address = resolver.resolve(hostname, ipVersion)
            ?: throw UnknownHostException("Unable to resolve \"$hostname\" via mDNS")
        onResolved?.invoke(address)

        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(address, port), connectTimeout.coerceAtLeast(0))
        } catch (e: IOException) {
            try {
                socket.close()
            } catch (_: IOException) {
            }
            throw e
        }
        return socket
    }
}
