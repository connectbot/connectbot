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

import com.yubico.yubikit.piv.KeyType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.spec.ECGenParameterSpec

class PivAlgorithmTest {
    private fun ec(curve: String) = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec(curve)) }.generateKeyPair()

    private fun rsa(bits: Int) = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()

    @Test
    fun savedKeysMapToTheirAlgorithm() {
        assertEquals(PivAlgorithm.EC_P256, PivAlgorithm.fromPublicKey(ec("secp256r1").public))
        assertEquals(PivAlgorithm.EC_P384, PivAlgorithm.fromPublicKey(ec("secp384r1").public))
        assertEquals(PivAlgorithm.RSA_2048, PivAlgorithm.fromPublicKey(rsa(2048).public))
        assertEquals(PivAlgorithm.RSA_3072, PivAlgorithm.fromPublicKey(rsa(3072).public))
        assertEquals(PivAlgorithm.ED25519, PivAlgorithm.fromPublicKey(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public))
    }

    @Test
    fun unsupportedKeysAreNotMappedButCanBeDescribed() {
        val p521 = ec("secp521r1").public
        val rsa1024 = rsa(1024).public
        assertNull(PivAlgorithm.fromPublicKey(p521))
        assertNull(PivAlgorithm.fromPublicKey(rsa1024))
        assertEquals("EC P-521", PivAlgorithm.describe(p521))
        assertEquals("RSA 1024", PivAlgorithm.describe(rsa1024))
        assertEquals("RSA 1024", PivAlgorithm.describe(KeyType.RSA1024))
        assertEquals("X25519", PivAlgorithm.describe(KeyType.X25519))
        assertEquals("EC P-384", PivAlgorithm.describe(KeyType.ECCP384))
    }

    @Test
    fun everyAlgorithmRoundTripsThroughItsKeyType() {
        for (algorithm in PivAlgorithm.entries) {
            assertEquals(algorithm, PivAlgorithm.fromKeyType(algorithm.keyType))
        }
    }

    @Test
    fun rsaIsOnlyOfferedWithSha2() {
        for (algorithm in listOf(PivAlgorithm.RSA_2048, PivAlgorithm.RSA_3072, PivAlgorithm.RSA_4096)) {
            assertEquals(listOf("rsa-sha2-512", "rsa-sha2-256"), algorithm.sshNames)
        }
    }

    @Test
    fun pkcs1BlockIsWhatAJcaRsaSignatureSigns() {
        val pair = rsa(2048)
        val private = pair.private as RSAPrivateKey
        val message = "message to sign".toByteArray()
        for ((hash, prefix, jca) in listOf(
            Triple("SHA-256", PivSignature.SHA256_DIGEST_INFO, "SHA256withRSA"),
            Triple("SHA-512", PivSignature.SHA512_DIGEST_INFO, "SHA512withRSA"),
        )) {
            val block = PivSignature.pkcs1Block(prefix, MessageDigest.getInstance(hash).digest(message), 256)
            val raw = BigInteger(1, block).modPow(private.privateExponent, private.modulus).toByteArray().takeLast(256).toByteArray()
            val expected = Signature.getInstance(jca).run {
                initSign(private)
                update(message)
                sign()
            }
            assertArrayEquals(jca, expected, if (raw.size < 256) ByteArray(256 - raw.size) + raw else raw)
            assertEquals(0, block[0].toInt())
            assertEquals(1, block[1].toInt())
        }
    }

    @Test
    fun pkcs1BlockRejectsATooShortModulus() {
        assertThrows(IllegalArgumentException::class.java) {
            PivSignature.pkcs1Block(PivSignature.SHA512_DIGEST_INFO, ByteArray(64), 64)
        }
    }

    @Test
    fun sshSignatureBlobIsTwoLengthPrefixedStrings() {
        val blob = PivSignature.sshSignatureBlob("ssh-ed25519", byteArrayOf(1, 2, 3))
        assertArrayEquals(
            byteArrayOf(0, 0, 0, 11) + "ssh-ed25519".toByteArray() + byteArrayOf(0, 0, 0, 3, 1, 2, 3),
            blob,
        )
    }

    @Test
    fun aSignatureFromTheWrongKeyIsRejected() {
        val key = ec("secp256r1")
        val other = ec("secp256r1")
        val data = "payload".toByteArray()
        val forged = Signature.getInstance("SHA256withECDSA").run {
            initSign(other.private)
            update(data)
            sign()
        }
        assertThrows(YubiKeyPiv.WrongKey::class.java) {
            PivSignature.sign(PivAlgorithm.EC_P256, "ecdsa-sha2-nistp256", key.public, data) { forged }
        }
        assertTrue(PivAlgorithm.EC_P256.sshNames.contains("ecdsa-sha2-nistp256"))
    }

    @Test
    fun signingWithANameTheAlgorithmCannotUseFails() {
        val key = ec("secp256r1")
        assertThrows(IllegalArgumentException::class.java) {
            PivSignature.sign(PivAlgorithm.EC_P256, "ssh-rsa", key.public, byteArrayOf()) { byteArrayOf() }
        }
    }
}
