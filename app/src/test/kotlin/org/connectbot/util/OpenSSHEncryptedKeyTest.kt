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

import com.trilead.ssh2.crypto.PEMDecoder
import com.trilead.ssh2.crypto.PublicKeyUtils
import com.trilead.ssh2.signature.Ed25519Verify
import com.trilead.ssh2.signature.RSASHA256Verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.connectbot.data.entity.Pubkey
import org.junit.Test
import java.io.IOException
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey

/**
 * Regression tests for unlocking private keys encrypted by OpenSSH 7.6 and
 * later (upstream issue connectbot/connectbot#742).
 *
 * OpenSSH 7.6 switched new-style private key encryption from AES-256-CBC to
 * AES-256-CTR inside the "openssh-key-v1" container (bcrypt KDF). Old sshlib
 * releases rejected these keys with "unknown cipher aes256-ctr", surfacing to
 * the user as "Bad password for key". Both fixtures below were generated with
 * a modern ssh-keygen and are encrypted with aes256-ctr; the passphrase is
 * "testpass".
 */
class OpenSSHEncryptedKeyTest {

    companion object {
        private const val PASSPHRASE = "testpass"

        /** ssh-keygen -t rsa -b 2048 -N testpass — openssh-key-v1, aes256-ctr, bcrypt */
        private val ENCRYPTED_RSA_KEY = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            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
            8lc/OlFohjvAd/TsveqGNISZeZfMU=
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()

        /** ssh-keygen -t ed25519 -N testpass — openssh-key-v1, aes256-ctr, bcrypt */
        private val ENCRYPTED_ED25519_KEY = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAAGAAAABBOJWzPzh
            Y3ZOJgSlS3X/NyAAAAGAAAAAEAAAAzAAAAC3NzaC1lZDI1NTE5AAAAIG8bs3lBPqJOXIca
            eAwG3M3dbceihppIjgJwSEaVc/ksAAAAkEkxYckMA/Osfn3RU1jOy7aN3v47V/Ow7Ragpz
            nwlYiYXzRuMcUVznhYnhGrxJdcInF9vgImMFu3JL4JH0Z/IIyMASIIPyWYm1JNQ+NNUMVp
            BZ3KAKdJqN9Ap8E3GMMciv59btyn0XwyhYRWicGrnYdD2aKNesSEXtJNN8DLe8kB6n7Gq6
            Cnz0X9QjY+GXCiIg==
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()
    }

    private fun importedPubkey(pem: String) = Pubkey(
        id = 0,
        nickname = "imported",
        type = "IMPORTED",
        encrypted = true,
        startup = false,
        confirmation = false,
        createdDate = 0L,
        privateKey = pem.toByteArray(Charsets.UTF_8),
        publicKey = ByteArray(0),
    )

    @Test
    fun parsePEM_recognizesOpenSSHFormatAsEncrypted() {
        for (pem in listOf(ENCRYPTED_RSA_KEY, ENCRYPTED_ED25519_KEY)) {
            val struct = PEMDecoder.parsePEM(pem.toCharArray())
            assertThat(PEMDecoder.isPEMEncrypted(struct)).isTrue()
        }
    }

    @Test
    fun decode_rsaKeyEncryptedWithAes256Ctr_producesWorkingKeyPair() {
        val pair = PEMDecoder.decode(ENCRYPTED_RSA_KEY.toCharArray(), PASSPHRASE)

        val publicKey = pair.public as RSAPublicKey
        assertThat(publicKey.modulus.bitLength()).isEqualTo(2048)

        // The decoded private key must actually be usable for SSH signing.
        val challenge = "issue 742 challenge".toByteArray()
        val signature = RSASHA256Verify.get().generateSignature(challenge, pair.private, SecureRandom())
        assertThat(RSASHA256Verify.get().verifySignature(challenge, signature, pair.public)).isTrue()
    }

    @Test
    fun decode_ed25519KeyEncryptedWithAes256Ctr_producesWorkingKeyPair() {
        val pair = PEMDecoder.decode(ENCRYPTED_ED25519_KEY.toCharArray(), PASSPHRASE)

        assertThat(PublicKeyUtils.isEd25519Key(pair.public)).isTrue()

        val challenge = "issue 742 challenge".toByteArray()
        val signature = Ed25519Verify.get().generateSignature(challenge, pair.private, SecureRandom())
        assertThat(Ed25519Verify.get().verifySignature(challenge, signature, pair.public)).isTrue()
    }

    @Test
    fun decode_withWrongPassphrase_throwsInsteadOfReturningGarbage() {
        assertThatThrownBy {
            PEMDecoder.decode(ENCRYPTED_RSA_KEY.toCharArray(), "wrongpass")
        }.isInstanceOf(IOException::class.java)
    }

    @Test
    fun convertToKeyPair_importedOpenSSHKey_unlocksWithPassphrase() {
        val pair = PubkeyUtils.convertToKeyPair(importedPubkey(ENCRYPTED_RSA_KEY), PASSPHRASE)

        assertThat(pair).isNotNull()
        assertThat((pair!!.public as RSAPublicKey).modulus.bitLength()).isEqualTo(2048)
    }

    @Test
    fun convertToKeyPair_importedOpenSSHKeyWithWrongPassphrase_throwsBadPassword() {
        assertThatThrownBy {
            PubkeyUtils.convertToKeyPair(importedPubkey(ENCRYPTED_ED25519_KEY), "wrongpass")
        }.isInstanceOf(PubkeyUtils.BadPasswordException::class.java)
    }
}
