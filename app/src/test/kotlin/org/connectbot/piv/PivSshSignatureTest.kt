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

import org.connectbot.sshlib.SshException
import org.connectbot.sshlib.SshSigning
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer

class PivSshSignatureTest {
    @Test
    fun packsPositiveSshMpintsAndAlgorithmName() {
        val der = byteArrayOf(0x30, 7, 2, 2, 0, 0x80.toByte(), 2, 1, 0x7f)
        val signature = ByteBuffer.wrap(SshSigning.encodeEcdsaSignature("ecdsa-sha2-nistp256", der))

        assertEquals("ecdsa-sha2-nistp256", String(readString(signature), Charsets.US_ASCII))
        val scalars = ByteBuffer.wrap(readString(signature))
        assertArrayEquals(byteArrayOf(0, 0x80.toByte()), readString(scalars))
        assertArrayEquals(byteArrayOf(0x7f), readString(scalars))
        assertFalse(scalars.hasRemaining())
        assertFalse(signature.hasRemaining())
    }

    @Test
    fun rejectsNegativeNonCanonicalAndTruncatedScalars() {
        val malformed = listOf(
            byteArrayOf(0x30, 6, 2, 1, 0x80.toByte(), 2, 1, 1),
            byteArrayOf(0x30, 7, 2, 2, 0, 1, 2, 1, 1),
            byteArrayOf(0x30, 6, 2, 1, 1, 2, 2, 1),
            byteArrayOf(0x30, 6, 2, 1, 0, 2, 1, 1),
            byteArrayOf(0x30, 7, 2, 1, 1, 2, 1, 1, 0),
        )
        malformed.forEach { der ->
            assertThrows(SshException::class.java) { SshSigning.encodeEcdsaSignature("ecdsa-sha2-nistp256", der) }
        }
    }

    private fun readString(input: ByteBuffer): ByteArray = ByteArray(input.int).also(input::get)
}
