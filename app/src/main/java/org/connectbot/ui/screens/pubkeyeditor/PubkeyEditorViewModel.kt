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

package org.connectbot.ui.screens.pubkeyeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Fido2Transport
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.PubkeyUtils
import timber.log.Timber
import javax.inject.Inject

data class PubkeyEditorUiState(
    val nickname: String = "",
    val keyType: String = "",
    val isEncrypted: Boolean = false,
    val oldPassword: String = "",
    val newPassword1: String = "",
    val newPassword2: String = "",
    val unlockAtStartup: Boolean = false,
    val confirmUse: Boolean = false,
    val isFido2: Boolean = false,
    val fido2Transport: Fido2Transport = Fido2Transport.USB,
    val isLoading: Boolean = true,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val wrongPassword: Boolean = false,
    val nicknameExists: Boolean = false
) {
    val passwordMismatch: Boolean
        get() = newPassword1 != newPassword2 && newPassword2.isNotEmpty()

    val willBeEncrypted: Boolean
        get() = newPassword1.isNotEmpty()

    val canSave: Boolean
        get() = nickname.isNotEmpty() && !passwordMismatch && !wrongPassword &&
                !(willBeEncrypted && unlockAtStartup) && !nicknameExists
}

@HiltViewModel
class PubkeyEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PubkeyRepository,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {

    private val pubkeyId: Long = savedStateHandle.get<Long>("pubkeyId") ?: -1L

    private val _uiState = MutableStateFlow(PubkeyEditorUiState())
    val uiState: StateFlow<PubkeyEditorUiState> = _uiState.asStateFlow()

    private var originalPubkey: Pubkey? = null

    init {
        loadPubkey()
    }

    private fun loadPubkey() {
        viewModelScope.launch {
            try {
                val pubkey = withContext(dispatchers.io) {
                    repository.getById(pubkeyId)
                }

                if (pubkey != null) {
                    originalPubkey = pubkey
                    _uiState.update {
                        it.copy(
                            nickname = pubkey.nickname,
                            keyType = pubkey.type,
                            isEncrypted = pubkey.encrypted,
                            unlockAtStartup = pubkey.startup,
                            confirmUse = pubkey.confirmation,
                            isFido2 = pubkey.isFido2,
                            fido2Transport = pubkey.fido2Transport ?: Fido2Transport.USB,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Public key not found"
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load pubkey")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load public key: ${e.message}"
                    )
                }
            }
        }
    }

    fun updateNickname(nickname: String) {
        _uiState.update { it.copy(nickname = nickname) }
        checkNicknameExists(nickname)
    }

    private fun checkNicknameExists(nickname: String) {
        val originalNickname = originalPubkey?.nickname ?: ""

        // Don't check if it's the same as the original nickname
        if (nickname.isEmpty() || nickname == originalNickname) {
            _uiState.update { it.copy(nicknameExists = false) }
            return
        }

        viewModelScope.launch {
            try {
                val exists = withContext(dispatchers.io) {
                    repository.getByNickname(nickname) != null
                }
                _uiState.update { it.copy(nicknameExists = exists) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check if nickname exists")
            }
        }
    }

    fun updateOldPassword(password: String) {
        _uiState.update { it.copy(oldPassword = password, wrongPassword = false) }
    }

    fun updateNewPassword1(password: String) {
        _uiState.update { it.copy(newPassword1 = password) }
    }

    fun updateNewPassword2(password: String) {
        _uiState.update { it.copy(newPassword2 = password) }
    }

    fun updateUnlockAtStartup(checked: Boolean) {
        _uiState.update { it.copy(unlockAtStartup = checked) }
    }

    fun updateConfirmUse(checked: Boolean) {
        _uiState.update { it.copy(confirmUse = checked) }
    }

    fun updateFido2Transport(transport: Fido2Transport) {
        _uiState.update { it.copy(fido2Transport = transport) }
    }

    fun save() {
        val pubkey = originalPubkey ?: return
        val state = _uiState.value

        viewModelScope.launch {
            try {
                withContext(dispatchers.io) {
                    // Handle password change if needed
                    // Password needs to be changed if:
                    // 1. Key is encrypted and user provided old password (removing or changing password)
                    // 2. Key is not encrypted but user provided new password (adding password)
                    val needsPasswordChange = (state.isEncrypted && state.oldPassword.isNotEmpty()) ||
                                             (!state.isEncrypted && state.newPassword1.isNotEmpty())

                    var newPrivateKey = pubkey.privateKey
                    var newEncrypted = pubkey.encrypted

                    if (needsPasswordChange) {
                        val oldPassword = if (state.isEncrypted) state.oldPassword else ""
                        val newPassword = state.newPassword1

                        val privateKeyData = pubkey.privateKey
                        if (privateKeyData == null) {
                            withContext(dispatchers.main) {
                                _uiState.update { it.copy(wrongPassword = true) }
                            }
                            return@withContext
                        }

                        try {
                            // Decode with old password
                            val privateKeyObj = PubkeyUtils.decodePrivate(
                                privateKeyData,
                                pubkey.type,
                                oldPassword
                            )

                            if (privateKeyObj == null) {
                                withContext(dispatchers.main) {
                                    _uiState.update { it.copy(wrongPassword = true) }
                                }
                                return@withContext
                            }

                            // Re-encode with new password
                            newPrivateKey = PubkeyUtils.getEncodedPrivate(privateKeyObj, newPassword)
                            newEncrypted = newPassword.isNotEmpty()
                        } catch (_: Exception) {
                            withContext(dispatchers.main) {
                                _uiState.update { it.copy(wrongPassword = true) }
                            }
                            return@withContext
                        }
                    }

                    // Create updated pubkey with immutable copy
                    val updatedPubkey = pubkey.copy(
                        nickname = state.nickname,
                        privateKey = newPrivateKey,
                        encrypted = newEncrypted,
                        startup = state.unlockAtStartup && !newEncrypted,
                        confirmation = state.confirmUse,
                        fido2Transport = if (state.isFido2) state.fido2Transport else pubkey.fido2Transport
                    )

                    // Save to database
                    repository.save(updatedPubkey)

                    Timber.d("Public key saved successfully")
                }

                _uiState.update { it.copy(saveSuccess = true) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save pubkey")
                _uiState.update {
                    it.copy(error = "Failed to save: ${e.message}")
                }
            }
        }
    }
}
