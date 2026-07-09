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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * DNS packet handling for mDNS (.local) hostname resolution.
 * https://github.com/connectbot/connectbot/issues/396
 */
class MdnsResolverTest {
    @Test
    fun isMdnsHostname_recognizesLocalNames() {
        assertThat(MdnsResolver.isMdnsHostname("workstation.local")).isTrue()
        assertThat(MdnsResolver.isMdnsHostname("my-pi.LOCAL")).isTrue()
        assertThat(MdnsResolver.isMdnsHostname("host.sub.local")).isTrue()
        assertThat(MdnsResolver.isMdnsHostname("workstation.local.")).isTrue()
    }

    @Test
    fun isMdnsHostname_rejectsNonLocalNames() {
        assertThat(MdnsResolver.isMdnsHostname("example.com")).isFalse()
        assertThat(MdnsResolver.isMdnsHostname("localhost")).isFalse()
        assertThat(MdnsResolver.isMdnsHostname("local")).isFalse()
        assertThat(MdnsResolver.isMdnsHostname("192.168.1.4")).isFalse()
        assertThat(MdnsResolver.isMdnsHostname("::1")).isFalse()
        assertThat(MdnsResolver.isMdnsHostname("host..local")).isFalse()
        assertThat(MdnsResolver.isMdnsHostname("localnotreally")).isFalse()
    }

    @Test
    fun isMdnsHostname_rejectsInvalidLabelLengths() {
        assertThat(MdnsResolver.isMdnsHostname("${"a".repeat(64)}.local")).isFalse()
        assertThat(MdnsResolver.isMdnsHostname("${"\u00e9".repeat(32)}.local")).isFalse()
    }

    @Test
    fun buildQuery_encodesQuestion() {
        val query = MdnsResolver.buildQuery(0x1234, "pi.local", MdnsResolver.TYPE_A)

        val expected = byteArrayOf(
            0x12, 0x34, // transaction id
            0x00, 0x00, // flags: standard query
            0x00, 0x01, // QDCOUNT
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // ANCOUNT, NSCOUNT, ARCOUNT
            0x02, 'p'.code.toByte(), 'i'.code.toByte(),
            0x05, 'l'.code.toByte(), 'o'.code.toByte(), 'c'.code.toByte(), 'a'.code.toByte(), 'l'.code.toByte(),
            0x00, // root label
            0x00, 0x01, // QTYPE = A
            0x00, 0x01, // QCLASS = IN
        )
        assertThat(query).isEqualTo(expected)
    }

    @Test
    fun parseAddressRecord_extractsARecord() {
        val response = response(
            transactionId = 0x1234,
            question = encodeName("pi.local") + qtypeClass(MdnsResolver.TYPE_A),
            answers = listOf(
                encodeName("pi.local") + record(MdnsResolver.TYPE_A, byteArrayOf(192.toByte(), 168.toByte(), 1, 7)),
            ),
        )

        val rdata = MdnsResolver.parseAddressRecord(response, "pi.local", MdnsResolver.TYPE_A)

        assertThat(rdata).isEqualTo(byteArrayOf(192.toByte(), 168.toByte(), 1, 7))
    }

    @Test
    fun parseAddressRecord_matchesNameCaseInsensitively() {
        val response = response(
            transactionId = 1,
            question = null,
            answers = listOf(
                encodeName("PI.Local") + record(MdnsResolver.TYPE_A, byteArrayOf(10, 0, 0, 1)),
            ),
        )

        val rdata = MdnsResolver.parseAddressRecord(response, "pi.local", MdnsResolver.TYPE_A)

        assertThat(rdata).isEqualTo(byteArrayOf(10, 0, 0, 1))
    }

    @Test
    fun parseAddressRecord_followsCompressionPointer() {
        // Question at offset 12 spells out the name; the answer refers back
        // to it with a compression pointer (0xC00C).
        val question = encodeName("pi.local") + qtypeClass(MdnsResolver.TYPE_A)
        val pointerName = byteArrayOf(0xC0.toByte(), 0x0C)
        val response = response(
            transactionId = 7,
            question = question,
            answers = listOf(pointerName + record(MdnsResolver.TYPE_A, byteArrayOf(10, 1, 2, 3))),
        )

        val rdata = MdnsResolver.parseAddressRecord(response, "pi.local", MdnsResolver.TYPE_A)

        assertThat(rdata).isEqualTo(byteArrayOf(10, 1, 2, 3))
    }

