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

package org.connectbot.ui.screens.pubkeylist

import android.content.ClipData
import android.content.ClipboardManager
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
import org.connectbot.service.TerminalManager
import org.connectbot.util.PubkeyUtils

data class PubkeyListUiState(
    val pubkeys: List<Pubkey> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val loadedKeyNicknames: Set<String> = emptySet()
)

class PubkeyListViewModel(
    private val context: Context,
    private val repository: PubkeyRepository = PubkeyRepository.get(context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(PubkeyListUiState(isLoading = true))
    val uiState: StateFlow<PubkeyListUiState> = _uiState.asStateFlow()

    var terminalManager: TerminalManager? = null
        set(value) {
            field = value
            updateLoadedKeys()
        }

    init {
        loadPubkeys()
    }

    fun loadPubkeys() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val pubkeys = repository.getAll()
                _uiState.update {
                    it.copy(pubkeys = pubkeys, isLoading = false, error = null)
                }
                updateLoadedKeys()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load keys")
                }
            }
        }
    }

    private fun updateLoadedKeys() {
        terminalManager?.let { manager ->
            val loadedKeys = _uiState.value.pubkeys
                .map { it.nickname }
                .filter { manager.isKeyLoaded(it) }
                .toSet()
            _uiState.update { it.copy(loadedKeyNicknames = loadedKeys) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun deletePubkey(pubkey: Pubkey) {
        viewModelScope.launch {
            try {
                terminalManager?.removeKey(pubkey.nickname)
                repository.delete(pubkey)
                loadPubkeys()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete key")
                }
            }
        }
    }

    fun toggleKeyLoaded(pubkey: Pubkey, onPasswordRequired: (Pubkey, (String) -> Unit) -> Unit) {
        val isLoaded = terminalManager?.isKeyLoaded(pubkey.nickname) == true

        if (isLoaded) {
            terminalManager?.removeKey(pubkey.nickname)
            updateLoadedKeys()
        } else {
            if (pubkey.encrypted) {
                onPasswordRequired(pubkey) { password ->
                    loadKeyWithPassword(pubkey, password)
                }
            } else {
                loadKeyWithPassword(pubkey, null)
            }
        }
    }

    private fun loadKeyWithPassword(pubkey: Pubkey, password: String?) {
        viewModelScope.launch {
            try {
                val keyPair = withContext(Dispatchers.Default) {
                    PubkeyUtils.convertToKeyPair(pubkey, password)
                }
                terminalManager?.addKey(pubkey, keyPair, true)
                updateLoadedKeys()
            } catch (e: PubkeyUtils.BadPasswordException) {
                _uiState.update {
                    it.copy(error = "Failed to unlock key: Bad password")
                }
            } catch (e: Exception) {
                Log.e("PubkeyListViewModel", "Failed to load key", e)
                _uiState.update {
                    it.copy(error = "Failed to load key: ${e.message}")
                }
            }
        }
    }

    fun copyPublicKey(pubkey: Pubkey) {
        viewModelScope.launch {
            try {
                val publicKeyString = withContext(Dispatchers.Default) {
                    // Check if this is an imported key
                    val isImported = pubkey.type == "IMPORTED"
                    if (isImported) {
                        throw IllegalArgumentException("Cannot export public key from imported key")
                    }

                    val pk = PubkeyUtils.decodePublic(pubkey.publicKey, pubkey.type)
                    PubkeyUtils.convertToOpenSSHFormat(pk, pubkey.nickname)
                }

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Public Key", publicKeyString)
                clipboard.setPrimaryClip(clip)
            } catch (e: Exception) {
                Log.e("PubkeyListViewModel", "Failed to copy public key", e)
                _uiState.update {
                    it.copy(error = "Failed to copy public key: ${e.message}")
                }
            }
        }
    }

    fun copyPrivateKey(pubkey: Pubkey) {
        viewModelScope.launch {
            try {
                val isImported = pubkey.type == "IMPORTED"

                if (pubkey.encrypted && !isImported) {
                    // Encrypted non-imported keys cannot be exported
                    _uiState.update {
                        it.copy(error = "Cannot copy encrypted private key. Remove password first.")
                    }
                    return@launch
                }

                val privateKeyString = withContext(Dispatchers.Default) {
                    if (isImported) {
                        // For imported keys, just return the raw data
                        String(pubkey.privateKey ?: ByteArray(0))
                    } else {
                        // For non-imported keys, export as PEM
                        val pk = PubkeyUtils.decodePrivate(pubkey.privateKey, pubkey.type)
                        PubkeyUtils.exportPEM(pk, null)
                    }
                }

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Private Key", privateKeyString)
                clipboard.setPrimaryClip(clip)
            } catch (e: Exception) {
                Log.e("PubkeyListViewModel", "Failed to copy private key", e)
                _uiState.update {
                    it.copy(error = "Failed to copy private key: ${e.message}")
                }
            }
        }
    }
}
