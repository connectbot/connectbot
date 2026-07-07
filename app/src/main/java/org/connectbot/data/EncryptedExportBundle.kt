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
import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Thrown when an encrypted bundle cannot be decrypted with the given passphrase.
 * This also covers a tampered/corrupted bundle, since AES-GCM authentication
 * cannot distinguish the two cases.
 */
class WrongPassphraseException : GeneralSecurityException("Wrong passphrase or corrupted bundle")

/**
 * Thrown when a bundle envelope is structurally invalid or uses unsupported parameters.
 */
class InvalidBundleException(message: String) : IllegalArgumentException(message)

/**
 * Passphrase-encrypted envelope for host configuration exports.
 *
 * The envelope is itself a small JSON document so exported files remain
 * self-describing and keep the .json extension:
 *
 * ```json
 * {
 *   "format": "connectbot-encrypted-bundle",
 *   "version": 1,
 *   "kdf": "PBKDF2WithHmacSHA256",
 *   "iterations": 600000,
 *   "salt": "<base64, 16 bytes>",
 *   "iv": "<base64, 12 bytes>",
 *   "ciphertext": "<base64, AES-256-GCM ciphertext + tag>"
 * }
 * ```
 *
 * The payload (the regular export JSON) is encrypted with AES-256-GCM using a
 * key derived from the passphrase via PBKDF2. The format/version identifier is
 * bound into the GCM authenticated data, so a bundle cannot be silently
 * re-purposed under a different format version.
 */
object EncryptedExportBundle {
    const val FORMAT = "connectbot-encrypted-bundle"
    const val VERSION = 1

    private const val KDF_SHA256 = "PBKDF2WithHmacSHA256"
    private const val KDF_SHA1 = "PBKDF2WithHmacSHA1"

    /** PBKDF2 iteration count for new exports (OWASP 2023 recommendation for HMAC-SHA256). */
    private const val ITERATIONS = 600_000

    /** Upper bound accepted on import, to reject hostile bundles that would spin the CPU. */
    private const val MAX_ITERATIONS = 10_000_000

    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val KEY_LENGTH_BITS = 256
    private const val TAG_LENGTH_BITS = 128

    private const val CIPHER = "AES/GCM/NoPadding"

    /**
     * Check whether the given file content looks like an encrypted export bundle.
     * Used by the import flow to decide whether to prompt for a passphrase.
     */
    fun isEncryptedBundle(content: String): Boolean = try {
        JSONObject(content).optString("format") == FORMAT
    } catch (_: JSONException) {
        false
    }

    /**
     * Encrypt the given plaintext (the regular export JSON) with the passphrase.
     *
     * @return The encrypted bundle envelope as a JSON string
     */
    fun encrypt(plaintext: String, passphrase: CharArray): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }

        val kdf = preferredKdf()
        val key = deriveKey(passphrase, salt, kdf, ITERATIONS)

        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val envelope = JSONObject()
        envelope.put("format", FORMAT)
        envelope.put("version", VERSION)
        envelope.put("kdf", kdf)
        envelope.put("iterations", ITERATIONS)
        envelope.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        envelope.put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
        return envelope.toString(2)
    }

    /**
     * Decrypt an encrypted bundle envelope back to the plaintext export JSON.
     *
     * @throws InvalidBundleException if the envelope is malformed or uses unsupported parameters
     * @throws WrongPassphraseException if the passphrase is wrong (or the bundle was tampered with)
     */
    fun decrypt(bundleJson: String, passphrase: CharArray): String {
        val envelope = try {
            JSONObject(bundleJson)
        } catch (e: JSONException) {
            throw InvalidBundleException("Not a valid bundle: ${e.message}")
        }

        if (envelope.optString("format") != FORMAT) {
            throw InvalidBundleException("Not an encrypted export bundle")
        }
        val version = envelope.optInt("version", -1)
        if (version != VERSION) {
            throw InvalidBundleException("Unsupported bundle version: $version")
        }

        val kdf = envelope.optString("kdf")
        if (kdf != KDF_SHA256 && kdf != KDF_SHA1) {
            throw InvalidBundleException("Unsupported key derivation function: $kdf")
        }
        val iterations = envelope.optInt("iterations", -1)
        if (iterations !in 1..MAX_ITERATIONS) {
            throw InvalidBundleException("Invalid iteration count: $iterations")
        }

        val salt = decodeField(envelope, "salt")
        val iv = decodeField(envelope, "iv")
        val ciphertext = decodeField(envelope, "ciphertext")
        if (iv.size != IV_LENGTH) {
            throw InvalidBundleException("Invalid IV length: ${iv.size}")
        }

        val key = deriveKey(passphrase, salt, kdf, iterations)

        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad())
        val plaintext = try {
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw WrongPassphraseException()
        }
        return String(plaintext, Charsets.UTF_8)
    }

    /** Authenticated data binding the ciphertext to this format and version. */
    private fun aad(): ByteArray = "$FORMAT:$VERSION".toByteArray(Charsets.UTF_8)

    /**
     * PBKDF2WithHmacSHA256 is only available from API 26; fall back to
     * PBKDF2WithHmacSHA1 on older devices. The chosen KDF is recorded in the
     * envelope so bundles decrypt correctly across devices.
     */
    private fun preferredKdf(): String = try {
        SecretKeyFactory.getInstance(KDF_SHA256)
        KDF_SHA256
    } catch (_: NoSuchAlgorithmException) {
        KDF_SHA1
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, kdf: String, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH_BITS)
        try {
            val factory = SecretKeyFactory.getInstance(kdf)
            val keyBytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeField(envelope: JSONObject, name: String): ByteArray {
        val value = envelope.optString(name)
        if (value.isEmpty()) {
            throw InvalidBundleException("Missing field: $name")
        }
        return try {
            Base64.decode(value, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            throw InvalidBundleException("Field is not valid base64: $name")
        }
    }
}
