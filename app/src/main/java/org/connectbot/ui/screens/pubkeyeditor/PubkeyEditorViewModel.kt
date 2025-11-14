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

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class PubkeyEditorUiState(
    val nickname: String = "",
    val keyType: String = "",
    val isEncrypted: Boolean = false,
    val oldPassword: String = "",
    val newPassword1: String = "",
    val newPassword2: String = "",
    val unlockAtStartup: Boolean = false,
    val confirmUse: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val wrongPassword: Boolean = false
) {
    val passwordMismatch: Boolean
        get() = newPassword1 != newPassword2 && newPassword2.isNotEmpty()

    val willBeEncrypted: Boolean
        get() = newPassword1.isNotEmpty()

    val canSave: Boolean
        get() = nickname.isNotEmpty() && !passwordMismatch && !wrongPassword &&
                !(willBeEncrypted && unlockAtStartup)
}

class PubkeyEditorViewModel(
    private val context: Context,
    private val pubkeyId: Long
) : ViewModel() {
    companion object {
        private const val TAG = "PubkeyEditorViewModel"
    }

    private val repository: PubkeyRepository = PubkeyRepository.get(context)

    private val _uiState = MutableStateFlow(PubkeyEditorUiState())
    val uiState: StateFlow<PubkeyEditorUiState> = _uiState.asStateFlow()

    private var originalPubkey: Pubkey? = null

    init {
        loadPubkey()
    }

    private fun loadPubkey() {
        viewModelScope.launch {
            try {
                val pubkey = withContext(Dispatchers.IO) {
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
                Log.e(TAG, "Failed to load pubkey", e)
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

    fun save() {
        var pubkey = originalPubkey ?: return
        val state = _uiState.value

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
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

                        try {
                            // Decode with old password
                            val privateKeyObj = PubkeyUtils.decodePrivate(
                                pubkey.privateKey,
                                pubkey.type,
                                oldPassword
                            )

                            // Re-encode with new password
                            newPrivateKey = PubkeyUtils.getEncodedPrivate(privateKeyObj, newPassword)
                            newEncrypted = newPassword.isNotEmpty()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
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
                        confirmation = state.confirmUse
                    )

                    // Save to database
                    repository.save(updatedPubkey)

                    Log.d(TAG, "Public key saved successfully")
                }

                _uiState.update { it.copy(saveSuccess = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save pubkey", e)
                _uiState.update {
                    it.copy(error = "Failed to save: ${e.message}")
                }
            }
        }
    }
}
