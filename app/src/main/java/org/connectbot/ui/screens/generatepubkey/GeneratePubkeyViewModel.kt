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
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trilead.ssh2.crypto.keys.Ed25519Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Pubkey
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
    val nicknameExists: Boolean = false
) {
    val passwordMismatch: Boolean
        get() = password1 != password2 && password2.isNotEmpty()

    val canGenerate: Boolean
        get() = nickname.isNotEmpty() && !passwordMismatch && !nicknameExists
}

class GeneratePubkeyViewModel(
    private val context: Context
) : ViewModel() {
    companion object {
        private const val TAG = "GeneratePubkeyViewModel"
        private val ECDSA_SIZES = intArrayOf(256, 384, 521)
    }

    init {
        // Ensure Ed25519 provider is available
        Ed25519Provider.insertIfNeeded()
    }

    private val repository: PubkeyRepository = PubkeyRepository.get(context)

    private val _uiState = MutableStateFlow(
        GeneratePubkeyUiState(
            ecdsaAvailable = Security.getProviders("KeyPairGenerator.EC") != null
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
                Log.e(TAG, "Failed to check if nickname exists", e)
            }
        }
    }

    fun updateKeyType(keyType: KeyType) {
        val allowBitStrengthChange = when (keyType) {
            KeyType.RSA, KeyType.EC -> true
            KeyType.DSA, KeyType.ED25519 -> false
        }

        _uiState.update {
            it.copy(
                keyType = keyType,
                bits = keyType.defaultBits,
                minBits = keyType.minBits,
                maxBits = keyType.maxBits,
                allowBitStrengthChange = allowBitStrengthChange
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
        _uiState.update { it.copy(showEntropyDialog = true) }
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
                Log.e(TAG, "Failed to generate key pair", e)
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    private fun generateKeyPair(keyType: KeyType, bits: Int, entropy: ByteArray): KeyPair {
        val random = SecureRandom()
        // Work around JVM bug
        random.nextInt()
        random.setSeed(entropy)

        Log.d(TAG, "Starting generation of $keyType of strength $bits")

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

                Log.d(TAG, "Key pair saved successfully")

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isGenerating = false) }
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save key pair", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isGenerating = false) }
                }
            }
        }
    }
}
