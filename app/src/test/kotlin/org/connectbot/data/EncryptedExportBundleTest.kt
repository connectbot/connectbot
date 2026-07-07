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

package org.connectbot.data

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedExportBundleTest {

    private val plaintext = """{"version":1,"hosts":[{"nickname":"prod","hostname":"example.com"}]}"""
    private val passphrase = "correct horse battery staple".toCharArray()

    @Test
    fun roundTrip_recoversPlaintext() {
        val bundle = EncryptedExportBundle.encrypt(plaintext, passphrase)
        val decrypted = EncryptedExportBundle.decrypt(bundle, passphrase)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun encrypt_producesSelfDescribingEnvelope() {
        val bundle = EncryptedExportBundle.encrypt(plaintext, passphrase)
        val envelope = JSONObject(bundle)

        assertThat(envelope.getString("format")).isEqualTo(EncryptedExportBundle.FORMAT)
        assertThat(envelope.getInt("version")).isEqualTo(EncryptedExportBundle.VERSION)
        assertThat(envelope.getString("kdf")).startsWith("PBKDF2WithHmac")
        assertThat(envelope.getInt("iterations")).isGreaterThan(0)
        assertThat(Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)).hasSize(16)
        assertThat(Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)).hasSize(12)
        // Ciphertext must not leak the plaintext
        assertThat(bundle).doesNotContain("example.com")
    }

    @Test
    fun encrypt_usesFreshSaltAndIvEachTime() {
        val first = JSONObject(EncryptedExportBundle.encrypt(plaintext, passphrase))
        val second = JSONObject(EncryptedExportBundle.encrypt(plaintext, passphrase))

        assertThat(first.getString("salt")).isNotEqualTo(second.getString("salt"))
        assertThat(first.getString("iv")).isNotEqualTo(second.getString("iv"))
        assertThat(first.getString("ciphertext")).isNotEqualTo(second.getString("ciphertext"))
    }

    @Test
    fun decrypt_wrongPassphrase_throws() {
        val bundle = EncryptedExportBundle.encrypt(plaintext, passphrase)

        assertThatThrownBy {
            EncryptedExportBundle.decrypt(bundle, "wrong passphrase".toCharArray())
        }.isInstanceOf(WrongPassphraseException::class.java)
    }

    @Test
    fun decrypt_tamperedCiphertext_throws() {
        val envelope = JSONObject(EncryptedExportBundle.encrypt(plaintext, passphrase))
        val ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP)
        ciphertext[0] = (ciphertext[0].toInt() xor 0x01).toByte()
        envelope.put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))

        assertThatThrownBy {
            EncryptedExportBundle.decrypt(envelope.toString(), passphrase)
        }.isInstanceOf(WrongPassphraseException::class.java)
    }

    @Test
    fun decrypt_unsupportedVersion_throws() {
        val envelope = JSONObject(EncryptedExportBundle.encrypt(plaintext, passphrase))
        envelope.put("version", 99)

        assertThatThrownBy {
            EncryptedExportBundle.decrypt(envelope.toString(), passphrase)
        }.isInstanceOf(InvalidBundleException::class.java)
            .hasMessageContaining("version")
    }

    @Test
    fun decrypt_unsupportedKdf_throws() {
        val envelope = JSONObject(EncryptedExportBundle.encrypt(plaintext, passphrase))
        envelope.put("kdf", "MD5")

        assertThatThrownBy {
            EncryptedExportBundle.decrypt(envelope.toString(), passphrase)
        }.isInstanceOf(InvalidBundleException::class.java)
            .hasMessageContaining("key derivation")
    }

    @Test
    fun decrypt_excessiveIterations_throws() {
        val envelope = JSONObject(EncryptedExportBundle.encrypt(plaintext, passphrase))
        envelope.put("iterations", Int.MAX_VALUE)

        assertThatThrownBy {
            EncryptedExportBundle.decrypt(envelope.toString(), passphrase)
        }.isInstanceOf(InvalidBundleException::class.java)
            .hasMessageContaining("iteration")
    }

    @Test
    fun decrypt_missingField_throws() {
        val envelope = JSONObject(EncryptedExportBundle.encrypt(plaintext, passphrase))
        envelope.remove("salt")

        assertThatThrownBy {
            EncryptedExportBundle.decrypt(envelope.toString(), passphrase)
        }.isInstanceOf(InvalidBundleException::class.java)
            .hasMessageContaining("salt")
    }

    @Test
    fun decrypt_notJson_throws() {
        assertThatThrownBy {
            EncryptedExportBundle.decrypt("definitely not json", passphrase)
        }.isInstanceOf(InvalidBundleException::class.java)
    }

    @Test
    fun decrypt_plainExportJson_throws() {
        assertThatThrownBy {
            EncryptedExportBundle.decrypt(plaintext, passphrase)
        }.isInstanceOf(InvalidBundleException::class.java)
    }

    @Test
    fun isEncryptedBundle_detectsBundle() {
        val bundle = EncryptedExportBundle.encrypt(plaintext, passphrase)

        assertThat(EncryptedExportBundle.isEncryptedBundle(bundle)).isTrue()
    }

    @Test
    fun isEncryptedBundle_rejectsPlainExportAndGarbage() {
        assertThat(EncryptedExportBundle.isEncryptedBundle(plaintext)).isFalse()
        assertThat(EncryptedExportBundle.isEncryptedBundle("not json at all")).isFalse()
        assertThat(EncryptedExportBundle.isEncryptedBundle("")).isFalse()
    }

    @Test
    fun roundTrip_unicodePayloadAndPassphrase() {
        val unicode = """{"hosts":[{"nickname":"日本語ホスト ✨"}]}"""
        val pass = "pässwörd-日本語-🔑".toCharArray()

        val bundle = EncryptedExportBundle.encrypt(unicode, pass)

        assertThat(EncryptedExportBundle.decrypt(bundle, pass)).isEqualTo(unicode)
    }
}
