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
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import org.connectbot.ui.screens.generatepubkey.KeyType
import timber.log.Timber
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

interface BiometricKeyManager {
    fun isBiometricAvailable(): BiometricAvailability
    fun generateKeyAlias(): String
    fun generateRsaKey(alias: String, keySize: Int = 4096): PublicKey
    fun generateEcKey(alias: String, keySize: Int = 256): PublicKey
    fun generateKey(alias: String, keyType: String, keySize: Int): PublicKey
    fun getCryptoObject(alias: String, algorithm: String): BiometricPrompt.CryptoObject
    fun getSignatureAlgorithm(keyType: String, keySize: Int = 256): String
    fun getPrivateKey(alias: String): PrivateKey
    fun getPublicKey(alias: String): PublicKey?
    fun keyExists(alias: String): Boolean
    fun deleteKey(alias: String)
    fun sign(signature: Signature, data: ByteArray): ByteArray

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        val SUPPORTED_KEY_TYPES = listOf("RSA", "EC")

        fun supportsBiometric(keyType: KeyType): Boolean = keyType == KeyType.RSA || keyType == KeyType.EC
    }
}

/**
 * Manages SSH keys stored in Android Keystore with biometric authentication.
 *
 * Keys generated with this manager:
 * - Are stored in the device's secure hardware (TEE or StrongBox)
 * - Require biometric authentication for signing operations
 * - Cannot be exported or backed up
 * - Are invalidated if new biometrics are enrolled
 */
class BiometricKeyManagerImpl(
    private val context: Context,
) : BiometricKeyManager {
    companion object {
        private const val TAG = "BiometricKeyManager"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "connectbot_bio_"

        // Authentication validity duration in seconds after successful biometric auth
        private const val AUTH_VALIDITY_DURATION_SECONDS = 30
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    override fun isBiometricAvailable(): BiometricAvailability {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.SECURITY_UPDATE_REQUIRED
            else -> BiometricAvailability.UNKNOWN_ERROR
        }
    }

    override fun generateKeyAlias(): String = KEY_ALIAS_PREFIX + UUID.randomUUID().toString()

    override fun generateRsaKey(alias: String, keySize: Int): PublicKey {
        Timber.d("Generating RSA key with alias: $alias, size: $keySize")

        // Try with StrongBox first, fall back to TEE if not available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateRsaKeyInternal(alias, keySize, useStrongBox = true)
            } catch (e: Exception) {
                Timber.w("StrongBox key generation failed, falling back to TEE", e)
                // Delete any partial key that might have been created
                try {
                    deleteKey(alias)
                } catch (_: Exception) {}
            }
        }

        return generateRsaKeyInternal(alias, keySize, useStrongBox = false)
    }

    private fun generateRsaKeyInternal(alias: String, keySize: Int, useStrongBox: Boolean): PublicKey {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            KEYSTORE_PROVIDER,
        )

        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        ).apply {
            setKeySize(keySize)
            setDigests(
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512,
            )
            setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)

            // Require biometric authentication for every use
            setUserAuthenticationRequired(true)

            // Set authentication validity duration - allows signing for N seconds after auth
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(
                    AUTH_VALIDITY_DURATION_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG,
                )
            } else {
                @Suppress("DEPRECATION")
                setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_DURATION_SECONDS)
            }

            // Invalidate key if new biometrics are enrolled
            setInvalidatedByBiometricEnrollment(true)

            // Use StrongBox if requested
            if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setIsStrongBoxBacked(true)
            }
        }

        keyPairGenerator.initialize(builder.build())
        val keyPair = keyPairGenerator.generateKeyPair()

        Timber.d("RSA key generated successfully (StrongBox: $useStrongBox)")
        return keyPair.public
    }

    override fun generateEcKey(alias: String, keySize: Int): PublicKey {
        Timber.d("Generating EC key with alias: $alias, size: $keySize")

        // Try with StrongBox first, fall back to TEE if not available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateEcKeyInternal(alias, keySize, useStrongBox = true)
            } catch (e: Exception) {
                Timber.w("StrongBox key generation failed, falling back to TEE", e)
                // Delete any partial key that might have been created
                try {
                    deleteKey(alias)
                } catch (_: Exception) {}
            }
        }

        return generateEcKeyInternal(alias, keySize, useStrongBox = false)
    }

    private fun generateEcKeyInternal(alias: String, keySize: Int, useStrongBox: Boolean): PublicKey {
        val curveName = when (keySize) {
            256 -> "secp256r1"
            384 -> "secp384r1"
            521 -> "secp521r1"
            else -> throw IllegalArgumentException("Unsupported EC key size: $keySize")
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_PROVIDER,
        )

        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        ).apply {
            setAlgorithmParameterSpec(ECGenParameterSpec(curveName))
            setDigests(
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512,
            )

            // Require biometric authentication for every use
            setUserAuthenticationRequired(true)

            // Set authentication validity duration - allows signing for N seconds after auth
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(
                    AUTH_VALIDITY_DURATION_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG,
                )
            } else {
                @Suppress("DEPRECATION")
                setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_DURATION_SECONDS)
            }

            // Invalidate key if new biometrics are enrolled
            setInvalidatedByBiometricEnrollment(true)

            // Use StrongBox if requested
            if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setIsStrongBoxBacked(true)
            }
        }

        keyPairGenerator.initialize(builder.build())
        val keyPair = keyPairGenerator.generateKeyPair()

        Timber.d("EC key generated successfully (StrongBox: $useStrongBox)")
        return keyPair.public
    }

    override fun generateKey(alias: String, keyType: String, keySize: Int): PublicKey = when (keyType) {
        "RSA" -> generateRsaKey(alias, keySize)
        "EC" -> generateEcKey(alias, keySize)
        else -> throw IllegalArgumentException("Unsupported key type for biometric protection: $keyType")
    }

    override fun getCryptoObject(alias: String, algorithm: String): BiometricPrompt.CryptoObject {
        val privateKey = keyStore.getKey(alias, null) as PrivateKey
        val signature = Signature.getInstance(algorithm)
        signature.initSign(privateKey)
        return BiometricPrompt.CryptoObject(signature)
    }

    override fun getSignatureAlgorithm(keyType: String, keySize: Int): String = when (keyType) {
        "RSA" -> "SHA256withRSA"

        "EC" -> when (keySize) {
            521 -> "SHA512withECDSA"
            384 -> "SHA384withECDSA"
            else -> "SHA256withECDSA"
        }

        else -> throw IllegalArgumentException("Unsupported key type: $keyType")
    }

    override fun getPrivateKey(alias: String): PrivateKey = keyStore.getKey(alias, null) as PrivateKey

    override fun getPublicKey(alias: String): PublicKey? = keyStore.getCertificate(alias)?.publicKey

    override fun keyExists(alias: String): Boolean = keyStore.containsAlias(alias)

    override fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
            Timber.d("Key deleted: $alias")
        }
    }

    override fun sign(signature: Signature, data: ByteArray): ByteArray {
        signature.update(data)
        return signature.sign()
    }
}

/**
 * Represents the availability status of biometric authentication.
 */
enum class BiometricAvailability {
    /** Biometric authentication is available and ready to use */
    AVAILABLE,

    /** No biometric hardware on this device */
    NO_HARDWARE,

    /** Biometric hardware is currently unavailable */
    HARDWARE_UNAVAILABLE,

    /** No biometrics are enrolled on this device */
    NOT_ENROLLED,

    /** A security update is required */
    SECURITY_UPDATE_REQUIRED,

    /** Unknown error */
    UNKNOWN_ERROR,
}
