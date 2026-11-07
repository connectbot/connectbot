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

package org.connectbot.fido2

import com.yubico.yubikit.fido.ctap.CredentialManagement
import com.yubico.yubikit.fido.ctap.Ctap2Session
import org.connectbot.fido2.ssh.SkEcdsaPublicKey
import org.connectbot.fido2.ssh.SkEcdsaVerify
import org.connectbot.fido2.ssh.SkEd25519PublicKey
import org.connectbot.fido2.ssh.SkEd25519Verify
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class Fido2MappingTest {
    @Test
    fun mapsCredentialAlgorithmsAndPreservesRpAndUserInformation() {
        val rp = mock(CredentialManagement.RpData::class.java)
        val credential = mock(CredentialManagement.CredentialData::class.java)
        for (name in listOf("ssh:", "ssh:server", "https://example.com", "")) {
            `when`(rp.rp).thenReturn(mapOf("id" to name))
            assertEquals(name.startsWith("ssh:"), Fido2CredentialMapper.isSshRp(rp))
        }
        `when`(rp.rp).thenReturn(emptyMap<String, Any>())
        assertFalse(Fido2CredentialMapper.isSshRp(rp))
        `when`(credential.user).thenReturn(mapOf("id" to byteArrayOf(1), "name" to "user"))
        `when`(credential.credentialId).thenReturn(mapOf("id" to byteArrayOf(2)))
        for (algorithm in listOf(-7, -7L, -8, -8L)) {
            doReturn(mapOf(3 to algorithm, 1 to 1, -1L to -2L, -2 to ByteArray(32), "ignored" to "ignored", 4 to "ignored")).`when`(credential).getPublicKey()
            val result = Fido2CredentialMapper.fromCredentialData(credential, rp)
            assertEquals("ssh:", result.rpId)
            assertEquals("user", result.userName)
            assertArrayEquals(byteArrayOf(2), result.credentialId)
            assertTrue(result.publicKeyCose.isNotEmpty())
            assertEquals(if (algorithm.toString() == "-7") Fido2Algorithm.ES256 else Fido2Algorithm.EDDSA, result.algorithm)
        }
        `when`(credential.user).thenReturn(emptyMap<String, Any>())
        assertEquals(null, Fido2CredentialMapper.fromCredentialData(credential, rp).userHandle)
        doReturn(mapOf(3 to 0)).`when`(credential).getPublicKey()
        assertThrows(IllegalStateException::class.java) { Fido2CredentialMapper.fromCredentialData(credential, rp) }
        `when`(credential.credentialId).thenReturn(emptyMap<String, Any>())
        assertThrows(IllegalStateException::class.java) { Fido2CredentialMapper.fromCredentialData(credential, rp) }
    }

    @Test
    fun signatureMappingHandlesMissingFlagsAndUnsignedCounterBytes() {
        val assertion = mock(Ctap2Session.AssertionData::class.java)
        `when`(assertion.signature).thenReturn(byteArrayOf(9))
        for (size in listOf(0, 32, 33, 36, 37)) {
            val data = ByteArray(size)
            if (size > 32) data[32] = 5
            if (size > 36) {
                data[33] = 1
                data[34] = -1
                data[35] = -2
                data[36] = -3
            }
            `when`(assertion.authenticatorData).thenReturn(data)
            val result = Fido2SignatureMapper.fromAssertion(assertion)
            assertEquals(size > 32, result.userPresenceVerified)
            assertEquals(size > 32, result.userVerified)
            assertEquals(if (size == 37) 0x01fffefd else 0, result.counter)
            assertArrayEquals(byteArrayOf(9), result.signature)
        }
    }

    @Test
    fun securityKeyPublicKeysExposeWireEncodingAndCompareKeyContents() {
        val ed = SkEd25519PublicKey("ssh:", ByteArray(32))
        val ec = SkEcdsaPublicKey("ssh:", ByteArray(65).apply { this[0] = 4 })
        for (key in listOf(ed, ec)) {
            assertEquals("ssh:", key.getApplication())
            assertEquals("OpenSSH", key.format)
            assertTrue(key.getKeyData().isNotEmpty())
            assertTrue(key.getSshKeyType().startsWith("sk-"))
            assertEquals(key, key)
            assertFalse(key.equals(null))
            assertFalse(key.equals("other"))
        }
        assertEquals("Ed25519", ed.algorithm)
        assertEquals("EC", ec.algorithm)
        assertEquals(ed, SkEd25519Verify.decodePublicKey(ed.encoded))
        assertEquals(ec, SkEcdsaVerify.decodePublicKey(ec.encoded))
        assertEquals(ed.hashCode(), ed.copy(ed25519Key = ed.ed25519Key.copyOf()).hashCode())
        assertEquals(ec.hashCode(), ec.copy(ecPoint = ec.ecPoint.copyOf()).hashCode())
        assertNotEquals(ed, ed.copy(application = "ssh:other"))
        assertNotEquals(ed, ed.copy(ed25519Key = ByteArray(32) { 1 }))
        assertNotEquals(ec, ec.copy(application = "ssh:other"))
        assertNotEquals(ec, ec.copy(ecPoint = ByteArray(65) { 1 }))
        assertNotEquals(ec, ec.copy(curve = "other"))
    }
}
