/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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

package org.connectbot.ui.screens.knownhostlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trilead.ssh2.crypto.fingerprint.KeyFingerprint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.KnownHost
import org.connectbot.di.CoroutineDispatchers
import javax.inject.Inject

data class KnownHostListItem(
    val knownHost: KnownHost,
    val fingerprint: String,
) {
    val endpoint: String
        get() = "${knownHost.hostname}:${knownHost.port}"
}

data class KnownHostListUiState(
    val knownHosts: List<KnownHostListItem> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
)

@HiltViewModel
class KnownHostListViewModel @Inject constructor(
    private val repository: HostRepository,
    private val dispatchers: CoroutineDispatchers,
) : ViewModel() {
    private val _uiState = MutableStateFlow(KnownHostListUiState())
    val uiState: StateFlow<KnownHostListUiState> = _uiState.asStateFlow()

    init {
        observeKnownHosts()
    }

    private fun observeKnownHosts() {
        viewModelScope.launch {
            repository.observeKnownHosts()
                .map { knownHosts ->
                    knownHosts.map { knownHost ->
                        KnownHostListItem(
                            knownHost = knownHost,
                            fingerprint = KeyFingerprint.createSHA256Fingerprint(knownHost.hostKey),
                        )
                    }
                }
                .flowOn(dispatchers.default)
                .catch {
                    _uiState.update { state ->
                        state.copy(isLoading = false, hasError = true)
                    }
                }
                .collect { knownHosts ->
                    _uiState.update {
                        it.copy(knownHosts = knownHosts, isLoading = false, hasError = false)
                    }
                }
        }
    }

    fun deleteKnownHost(knownHost: KnownHost) {
        viewModelScope.launch {
            try {
                withContext(dispatchers.io) {
                    repository.deleteKnownHost(knownHost)
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(hasError = true) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(hasError = false) }
    }
}
