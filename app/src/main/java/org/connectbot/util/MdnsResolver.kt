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

import android.content.Context
import android.net.wifi.WifiManager
import timber.log.Timber
import java.io.ByteArrayOutputStream
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
 * Resolves `.local` (mDNS / Bonjour / Zeroconf) hostnames.
 *
 * Android's platform resolver handles `.local` lookups natively only on
 * builds whose DNS resolver module supports mDNS (see
 * https://source.android.com/docs/core/ota/modular-system/dns-resolver);
 * elsewhere they fail with [UnknownHostException]. This resolver asks the
 * platform first, then falls back to a direct mDNS "legacy unicast" query
 * (RFC 6762 §6.7): the question is multicast to 224.0.0.251:5353 from an
 * ephemeral port, which obliges responders to answer with a conventional
 * unicast response to that port — no multicast receive path is needed.
 *
 * https://github.com/connectbot/connectbot/issues/396
 */
class MdnsResolver(private val context: Context?) {
    /**
     * Resolve [hostname] to an address permitted by [ipVersion] (one of the
     * [HostConstants] IPVERSION values), or null if it could not be resolved.
     */
    fun resolve(hostname: String, ipVersion: String): InetAddress? {
        val name = normalizeHostname(hostname)
        val queryTypes = when (ipVersion) {
            HostConstants.IPVERSION_IPV4_ONLY -> listOf(TYPE_A)

            HostConstants.IPVERSION_IPV6_ONLY -> listOf(TYPE_AAAA)

            // Prefer IPv4: mDNS-advertised IPv6 addresses are frequently
            // link-local and not connectable without a scope id.
            else -> listOf(TYPE_A, TYPE_AAAA)
        }

        resolveViaPlatform(name, queryTypes)?.let { return it }

        return withMulticastLock { queryViaMulticast(name, queryTypes) }
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

    private fun queryViaMulticast(hostname: String, queryTypes: List<Int>): InetAddress? {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = RECEIVE_TIMEOUT_MS
                val group = InetAddress.getByAddress(MDNS_GROUP_V4)
                for (type in queryTypes) {
                    repeat(QUERY_ATTEMPTS) {
                        val transactionId = Random.nextInt(0x10000)
                        val query = buildQuery(transactionId, hostname, type)
                        socket.send(DatagramPacket(query, query.size, group, MDNS_PORT))

                        val deadline = System.currentTimeMillis() + RECEIVE_TIMEOUT_MS
                        while (System.currentTimeMillis() < deadline) {
                            val buffer = ByteArray(MAX_PACKET_SIZE)
                            val packet = DatagramPacket(buffer, buffer.size)
                            try {
                                socket.receive(packet)
                            } catch (_: SocketTimeoutException) {
                                break
                            }
                            val response = buffer.copyOf(packet.length)
                            if (response.size < 2 || readU16(response, 0) != transactionId) {
                                continue
                            }
                            parseAddressRecord(response, hostname, type)?.let { rdata ->
                                return InetAddress.getByAddress(hostname, rdata)
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "mDNS query for %s failed", hostname)
        }
        return null
    }

    /**
     * Hold a Wi-Fi multicast lock while querying. Unicast replies arrive
     * without it, but some devices filter even the outgoing multicast frames
     * unless a lock is held. Requires CHANGE_WIFI_MULTICAST_STATE.
     */
    private fun <T> withMulticastLock(block: () -> T): T {
        val wifiManager =
            context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = try {
            wifiManager?.createMulticastLock("ConnectBot mDNS")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: SecurityException) {
            Timber.w(e, "Unable to acquire multicast lock for mDNS resolution")
            null
        }
        try {
            return block()
        } finally {
            try {
                lock?.release()
            } catch (_: RuntimeException) {
            }
        }
    }

    companion object {
        private const val MDNS_PORT = 5353
        private val MDNS_GROUP_V4 = byteArrayOf(224.toByte(), 0, 0, 251.toByte())
        private const val MAX_PACKET_SIZE = 9000
        private const val RECEIVE_TIMEOUT_MS = 1000
        private const val QUERY_ATTEMPTS = 2
        private const val MAX_COMPRESSION_JUMPS = 16
        private const val CLASS_IN = 1

        internal const val TYPE_A = 1
        internal const val TYPE_AAAA = 28

        /** Whether [hostname] is a `.local` name that mDNS should resolve. */
        @JvmStatic
        fun isMdnsHostname(hostname: String): Boolean {
            val name = normalizeHostname(hostname)
            if (HostConstants.isIpAddress(name)) return false
            val labels = name.split('.')
            return labels.size >= 2 &&
                labels.last().equals("local", ignoreCase = true) &&
                labels.all { it.isNotEmpty() }
        }

        internal fun normalizeHostname(hostname: String): String = hostname.trim().removeSuffix(".")

        private fun matchesType(address: InetAddress, type: Int): Boolean = when (type) {
            TYPE_A -> address is Inet4Address
            TYPE_AAAA -> address is Inet6Address
            else -> false
        }

        /** Build a single-question DNS query packet for [hostname]. */
        internal fun buildQuery(transactionId: Int, hostname: String, queryType: Int): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(transactionId ushr 8)
            out.write(transactionId and 0xFF)
            out.write(0)
            out.write(0) // flags: standard query
            out.write(0)
            out.write(1) // QDCOUNT = 1
            repeat(6) { out.write(0) } // ANCOUNT, NSCOUNT, ARCOUNT = 0
            for (label in hostname.split('.')) {
                val bytes = label.toByteArray(Charsets.UTF_8)
                require(bytes.size in 1..63) { "Invalid DNS label: $label" }
                out.write(bytes.size)
                out.write(bytes)
            }
            out.write(0)
            out.write(queryType ushr 8)
            out.write(queryType and 0xFF)
            out.write(CLASS_IN ushr 8)
            out.write(CLASS_IN and 0xFF)
            return out.toByteArray()
        }

        /**
         * Extract the address bytes (rdata) of the first answer record in
         * [packet] that matches [hostname] and [queryType], or null if the
         * packet is not a well-formed response containing one.
         */
        internal fun parseAddressRecord(packet: ByteArray, hostname: String, queryType: Int): ByteArray? {
            if (packet.size < 12) return null
            if (readU16(packet, 2) and 0x8000 == 0) return null // not a response
            val questionCount = readU16(packet, 4)
            val answerCount = readU16(packet, 6)

            var offset = 12
            repeat(questionCount) {
                offset = skipName(packet, offset) ?: return null
                offset += 4 // QTYPE + QCLASS
                if (offset > packet.size) return null
            }

            val expectedLength = if (queryType == TYPE_A) 4 else 16
            repeat(answerCount) {
                val (name, afterName) = readName(packet, offset) ?: return null
                if (afterName + 10 > packet.size) return null
                val type = readU16(packet, afterName)
                val rdataLength = readU16(packet, afterName + 8)
                val rdataStart = afterName + 10
                if (rdataStart + rdataLength > packet.size) return null
                if (type == queryType &&
                    rdataLength == expectedLength &&
                    name.equals(hostname, ignoreCase = true)
                ) {
                    return packet.copyOfRange(rdataStart, rdataStart + rdataLength)
                }
                offset = rdataStart + rdataLength
            }
            return null
        }

        private fun readU16(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

        /** Decode a possibly-compressed DNS name; returns name and the offset after its field. */
        private fun readName(packet: ByteArray, startOffset: Int): Pair<String, Int>? {
            val labels = mutableListOf<String>()
            var offset = startOffset
            var afterFirstPointer = -1
            var jumps = 0
            while (true) {
                if (offset >= packet.size) return null
                val length = packet[offset].toInt() and 0xFF
                when {
                    length == 0 -> {
                        val end = if (afterFirstPointer >= 0) afterFirstPointer else offset + 1
                        return labels.joinToString(".") to end
                    }

                    length and 0xC0 == 0xC0 -> {
                        if (offset + 1 >= packet.size || ++jumps > MAX_COMPRESSION_JUMPS) return null
                        if (afterFirstPointer < 0) afterFirstPointer = offset + 2
                        offset = ((length and 0x3F) shl 8) or (packet[offset + 1].toInt() and 0xFF)
                    }

                    length and 0xC0 != 0 -> return null

                    // reserved label type

                    else -> {
                        if (offset + 1 + length > packet.size) return null
                        labels.add(String(packet, offset + 1, length, Charsets.UTF_8))
                        offset += 1 + length
                    }
                }
            }
        }

        private fun skipName(packet: ByteArray, offset: Int): Int? = readName(packet, offset)?.second
    }
}
