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

import android.app.Activity
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
import org.connectbot.data.entity.Fido2Transport
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
    val selectedTransport: Fido2Transport = Fido2Transport.USB,
    val transportSelected: Boolean = false, // True when transport has been selected
    val isScanning: Boolean = false,
    val needsPin: Boolean = false,
    val waitingForNfcTap: Boolean = false, // True when PIN entered and waiting for NFC tap
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
        // Start with transport selection - user picks USB or NFC first
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            fido2Manager.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }

                when (state) {
                    is Fido2ConnectionState.Connected -> {
                        // Both USB and NFC now use callback-based credential delivery
                        // When USB connects for the first time (without PIN), just show PIN prompt
                        // When PIN is submitted, retryUsbWithPin is called and results come via callback
                        if (state.transport == "USB" && _uiState.value.credentials.isEmpty() && !_uiState.value.isScanning) {
                            // First USB connection - show PIN prompt
                            _uiState.update {
                                it.copy(needsPin = true)
                            }
                        }
                        // NFC: Results delivered via callback, nothing to do here
                    }

                    is Fido2ConnectionState.Error -> {
                        _uiState.update {
                            it.copy(
                                error = state.message,
                                isScanning = false,
                                waitingForNfcTap = false
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
     * Start NFC device discovery.
     * Call this in Activity.onResume().
     */
    fun startNfcDiscovery(activity: Activity) {
        fido2Manager.startNfcDiscovery(activity)
    }

    /**
     * Stop NFC device discovery.
     * Call this in Activity.onPause().
     */
    fun stopNfcDiscovery(activity: Activity) {
        fido2Manager.stopNfcDiscovery(activity)
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

    /**
     * Scan for credentials on USB-connected device.
     * NFC credentials are handled via callback in connectToDevice.
     */
    private fun scanForCredentials() {
        viewModelScope.launch(dispatchers.io) {
            _uiState.update { it.copy(isScanning = true, error = null, credentials = emptyList(), waitingForNfcTap = false) }

            try {
                // Check if PIN is required for USB
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

                // Discover SSH credentials via USB
                val result = fido2Manager.discoverSshCredentials(currentPin)
                handleCredentialResult(result)
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

        // Check if we're already connected via USB
        val currentState = _uiState.value.connectionState
        val isUsbConnected = currentState is Fido2ConnectionState.Connected &&
            currentState.transport == "USB"

        // Set the PIN for both USB and NFC
        fido2Manager.setPendingPin(pin)

        if (isUsbConnected) {
            // USB: Set callback and retry with PIN
            fido2Manager.setUsbCredentialCallback { result ->
                viewModelScope.launch {
                    handleCredentialResult(result)
                }
            }

            _uiState.update {
                it.copy(
                    pinError = null,
                    needsPin = false,
                    isScanning = true
                )
            }

            // Retry USB connection with PIN - all operations happen in callback
            fido2Manager.retryUsbWithPin()
        } else {
            // NFC mode: Set up for NFC tap
            fido2Manager.setNfcCredentialCallback { result ->
                viewModelScope.launch {
                    handleCredentialResult(result)
                }
            }

            _uiState.update {
                it.copy(
                    pinError = null,
                    needsPin = false,
                    waitingForNfcTap = true // Now waiting for user to tap NFC
                )
            }
            // Wait for NFC tap which will trigger connection
            // Fido2Manager will do all operations in the NFC callback and deliver results
        }
    }

    private fun handleCredentialResult(result: Fido2Result<List<Fido2Credential>>) {
        when (result) {
            is Fido2Result.Success -> {
                val credentials = result.value
                _uiState.update {
                    it.copy(
                        credentials = credentials,
                        isScanning = false,
                        needsPin = false,
                        waitingForNfcTap = false,
                        error = if (credentials.isEmpty()) "No SSH credentials found on security key" else null
                    )
                }
            }

            is Fido2Result.PinRequired -> {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        needsPin = true,
                        waitingForNfcTap = false
                    )
                }
            }

            is Fido2Result.PinInvalid -> {
                currentPin = null
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        needsPin = true,
                        waitingForNfcTap = false,
                        pinError = "Invalid PIN. ${result.attemptsRemaining ?: "Unknown"} attempts remaining."
                    )
                }
            }

            is Fido2Result.PinLocked -> {
                currentPin = null
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        needsPin = false,
                        waitingForNfcTap = false,
                        error = result.message
                    )
                }
            }

            is Fido2Result.Error -> {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        waitingForNfcTap = false,
                        error = result.message
                    )
                }
            }

            is Fido2Result.Cancelled -> {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        waitingForNfcTap = false
                    )
                }
            }
        }
    }

    fun selectCredential(credential: Fido2Credential) {
        // Use the username from the credential as default nickname
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

    fun updateTransport(transport: Fido2Transport) {
        _uiState.update { it.copy(selectedTransport = transport) }
    }

    /**
     * Confirm transport selection and start the connection flow.
     * For USB: Start discovery and wait for connection
     * For NFC: Show PIN prompt first (connection is transient)
     */
    fun confirmTransportSelection() {
        val transport = _uiState.value.selectedTransport
        _uiState.update { it.copy(transportSelected = true) }

        when (transport) {
            Fido2Transport.USB -> {
                // USB: Start discovery and wait for connection
                // PIN prompt will show after device is detected
                fido2Manager.startUsbDiscovery()
            }

            Fido2Transport.NFC -> {
                // NFC: Need PIN upfront since connection is transient
                _uiState.update { it.copy(needsPin = true) }
            }
        }
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
        val transport = state.selectedTransport

        viewModelScope.launch(dispatchers.io) {
            try {
                // Convert FIDO2 credential to Pubkey entity
                val pubkey = createPubkeyFromCredential(credential, nickname, transport)
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

    private fun createPubkeyFromCredential(credential: Fido2Credential, nickname: String, transport: Fido2Transport): Pubkey {
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
            fido2RpId = credential.rpId,
            fido2Transport = transport
        )
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
