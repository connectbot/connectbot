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
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import org.connectbot.ui.screens.generatepubkey.KeyType
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages SSH keys stored in Android Keystore with biometric authentication.
 *
 * Keys generated with this manager:
 * - Are stored in the device's secure hardware (TEE or StrongBox)
 * - Require biometric authentication for signing operations
 * - Cannot be exported or backed up
 * - Are invalidated if new biometrics are enrolled
 */
@Singleton
class BiometricKeyManager @Inject constructor(
	@ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "BiometricKeyManager"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "connectbot_bio_"

        // Authentication validity duration in seconds after successful biometric auth
        private const val AUTH_VALIDITY_DURATION_SECONDS = 30

        // Supported key types for biometric protection
        val SUPPORTED_KEY_TYPES = listOf("RSA", "EC")

        /**
         * Check if a KeyType supports biometric protection.
         * Only RSA and EC keys can be stored in Android Keystore with biometric auth.
         */
        fun supportsBiometric(keyType: KeyType): Boolean {
            return keyType == KeyType.RSA || keyType == KeyType.EC
        }
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    /**
     * Check if biometric authentication is available and configured on this device.
     */
    fun isBiometricAvailable(): BiometricAvailability {
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

    /**
     * Generate a unique alias for a new biometric key.
     */
    fun generateKeyAlias(): String {
        return KEY_ALIAS_PREFIX + UUID.randomUUID().toString()
    }

    /**
     * Generate an RSA key pair in Android Keystore with biometric authentication requirement.
     *
     * @param alias The alias to store the key under
     * @param keySize The RSA key size in bits (2048, 3072, or 4096)
     * @return The public key (private key remains in Keystore)
     */
    fun generateRsaKey(alias: String, keySize: Int = 4096): PublicKey {
        Log.d(TAG, "Generating RSA key with alias: $alias, size: $keySize")

        // Try with StrongBox first, fall back to TEE if not available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateRsaKeyInternal(alias, keySize, useStrongBox = true)
            } catch (e: Exception) {
                Log.w(TAG, "StrongBox key generation failed, falling back to TEE", e)
                // Delete any partial key that might have been created
                try { deleteKey(alias) } catch (_: Exception) {}
            }
        }

        return generateRsaKeyInternal(alias, keySize, useStrongBox = false)
    }

    private fun generateRsaKeyInternal(alias: String, keySize: Int, useStrongBox: Boolean): PublicKey {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            KEYSTORE_PROVIDER
        )

        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setKeySize(keySize)
            setDigests(
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512
            )
            setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)

            // Require biometric authentication for every use
            setUserAuthenticationRequired(true)

            // Set authentication validity duration - allows signing for N seconds after auth
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(
                    AUTH_VALIDITY_DURATION_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG
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

        Log.d(TAG, "RSA key generated successfully (StrongBox: $useStrongBox)")
        return keyPair.public
    }

    /**
     * Generate an EC (ECDSA) key pair in Android Keystore with biometric authentication requirement.
     *
     * @param alias The alias to store the key under
     * @param keySize The EC key size in bits (256, 384, or 521)
     * @return The public key (private key remains in Keystore)
     */
    fun generateEcKey(alias: String, keySize: Int = 256): PublicKey {
        Log.d(TAG, "Generating EC key with alias: $alias, size: $keySize")

        // Try with StrongBox first, fall back to TEE if not available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateEcKeyInternal(alias, keySize, useStrongBox = true)
            } catch (e: Exception) {
                Log.w(TAG, "StrongBox key generation failed, falling back to TEE", e)
                // Delete any partial key that might have been created
                try { deleteKey(alias) } catch (_: Exception) {}
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
            KEYSTORE_PROVIDER
        )

        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setAlgorithmParameterSpec(ECGenParameterSpec(curveName))
            setDigests(
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512
            )

            // Require biometric authentication for every use
            setUserAuthenticationRequired(true)

            // Set authentication validity duration - allows signing for N seconds after auth
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setUserAuthenticationParameters(
                    AUTH_VALIDITY_DURATION_SECONDS,
                    KeyProperties.AUTH_BIOMETRIC_STRONG
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

        Log.d(TAG, "EC key generated successfully (StrongBox: $useStrongBox)")
        return keyPair.public
    }

    /**
     * Generate a biometric-protected key of the specified type.
     *
     * @param alias The alias to store the key under
     * @param keyType "RSA" or "EC"
     * @param keySize The key size in bits
     * @return The public key
     */
    fun generateKey(alias: String, keyType: String, keySize: Int): PublicKey {
        return when (keyType) {
            "RSA" -> generateRsaKey(alias, keySize)
            "EC" -> generateEcKey(alias, keySize)
            else -> throw IllegalArgumentException("Unsupported key type for biometric protection: $keyType")
        }
    }

    /**
     * Get a CryptoObject for BiometricPrompt authentication.
     * This must be used before signing operations.
     *
     * @param alias The key alias
     * @param algorithm The signature algorithm (e.g., "SHA256withRSA", "SHA256withECDSA")
     * @return A CryptoObject containing the Signature instance
     */
    fun getCryptoObject(alias: String, algorithm: String): BiometricPrompt.CryptoObject {
        val privateKey = keyStore.getKey(alias, null) as PrivateKey
        val signature = Signature.getInstance(algorithm)
        signature.initSign(privateKey)
        return BiometricPrompt.CryptoObject(signature)
    }

    /**
     * Get the signature algorithm for a key type.
     *
     * @param keyType The key type ("RSA" or "EC")
     * @param keySize The key size in bits (used to determine hash algorithm for EC keys)
     * @return The signature algorithm string
     */
    fun getSignatureAlgorithm(keyType: String, keySize: Int = 256): String {
        return when (keyType) {
            "RSA" -> "SHA256withRSA"
            "EC" -> when (keySize) {
                521 -> "SHA512withECDSA"
                384 -> "SHA384withECDSA"
                else -> "SHA256withECDSA" // P-256 and any other size
            }
            else -> throw IllegalArgumentException("Unsupported key type: $keyType")
        }
    }

    /**
     * Get the private key from Keystore.
     * Note: This will fail if biometric authentication is required and not satisfied.
     *
     * @param alias The key alias
     * @return The private key
     */
    fun getPrivateKey(alias: String): PrivateKey {
        return keyStore.getKey(alias, null) as PrivateKey
    }

    /**
     * Get the public key from Keystore.
     *
     * @param alias The key alias
     * @return The public key, or null if not found
     */
    fun getPublicKey(alias: String): PublicKey? {
        return keyStore.getCertificate(alias)?.publicKey
    }

    /**
     * Check if a key exists in the Keystore.
     *
     * @param alias The key alias
     * @return True if the key exists
     */
    fun keyExists(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }

    /**
     * Delete a key from the Keystore.
     *
     * @param alias The key alias
     */
    fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
            Log.d(TAG, "Key deleted: $alias")
        }
    }

    /**
     * Sign data using a key in the Keystore.
     * This should be called after successful BiometricPrompt authentication.
     *
     * @param signature The authenticated Signature from BiometricPrompt.CryptoObject
     * @param data The data to sign
     * @return The signature bytes
     */
    fun sign(signature: Signature, data: ByteArray): ByteArray {
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
    UNKNOWN_ERROR
}
