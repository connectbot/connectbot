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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.bean.PubkeyBean
import org.connectbot.util.PubkeyDatabase

data class PubkeyListUiState(
    val pubkeys: List<PubkeyBean> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PubkeyListViewModel(
    private val context: Context
) : ViewModel() {
    private val database: PubkeyDatabase = PubkeyDatabase.get(context)

    private val _uiState = MutableStateFlow(PubkeyListUiState(isLoading = true))
    val uiState: StateFlow<PubkeyListUiState> = _uiState.asStateFlow()

    init {
        loadPubkeys()
    }

    fun loadPubkeys() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val pubkeys = withContext(Dispatchers.IO) {
                    database.allPubkeys()
                }
                _uiState.update {
                    it.copy(pubkeys = pubkeys, isLoading = false, error = null)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load keys")
                }
            }
        }
    }

    fun deletePubkey(pubkey: PubkeyBean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.deletePubkey(pubkey)
                }
                loadPubkeys()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete key")
                }
            }
        }
    }
}