    @Test
    fun parseAddressRecord_skipsWrongTypeAndName() {
        val aaaa = ByteArray(16) { it.toByte() }
        val response = response(
            transactionId = 7,
            question = null,
            answers = listOf(
                // AAAA record when we asked for A
                encodeName("pi.local") + record(MdnsResolver.TYPE_AAAA, aaaa),
                // A record for a different host
                encodeName("other.local") + record(MdnsResolver.TYPE_A, byteArrayOf(10, 0, 0, 2)),
                // the record we want
                encodeName("pi.local") + record(MdnsResolver.TYPE_A, byteArrayOf(10, 0, 0, 3)),
            ),
        )

        val rdata = MdnsResolver.parseAddressRecord(response, "pi.local", MdnsResolver.TYPE_A)

        assertThat(rdata).isEqualTo(byteArrayOf(10, 0, 0, 3))
    }

    @Test
    fun parseAddressRecord_extractsAaaaRecord() {
        val aaaa = ByteArray(16) { (it + 1).toByte() }
        val response = response(
            transactionId = 9,
            question = null,
            answers = listOf(encodeName("pi.local") + record(MdnsResolver.TYPE_AAAA, aaaa)),
        )

        val rdata = MdnsResolver.parseAddressRecord(response, "pi.local", MdnsResolver.TYPE_AAAA)

        assertThat(rdata).isEqualTo(aaaa)
    }

    @Test
    fun parseAddressRecord_rejectsMalformedPackets() {
        // Too short
        assertThat(MdnsResolver.parseAddressRecord(ByteArray(4), "pi.local", MdnsResolver.TYPE_A)).isNull()

        // Not a response (QR bit clear)
        val query = MdnsResolver.buildQuery(1, "pi.local", MdnsResolver.TYPE_A)
        assertThat(MdnsResolver.parseAddressRecord(query, "pi.local", MdnsResolver.TYPE_A)).isNull()

        // Truncated answer
        val truncated = response(
            transactionId = 1,
            question = null,
            answers = listOf(encodeName("pi.local") + record(MdnsResolver.TYPE_A, byteArrayOf(10, 0, 0, 1))),
        ).copyOf(20)
        assertThat(MdnsResolver.parseAddressRecord(truncated, "pi.local", MdnsResolver.TYPE_A)).isNull()

        // Self-referencing compression pointer must not loop forever
        val loop = response(
            transactionId = 1,
            question = null,
            answers = listOf(byteArrayOf(0xC0.toByte(), 12) + record(MdnsResolver.TYPE_A, byteArrayOf(10, 0, 0, 1))),
        )
        assertThat(MdnsResolver.parseAddressRecord(loop, "pi.local", MdnsResolver.TYPE_A)).isNull()
    }

    @Test
    fun normalizeHostname_trimsTrailingDotAndWhitespace() {
        assertThat(MdnsResolver.normalizeHostname(" pi.local. ")).isEqualTo("pi.local")
        assertThat(MdnsResolver.normalizeHostname("pi.local")).isEqualTo("pi.local")
    }

    private fun response(transactionId: Int, question: ByteArray?, answers: List<ByteArray>): ByteArray {
        val header = byteArrayOf(
            (transactionId ushr 8).toByte(), (transactionId and 0xFF).toByte(),
            0x84.toByte(), 0x00, // flags: response, authoritative
            0x00, if (question != null) 0x01 else 0x00, // QDCOUNT
            0x00, answers.size.toByte(), // ANCOUNT
            0x00, 0x00, // NSCOUNT
            0x00, 0x00, // ARCOUNT
        )
        return header + (question ?: ByteArray(0)) + answers.fold(ByteArray(0)) { acc, a -> acc + a }
    }

    private fun encodeName(name: String): ByteArray {
        var out = ByteArray(0)
        for (label in name.split('.')) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            out += byteArrayOf(bytes.size.toByte()) + bytes
        }
        return out + byteArrayOf(0)
    }

    private fun qtypeClass(qtype: Int): ByteArray = byteArrayOf((qtype ushr 8).toByte(), (qtype and 0xFF).toByte(), 0x00, 0x01)

    /** TYPE, CLASS=IN, TTL=120, RDLENGTH, RDATA — everything in a record after the name. */
    private fun record(type: Int, rdata: ByteArray): ByteArray = byteArrayOf(
        (type ushr 8).toByte(), (type and 0xFF).toByte(),
        0x00, 0x01, // class IN
        0x00, 0x00, 0x00, 0x78, // TTL
        (rdata.size ushr 8).toByte(), (rdata.size and 0xFF).toByte(),
    ) + rdata
}
