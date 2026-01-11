/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for host login passwords using Android Keystore.
 *
 * Passwords are stored encrypted and keyed by host ID. They are completely
 * separate from the database and will NOT be included in JSON exports.
 */
@Singleton
class SecurePasswordStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return existingKey.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )

        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: String): String? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            // Combine IV and encrypted data
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.e(e, "Failed to encrypt password")
            null
        }
    }

    private fun decrypt(encryptedData: String): String? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)

            // Extract IV and encrypted data
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt password")
            null
        }
    }

    /**
     * Save a password for a host.
     *
     * @param hostId The unique host ID
     * @param password The password to save (null or empty to clear)
     */
    fun savePassword(hostId: Long, password: String?) {
        val key = getPasswordKey(hostId)
        if (password.isNullOrEmpty()) {
            prefs.edit().remove(key).apply()
        } else {
            val encrypted = encrypt(password)
            if (encrypted != null) {
                prefs.edit().putString(key, encrypted).apply()
            }
        }
    }

    /**
     * Get the saved password for a host.
     *
     * @param hostId The unique host ID
     * @return The saved password, or null if none saved
     */
    fun getPassword(hostId: Long): String? {
        val encrypted = prefs.getString(getPasswordKey(hostId), null) ?: return null
        return decrypt(encrypted)
    }

    /**
     * Check if a password is saved for a host.
     *
     * @param hostId The unique host ID
     * @return true if a password is saved
     */
    fun hasPassword(hostId: Long): Boolean = prefs.contains(getPasswordKey(hostId))

    /**
     * Delete the saved password for a host.
     * Should be called when a host is deleted.
     *
     * @param hostId The unique host ID
     */
    fun deletePassword(hostId: Long) {
        prefs.edit().remove(getPasswordKey(hostId)).apply()
    }

    private fun getPasswordKey(hostId: Long): String = "$KEY_PREFIX$hostId"

    companion object {
        private const val PREFS_FILE_NAME = "secure_host_passwords"
        private const val KEY_PREFIX = "password_"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "connectbot_password_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
}
