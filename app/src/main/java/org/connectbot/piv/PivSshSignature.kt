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

package org.connectbot.piv

import java.nio.ByteBuffer

internal object PivSshSignature {
    const val ALGORITHM = "ecdsa-sha2-nistp256"

    /** Convert a P-256 DER ECDSA signature into the SSH signature field (RFC 5656). */
    fun encode(der: ByteArray): ByteArray {
        require(der.size in 8..72 && der[0] == 0x30.toByte() && der[1].toInt() == der.size - 2)
        val input = ByteBuffer.wrap(der, 2, der.size - 2)
        val r = readInteger(input)
        val s = readInteger(input)
        require(!input.hasRemaining())
        val algorithm = ALGORITHM.toByteArray(Charsets.US_ASCII)
        val innerLength = 2 * Int.SIZE_BYTES + r.size + s.size
        return ByteBuffer.allocate(2 * Int.SIZE_BYTES + algorithm.size + innerLength)
            .putInt(algorithm.size).put(algorithm)
            .putInt(innerLength)
            .putInt(r.size).put(r)
            .putInt(s.size).put(s)
            .array()
    }

    private fun readInteger(input: ByteBuffer): ByteArray {
        require(input.remaining() >= 2 && input.get() == 0x02.toByte())
        val length = input.get().toInt()
        require(length in 1..33 && length <= input.remaining())
        val integer = ByteArray(length)
        input.get(integer)

        // Positive DER integers already use SSH's canonical signed mpint representation.
        val first = integer[0].toInt()
        require(first >= 0)
        require(first != 0 || (length > 1 && integer[1] < 0))
        require(length <= 32 || first == 0)
        return integer
    }
}
