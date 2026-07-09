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

package org.connectbot.util

import org.connectbot.util.MdnsResolver.Companion.TYPE_A
import org.connectbot.util.MdnsResolver.Companion.TYPE_AAAA
import timber.log.Timber
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.random.Random

/**
 * Resolves Tailscale MagicDNS (`*.ts.net`) hostnames.
 *
 * The platform resolver handles these when the Tailscale app's MagicDNS is
 * the effective DNS server, and tailnets with HTTPS certificates also publish
 * public `ts.net` records — but Android Private DNS (DoT) commonly bypasses
 * MagicDNS and leaves the name unresolvable. This resolver asks the platform
 * first, then falls back to a direct unicast DNS query to Tailscale's
 * quad-100 resolver (100.100.100.100), which is reachable whenever the
 * Tailscale app's tunnel is up.
 */
class TailscaleResolver {
    /**
     * Resolve [hostname] to an address permitted by [ipVersion] (one of the
     * [HostConstants] IPVERSION values), or null if it could not be resolved.
     * When [timeoutMillis] is positive, the quad-100 fallback stops once that
     * timeout budget is exhausted.
     */
    fun resolve(hostname: String, ipVersion: String, timeoutMillis: Int = 0): InetAddress? {
        val name = MdnsResolver.normalizeHostname(hostname)
        val deadlineNanos = timeoutMillis.takeIf { it > 0 }
            ?.let { System.nanoTime() + it.toLong() * NANOS_PER_MILLI }
        val queryTypes = when (ipVersion) {
            HostConstants.IPVERSION_IPV4_ONLY -> listOf(TYPE_A)
            HostConstants.IPVERSION_IPV6_ONLY -> listOf(TYPE_AAAA)
            else -> listOf(TYPE_A, TYPE_AAAA)
        }

        resolveViaPlatform(name, queryTypes)?.let { return it }

        return queryViaUnicast(name, queryTypes, deadlineNanos)
    }

    private fun resolveViaPlatform(hostname: String, queryTypes: List<Int>): InetAddress? {
        val addresses = try {
            InetAddress.getAllByName(hostname)
        } catch (_: UnknownHostException) {
            return null
        }
        for (type in queryTypes) {
            addresses.firstOrNull { matchesType(it, type) }?.let { return it }
        }
        return null
    }

    private fun queryViaUnicast(hostname: String, queryTypes: List<Int>, deadlineNanos: Long?): InetAddress? {
        for (server in MAGIC_DNS_SERVERS) {
            try {
                DatagramSocket().use { socket ->
                    val serverAddress = InetAddress.getByName(server)
                    for (type in queryTypes) {
                        repeat(QUERY_ATTEMPTS) {
                            val timeout = MdnsResolver.receiveTimeoutMillis(deadlineNanos) ?: return null
                            socket.soTimeout = timeout
                            val transactionId = Random.nextInt(0x10000)
                            val query = MdnsResolver.buildQuery(transactionId, hostname, type, FLAGS_RD)
                            socket.send(DatagramPacket(query, query.size, serverAddress, DNS_PORT))

                            val receiveDeadline = System.nanoTime() + timeout.toLong() * NANOS_PER_MILLI
                            while (System.nanoTime() < receiveDeadline) {
                                val buffer = ByteArray(MAX_PACKET_SIZE)
                                val packet = DatagramPacket(buffer, buffer.size)
                                try {
                                    socket.soTimeout =
                                        MdnsResolver.receiveTimeoutMillis(deadlineNanos, receiveDeadline)
                                            ?: return null
                                    socket.receive(packet)
                                } catch (_: SocketTimeoutException) {
                                    break
                                }
                                val response = buffer.copyOf(packet.length)
                                if (response.size < 2 || readU16(response, 0) != transactionId) {
                                    continue
                                }
                                MdnsResolver.parseAddressRecord(response, hostname, type)?.let { rdata ->
                                    return InetAddress.getByAddress(hostname, rdata)
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                // e.g. the IPv6 server on a v4-only device fails fast; try the next.
                Timber.w(e, "MagicDNS query for %s via %s failed", hostname, server)
            }
        }
        return null
    }

    companion object {
        private const val DNS_PORT = 53
        private const val MAGIC_DNS_V4 = "100.100.100.100"
        private const val MAGIC_DNS_V6 = "fd7a:115c:a1e0::53"
        private val MAGIC_DNS_SERVERS = listOf(MAGIC_DNS_V4, MAGIC_DNS_V6)
        private const val FLAGS_RD = 0x0100
        private const val MAX_PACKET_SIZE = 9000
        private const val QUERY_ATTEMPTS = 2
        private const val NANOS_PER_MILLI = 1_000_000L

        /** First 6 bytes of Tailscale's IPv6 ULA range, fd7a:115c:a1e0::/48. */
        private val TS_ULA_PREFIX = byteArrayOf(
            0xFD.toByte(),
            0x7A,
            0x11,
            0x5C,
            0xA1.toByte(),
            0xE0.toByte(),
        )

        /**
         * Whether [hostname] is a Tailscale MagicDNS FQDN
         * (`<host>.<tailnet>.ts.net`). Bare tailnet names like `foo.ts.net`
         * are not hosts and are left to normal resolution.
         */
        @JvmStatic
        fun isTailscaleHostname(hostname: String): Boolean {
            val name = MdnsResolver.normalizeHostname(hostname)
            if (HostConstants.isIpAddress(name)) return false
            val labels = name.split('.')
            return labels.size >= 4 &&
                labels[labels.size - 1].equals("net", ignoreCase = true) &&
                labels[labels.size - 2].equals("ts", ignoreCase = true) &&
                labels.all { label ->
                    val bytes = label.toByteArray(Charsets.UTF_8)
                    bytes.size in 1..63
                }
        }

        /**
         * Whether [hostname] is a literal IP in a Tailscale address range:
         * 100.64.0.0/10 (CGNAT, also used by other VPN/WireGuard setups) or
         * fd7a:115c:a1e0::/48. Never performs a DNS lookup.
         */
        @JvmStatic
        fun isTailscaleAddress(hostname: String): Boolean {
            val name = hostname.trim().trim('[', ']')
            val literal = when {
                HostConstants.isIpv4Address(name) || HostConstants.isIpv6Address(name) ->
                    try {
                        InetAddress.getByName(name)
                    } catch (_: Exception) {
                        return false
                    }

                else -> return false
            }
            return isTailscaleIp(literal)
        }

        /** Whether [address] is inside a Tailscale address range. */
        @JvmStatic
        fun isTailscaleIp(address: InetAddress): Boolean {
            val bytes = address.address
            return when (address) {
                is Inet4Address -> bytes[0].toInt() == 100 && bytes[1].toInt() and 0xC0 == 0x40

                is Inet6Address ->
                    bytes.size >= TS_ULA_PREFIX.size &&
                        TS_ULA_PREFIX.indices.all { bytes[it] == TS_ULA_PREFIX[it] }

                else -> false
            }
        }

        private fun matchesType(address: InetAddress, type: Int): Boolean = when (type) {
            TYPE_A -> address is Inet4Address
            TYPE_AAAA -> address is Inet6Address
            else -> false
        }

        private fun readU16(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }
}
