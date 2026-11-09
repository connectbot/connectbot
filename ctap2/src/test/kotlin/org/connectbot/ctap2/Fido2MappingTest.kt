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

import com.yubico.yubikit.fido.ctap.CredentialManagement
import com.yubico.yubikit.fido.ctap.Ctap2Session
import org.connectbot.ctap2.Fido2Algorithm
import org.connectbot.ctap2.Fido2CredentialMapper
import org.connectbot.ctap2.Fido2SignatureMapper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class Fido2MappingTest {
    @Test
    fun mapsCredentialAlgorithmsAndPreservesRpAndUserInformation() {
        val rp = mock(CredentialManagement.RpData::class.java)
        val credential = mock(CredentialManagement.CredentialData::class.java)
        `when`(rp.rp).thenReturn(mapOf("id" to "example.com"))
        `when`(credential.user).thenReturn(mapOf("id" to byteArrayOf(1), "name" to "user"))
        `when`(credential.credentialId).thenReturn(mapOf("id" to byteArrayOf(2)))
        for (algorithm in listOf(-7, -7L, -8, -8L)) {
            val ed = algorithm.toString() == "-8"
            val x = ByteArray(32) { it.toByte() }
            val y = ByteArray(32) { (it + 32).toByte() }
            doReturn(mapOf(3 to algorithm, 1 to if (ed) 1 else 2, -1 to if (ed) 6 else 1, -2 to x, -3 to y)).`when`(credential).getPublicKey()
            val result = Fido2CredentialMapper.fromCredentialData(credential, rp)
            assertEquals("example.com", result.rpId)
            assertEquals("user", result.userName)
            assertArrayEquals(byteArrayOf(2), result.credentialId)
            assertArrayEquals(if (ed) x else byteArrayOf(4) + x + y, result.publicKeyBytes)
            assertEquals(if (algorithm.toString() == "-7") Fido2Algorithm.ES256 else Fido2Algorithm.EDDSA, result.algorithm)
        }
        for (invalid in listOf(
            mapOf(3 to -8, 1 to 2, -1 to 6, -2 to ByteArray(32)),
            mapOf(3 to -8, 1 to 1, -1 to 1, -2 to ByteArray(32)),
            mapOf(3 to -8, 1 to 1, -1 to 6, -2 to ByteArray(31)),
            mapOf(3 to -7, 1 to 2, -1 to 1, -2 to ByteArray(32)),
            mapOf(3 to -7, 1 to 2, -1 to 1, -2 to ByteArray(32), -3 to ByteArray(31)),
            mapOf(3 to -7, 1 to 2, -1 to 2, -2 to ByteArray(32), -3 to ByteArray(32)),
        )) {
            doReturn(invalid).`when`(credential).getPublicKey()
            assertThrows(IllegalArgumentException::class.java) { Fido2CredentialMapper.fromCredentialData(credential, rp) }
        }
        doReturn(mapOf(3 to -8, 1 to 1, -1 to 6, -2 to ByteArray(32))).`when`(credential).getPublicKey()
        `when`(credential.user).thenReturn(emptyMap<String, Any>())
        assertEquals(null, Fido2CredentialMapper.fromCredentialData(credential, rp).userHandle)
        doReturn(mapOf(3 to 0)).`when`(credential).getPublicKey()
        assertThrows(IllegalStateException::class.java) { Fido2CredentialMapper.fromCredentialData(credential, rp) }
        doReturn(mapOf(3 to -4294967303L)).`when`(credential).getPublicKey()
        assertThrows(IllegalStateException::class.java) { Fido2CredentialMapper.fromCredentialData(credential, rp) }
        `when`(credential.credentialId).thenReturn(emptyMap<String, Any>())
        assertThrows(IllegalStateException::class.java) { Fido2CredentialMapper.fromCredentialData(credential, rp) }
    }

    @Test
    fun signatureMappingUsesAuthenticatorDataAndRejectsTruncatedResponses() {
        val assertion = mock(Ctap2Session.AssertionData::class.java)
        `when`(assertion.signature).thenReturn(byteArrayOf(9))
        for (size in listOf(0, 32, 33, 36)) {
            `when`(assertion.authenticatorData).thenReturn(ByteArray(size))
            assertThrows(java.nio.BufferUnderflowException::class.java) { Fido2SignatureMapper.fromAssertion(assertion) }
        }
        val data = ByteArray(37)
        data[32] = 5
        data[33] = 1
        data[34] = -1
        data[35] = -2
        data[36] = -3
        `when`(assertion.authenticatorData).thenReturn(data)
        val result = Fido2SignatureMapper.fromAssertion(assertion)
        assertEquals(1, result.flags.toInt() and 1)
        assertEquals(4, result.flags.toInt() and 4)
        assertEquals(0x01fffefd, result.counter)
        assertArrayEquals(byteArrayOf(9), result.signature)

        data[32] = 0x85.toByte()
        val extensions = com.yubico.yubikit.fido.Cbor.encode(mapOf("example" to true))
        `when`(assertion.authenticatorData).thenReturn(data + extensions)
        val extended = Fido2SignatureMapper.fromAssertion(assertion)
        assertEquals(1, extended.flags.toInt() and 1)
        assertEquals(4, extended.flags.toInt() and 4)
        assertEquals(0x01fffefd, extended.counter)
        `when`(assertion.authenticatorData).thenReturn(data)
        assertThrows(IllegalArgumentException::class.java) { Fido2SignatureMapper.fromAssertion(assertion) }
    }
}
