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

package org.connectbot.ui.screens.generatepubkey

import android.content.Context
import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.trilead.ssh2.crypto.keys.Ed25519Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.util.BiometricAvailability
import org.connectbot.util.BiometricKeyManager
import org.connectbot.util.PubkeyUtils
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security

enum class KeyType(
    val dbName: String,
    val minBits: Int,
    val maxBits: Int,
    val defaultBits: Int
) {
    RSA("RSA", 1024, 16384, 2048),
    DSA("DSA", 1024, 1024, 1024),
    EC("EC", 256, 521, 256),
    ED25519("Ed25519", 255, 255, 255)
}

data class GeneratePubkeyUiState(
    val nickname: String = "",
    val keyType: KeyType = KeyType.RSA,
    val bits: Int = KeyType.RSA.defaultBits,
    val minBits: Int = KeyType.RSA.minBits,
    val maxBits: Int = KeyType.RSA.maxBits,
    val allowBitStrengthChange: Boolean = true,
    val password1: String = "",
    val password2: String = "",
    val unlockAtStartup: Boolean = false,
    val confirmUse: Boolean = false,
    val showEntropyDialog: Boolean = false,
    val isGenerating: Boolean = false,
    val ecdsaAvailable: Boolean = true,
    val nicknameExists: Boolean = false,
    // Biometric authentication options
    val useBiometric: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricNotEnrolled: Boolean = false
) {
    val passwordMismatch: Boolean
        get() = password1 != password2 && password2.isNotEmpty()

    val canGenerate: Boolean
        get() = nickname.isNotEmpty() &&
                !nicknameExists &&
                (useBiometric || !passwordMismatch)

    /** Whether the current key type supports biometric protection */
    val keyTypeSupportsBiometric: Boolean
        get() = BiometricKeyManager.supportsBiometric(keyType)
}

