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

package org.connectbot.ui.screens.fido2

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.fido2.CoseKeyDecoder
import org.connectbot.fido2.Fido2Algorithm
import org.connectbot.fido2.Fido2ConnectionState
import org.connectbot.fido2.Fido2Credential
import org.connectbot.fido2.Fido2Manager
import org.connectbot.fido2.Fido2Result
import org.connectbot.fido2.ssh.SkEcdsaPublicKey
import org.connectbot.fido2.ssh.SkEcdsaVerify
import org.connectbot.fido2.ssh.SkEd25519PublicKey
import org.connectbot.fido2.ssh.SkEd25519Verify
import org.connectbot.util.PubkeyConstants
import timber.log.Timber
import javax.inject.Inject

/**
 * UI state for the Import FIDO2 screen.
 */
data class ImportFido2UiState(
    val connectionState: Fido2ConnectionState = Fido2ConnectionState.Disconnected,
    val credentials: List<Fido2Credential> = emptyList(),
    val selectedCredential: Fido2Credential? = null,
    val nickname: String = "",
    val isScanning: Boolean = false,
    val needsPin: Boolean = false,
    val pinError: String? = null,
    val error: String? = null,
    val importSuccess: Boolean = false
)

@HiltViewModel
class ImportFido2ViewModel @Inject constructor(
    private val fido2Manager: Fido2Manager,
    private val repository: PubkeyRepository,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportFido2UiState())
    val uiState: StateFlow<ImportFido2UiState> = _uiState.asStateFlow()

    private var currentPin: String? = null

    init {
        observeConnectionState()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            fido2Manager.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }

                when (state) {
                    is Fido2ConnectionState.Connected -> {
                        // Device connected, start scanning for credentials
                        scanForCredentials()
                    }
                    is Fido2ConnectionState.Error -> {
                        _uiState.update {
                            it.copy(
                                error = state.message,
                                isScanning = false
                            )
                        }
                    }
                    else -> { /* No action needed */ }
                }
            }
        }
    }

    /**
     * Start USB device discovery.
     * YubiKit will automatically connect when a device is detected.
     */
    fun startUsbDiscovery() {
        fido2Manager.startUsbDiscovery()
    }

    /**
     * Stop USB device discovery.
     */
    fun stopUsbDiscovery() {
        fido2Manager.stopUsbDiscovery()
    }

    /**
     * Handle an NFC tag discovery.
     * Call this when the Activity receives an NFC tag intent.
     */
    fun onNfcTagDiscovered(tag: Tag) {
        viewModelScope.launch(dispatchers.io) {
            fido2Manager.connectToNfcTag(tag)
        }
    }

    private fun scanForCredentials() {
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isScanning = true, error = null, credentials = emptyList()) }

            try {
                // First check if PIN is required
                val infoResult = fido2Manager.getAuthenticatorInfo()
                if (infoResult is Fido2Result.Success) {
                    val info = infoResult.value
                    if (info.pinConfigured && currentPin == null) {
                        // PIN is required but we don't have it yet
                        _uiState.update {
                            it.copy(
                                isScanning = false,
                                needsPin = true
                            )
                        }
                        return@launch
                    }
                }

                // Discover SSH credentials
                val result = fido2Manager.discoverSshCredentials(currentPin)
                when (result) {
                    is Fido2Result.Success -> {
                        val credentials = result.value
                        _uiState.update {
                            it.copy(
                                credentials = credentials,
                                isScanning = false,
                                needsPin = false,
                                error = if (credentials.isEmpty()) "No SSH credentials found on security key" else null
                            )
                        }
                    }
                    is Fido2Result.PinRequired -> {
                        _uiState.update {
                            it.copy(
                                isScanning = false,
                                needsPin = true
                            )
                        }
                    }
                    is Fido2Result.PinInvalid -> {
                        currentPin = null
                        _uiState.update {
                            it.copy(
                                isScanning = false,
                                needsPin = true,
                                pinError = "Invalid PIN. ${result.attemptsRemaining ?: "Unknown"} attempts remaining."
                            )
                        }
                    }
                    is Fido2Result.Error -> {
                        _uiState.update {
                            it.copy(
                                isScanning = false,
                                error = result.message
                            )
                        }
                    }
                    else -> {
                        _uiState.update {
                            it.copy(
                                isScanning = false,
                                error = "Unexpected result"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to scan for FIDO2 credentials")
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        error = "Failed to scan: ${e.message}"
                    )
                }
            }
        }
    }

    fun submitPin(pin: String) {
        currentPin = pin
        _uiState.update { it.copy(pinError = null) }
        scanForCredentials()
    }

    fun selectCredential(credential: Fido2Credential) {
        // Generate a default nickname based on user info
        val defaultNickname = credential.userName ?: "FIDO2 Key"

        _uiState.update {
            it.copy(
                selectedCredential = credential,
                nickname = defaultNickname
            )
        }
    }

    fun updateNickname(nickname: String) {
        _uiState.update { it.copy(nickname = nickname) }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedCredential = null,
                nickname = ""
            )
        }
    }

    fun importSelectedCredential() {
        val state = _uiState.value
        val credential = state.selectedCredential ?: return
        val nickname = state.nickname.ifBlank { "FIDO2 Key" }

        viewModelScope.launch(dispatchers.io) {
            try {
                // Convert FIDO2 credential to Pubkey entity
                val pubkey = createPubkeyFromCredential(credential, nickname)
                repository.save(pubkey)

                _uiState.update {
                    it.copy(importSuccess = true)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to import FIDO2 credential")
                _uiState.update {
                    it.copy(error = "Failed to import: ${e.message}")
                }
            }
        }
    }

    private fun createPubkeyFromCredential(credential: Fido2Credential, nickname: String): Pubkey {
        // Determine key type and encode public key in SSH format
        // The credential contains COSE-encoded key, we need to decode it to raw bytes first
        val (keyType, publicKeyBytes) = when (credential.algorithm) {
            Fido2Algorithm.EDDSA -> {
                // Decode COSE key to get raw 32-byte Ed25519 public key
                val ed25519Key = CoseKeyDecoder.decodeEd25519PublicKey(credential.publicKeyCose)
                val skPubKey = SkEd25519PublicKey(
                    application = credential.rpId,
                    ed25519Key = ed25519Key
                )
                PubkeyConstants.KEY_TYPE_SK_ED25519 to SkEd25519Verify.encodePublicKey(skPubKey)
            }
            Fido2Algorithm.ES256 -> {
                // Decode COSE key to get uncompressed EC point (0x04 || x || y)
                val ecPoint = CoseKeyDecoder.decodeEcdsaP256PublicKey(credential.publicKeyCose)
                val skPubKey = SkEcdsaPublicKey(
                    application = credential.rpId,
                    ecPoint = ecPoint,
                    curve = "nistp256"
                )
                PubkeyConstants.KEY_TYPE_SK_ECDSA to SkEcdsaVerify.encodePublicKey(skPubKey)
            }
        }

        return Pubkey(
            id = 0,
            nickname = nickname,
            type = keyType,
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = System.currentTimeMillis(),
            privateKey = null, // FIDO2 keys don't have extractable private keys
            publicKey = publicKeyBytes,
            storageType = KeyStorageType.FIDO2_RESIDENT_KEY,
            credentialId = credential.credentialId,
            fido2RpId = credential.rpId
        )
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
