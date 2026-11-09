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

package org.connectbot.ctap2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class P256PublicKeyRecoveryTest {
    private fun key() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun assertion(key: KeyPair, data: ByteArray): P256PublicKeyRecovery.Assertion {
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(key.private)
            update(data)
            sign()
        }
        return P256PublicKeyRecovery.Assertion(data, signature)
    }

    private fun point(key: KeyPair): ByteArray {
        val public = key.public as ECPublicKey
        fun coordinate(value: BigInteger): ByteArray {
            val bytes = value.toByteArray().takeLast(32).toByteArray()
            return ByteArray(32 - bytes.size) + bytes
        }
        return byteArrayOf(4) + coordinate(public.w.affineX) + coordinate(public.w.affineY)
    }

    @Test
    fun recoversOriginalKeyFromIndependentJcaSignatures() {
        repeat(16) { index ->
            val key = key()
            val first = assertion(key, "first-$index".toByteArray())
            val second = assertion(key, "second-$index".toByteArray())
            assertArrayEquals(point(key), P256PublicKeyRecovery.recover(first, second))
        }
    }

    @Test
    fun supportsHighSSignatures() {
        val key = key()
        val first = assertion(key, byteArrayOf(1))
        val rLength = first.signature[3].toInt()
        val r = first.signature.copyOfRange(4, 4 + rLength)
        val originalS = BigInteger(first.signature.copyOfRange(6 + rLength, first.signature.size))
        val n = (key.public as ECPublicKey).params.order
        val highS = originalS.max(n - originalS)
        val encodedS = highS.toByteArray()
        val signature = byteArrayOf(0x30, (4 + r.size + encodedS.size).toByte(), 2, r.size.toByte()) +
            r + byteArrayOf(2, encodedS.size.toByte()) + encodedS
        assertArrayEquals(
            point(key),
            P256PublicKeyRecovery.recover(first.copy(signature = signature), assertion(key, byteArrayOf(2))),
        )
    }

    @Test
    fun rejectsDifferentKeysAndAlteredData() {
        val key = key()
        val first = assertion(key, byteArrayOf(1))
        val second = assertion(key, byteArrayOf(2))
        assertThrows(IllegalArgumentException::class.java) {
            P256PublicKeyRecovery.recover(first, assertion(key(), byteArrayOf(2)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            P256PublicKeyRecovery.recover(first, second.copy(signedData = byteArrayOf(3)))
        }
    }

    @Test
    fun rejectsMalformedDerSignatures() {
        val first = assertion(key(), byteArrayOf(1))
        val second = assertion(key(), byteArrayOf(2))
        val malformed = listOf(
            byteArrayOf(),
            byteArrayOf(0x30, 0x80.toByte(), 2, 1, 1, 2, 1, 1), // Indefinite length.
            byteArrayOf(0x30, 6, 2, 1, 0x80.toByte(), 2, 1, 1), // Negative integer.
            byteArrayOf(0x30, 7, 2, 2, 0, 1, 2, 1, 1), // Redundant leading zero.
            byteArrayOf(0x30, 6, 2, 0, 2, 2, 1, 1), // Empty integer.
            byteArrayOf(0x30, 6, 2, 1, 1, 2, 2, 1), // Truncated integer.
            byteArrayOf(0x30, 7, 2, 1, 1, 2, 1, 1, 0), // Trailing data.
            byteArrayOf(0x30, 6, 3, 1, 1, 2, 1, 1), // Incorrect tag.
        )
        malformed.forEach { signature ->
            assertThrows(IllegalArgumentException::class.java) {
                P256PublicKeyRecovery.recover(first.copy(signature = signature), second)
            }
        }
    }

    @Test
    fun rejectsReplayAndInvalidScalars() {
        val first = assertion(key(), byteArrayOf(1))
        assertThrows(IllegalArgumentException::class.java) {
            P256PublicKeyRecovery.recover(first, first)
        }
        val zeroR = byteArrayOf(0x30, 6, 2, 1, 0, 2, 1, 1)
        assertThrows(IllegalArgumentException::class.java) {
            P256PublicKeyRecovery.recover(first.copy(signature = zeroR), assertion(key(), byteArrayOf(2)))
        }
    }
}