@HiltViewModel
class GeneratePubkeyViewModel @Inject constructor(
    private val repository: PubkeyRepository,
    private val biometricKeyManager: BiometricKeyManager
) : ViewModel() {
    companion object {
        private const val TAG = "GeneratePubkeyViewModel"
        private val ECDSA_SIZES = intArrayOf(256, 384, 521)
    }

    init {
        // Ensure Ed25519 provider is available
        Ed25519Provider.insertIfNeeded()
    }

    private val _uiState = MutableStateFlow(
        GeneratePubkeyUiState(
            ecdsaAvailable = Security.getProviders("KeyPairGenerator.EC") != null,
            biometricAvailable = biometricKeyManager.isBiometricAvailable() == BiometricAvailability.AVAILABLE,
            biometricNotEnrolled = biometricKeyManager.isBiometricAvailable() == BiometricAvailability.NOT_ENROLLED
        )
    )
    val uiState: StateFlow<GeneratePubkeyUiState> = _uiState.asStateFlow()

    private var pendingEntropy: ByteArray? = null

    fun updateNickname(nickname: String) {
        _uiState.update { it.copy(nickname = nickname) }
        checkNicknameExists(nickname)
    }

    private fun checkNicknameExists(nickname: String) {
        if (nickname.isEmpty()) {
            _uiState.update { it.copy(nicknameExists = false) }
            return
        }

        viewModelScope.launch {
            try {
                val exists = withContext(Dispatchers.IO) {
                    repository.getByNickname(nickname) != null
                }
                _uiState.update { it.copy(nicknameExists = exists) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check if nickname exists")
            }
        }
    }

    fun updateKeyType(keyType: KeyType) {
        val allowBitStrengthChange = when (keyType) {
            KeyType.RSA, KeyType.EC -> true
            KeyType.DSA, KeyType.ED25519 -> false
        }

        // Disable biometric if key type doesn't support it
        val currentState = _uiState.value

        _uiState.update {
            it.copy(
                keyType = keyType,
                bits = keyType.defaultBits,
                minBits = keyType.minBits,
                maxBits = keyType.maxBits,
                allowBitStrengthChange = allowBitStrengthChange,
                useBiometric = if (BiometricKeyManager.supportsBiometric(keyType)) currentState.useBiometric else false
            )
        }
    }

    fun updateUseBiometric(useBiometric: Boolean) {
        val currentState = _uiState.value
        // Only allow biometric for RSA and EC keys
        if (useBiometric && !currentState.keyTypeSupportsBiometric) {
            return
        }
        _uiState.update {
            it.copy(
                useBiometric = useBiometric,
                // Clear passwords when switching to biometric
                password1 = if (useBiometric) "" else it.password1,
                password2 = if (useBiometric) "" else it.password2,
                // Biometric keys can't be auto-loaded at startup
                unlockAtStartup = if (useBiometric) false else it.unlockAtStartup
            )
        }
    }

    fun updateBits(bits: Int) {
        val currentState = _uiState.value
        val clampedBits = bits.coerceIn(currentState.minBits, currentState.maxBits)

        val finalBits = when (currentState.keyType) {
            KeyType.EC -> getClosestEcdsaSize(clampedBits)
            KeyType.ED25519 -> clampedBits
            else -> clampedBits - (clampedBits % 8) // Keep divisible by 8
        }

        _uiState.update { it.copy(bits = finalBits) }
    }

    private fun getClosestEcdsaSize(bits: Int): Int {
        return ECDSA_SIZES.minByOrNull { kotlin.math.abs(it - bits) } ?: ECDSA_SIZES[0]
    }

    fun updatePassword1(password: String) {
        _uiState.update { it.copy(password1 = password) }
    }

    fun updatePassword2(password: String) {
        _uiState.update { it.copy(password2 = password) }
    }

    fun updateUnlockAtStartup(checked: Boolean) {
        _uiState.update { it.copy(unlockAtStartup = checked) }
    }

    fun updateConfirmUse(checked: Boolean) {
        _uiState.update { it.copy(confirmUse = checked) }
    }

    private var onSuccessCallback: (() -> Unit)? = null

    fun generateKey(onSuccess: () -> Unit) {
        onSuccessCallback = onSuccess
        val currentState = _uiState.value

        if (currentState.useBiometric) {
            // Biometric keys don't need entropy gathering - go straight to generation
            startBiometricKeyGeneration()
        } else {
            // Traditional key generation needs entropy
            _uiState.update { it.copy(showEntropyDialog = true) }
        }
    }

    fun onEntropyGathered(entropy: ByteArray?) {
        _uiState.update { it.copy(showEntropyDialog = false) }

        if (entropy == null) {
            // User cancelled entropy gathering
            return
        }

        pendingEntropy = entropy
        startKeyGeneration()
    }

    fun cancelGeneration() {
        _uiState.update {
            it.copy(
                showEntropyDialog = false,
                isGenerating = false
            )
        }
        pendingEntropy = null
    }

    private fun startKeyGeneration() {
        val entropy = pendingEntropy ?: return
        val currentState = _uiState.value
        val callback = onSuccessCallback

        _uiState.update { it.copy(isGenerating = true) }

        viewModelScope.launch {
            try {
                val keyPair = withContext(Dispatchers.IO) {
                    generateKeyPair(
                        currentState.keyType,
                        currentState.bits,
                        entropy
                    )
                }

                saveKeyPair(keyPair, currentState, callback)
            } catch (e: Exception) {
                Timber.e(e, "Failed to generate key pair")
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    private fun startBiometricKeyGeneration() {
        val currentState = _uiState.value
        val callback = onSuccessCallback

        _uiState.update { it.copy(isGenerating = true) }

        viewModelScope.launch {
            try {
                val (publicKey, alias) = withContext(Dispatchers.IO) {
                    val alias = biometricKeyManager.generateKeyAlias()
                    val publicKey = biometricKeyManager.generateKey(
                        alias = alias,
                        keyType = currentState.keyType.dbName,
                        keySize = currentState.bits
                    )
                    Pair(publicKey, alias)
                }

                saveBiometricKey(publicKey, alias, currentState, callback)
            } catch (e: Exception) {
                Timber.e(e, "Failed to generate biometric key")
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    private fun generateKeyPair(keyType: KeyType, bits: Int, entropy: ByteArray): KeyPair {
        val random = SecureRandom()
        // Work around JVM bug
        random.nextInt()
        random.setSeed(entropy)

        Timber.d("Starting generation of $keyType of strength $bits")

        val keyPairGen = KeyPairGenerator.getInstance(keyType.dbName)
        keyPairGen.initialize(bits, random)
        return keyPairGen.generateKeyPair()
    }

    private suspend fun saveKeyPair(
        keyPair: KeyPair,
        state: GeneratePubkeyUiState,
        onSuccess: (() -> Unit)?
    ) {
        withContext(Dispatchers.IO) {
            try {
                val encrypted = state.password1.isNotEmpty()

                val pubkey = Pubkey(
                    id = 0, // Let Room auto-generate the ID
                    nickname = state.nickname,
                    type = state.keyType.dbName,
                    privateKey = PubkeyUtils.getEncodedPrivate(keyPair.private, state.password1),
                    publicKey = keyPair.public.encoded,
                    encrypted = encrypted,
                    startup = state.unlockAtStartup && !encrypted,
                    confirmation = state.confirmUse,
                    createdDate = System.currentTimeMillis()
                )

                repository.save(pubkey)

                Timber.d("Key pair saved successfully")

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isGenerating = false) }
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save key pair")
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isGenerating = false) }
                }
            }
        }
    }

    private suspend fun saveBiometricKey(
        publicKey: java.security.PublicKey,
        keystoreAlias: String,
        state: GeneratePubkeyUiState,
        onSuccess: (() -> Unit)?
    ) {
        withContext(Dispatchers.IO) {
            try {
                val pubkey = Pubkey(
                    id = 0, // Let Room auto-generate the ID
                    nickname = state.nickname,
                    type = state.keyType.dbName,
                    privateKey = null, // Private key stays in Android Keystore
                    publicKey = publicKey.encoded,
                    encrypted = false, // Not applicable for biometric keys
                    startup = false, // Biometric keys can't auto-load
                    confirmation = state.confirmUse,
                    createdDate = System.currentTimeMillis(),
                    storageType = KeyStorageType.ANDROID_KEYSTORE,
                    allowBackup = false, // Keystore keys can't be backed up
                    keystoreAlias = keystoreAlias
                )

                repository.save(pubkey)

                Timber.d("Biometric key saved successfully with alias: $keystoreAlias")

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isGenerating = false) }
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save biometric key")
                // Clean up the Keystore key if database save failed
                try {
                    biometricKeyManager.deleteKey(keystoreAlias)
                } catch (deleteError: Exception) {
                    Timber.e("Failed to clean up Keystore key after save failure", deleteError)
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isGenerating = false) }
                }
            }
        }
    }
}
