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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trilead.ssh2.crypto.Base64
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
    val importError: KnownHostImportError? = null,
)

enum class KnownHostImportError {
    INVALID_KEY,
    IMPORT_FAILED,
}

internal data class ParsedHostKey(
    val algorithm: String,
    val keyBlob: ByteArray,
)

internal fun parseOpenSshHostKey(value: String): ParsedHostKey? {
    val parts = value.trim().split(Regex("\\s+"))
    for (algorithmIndex in 0 until parts.lastIndex) {
        val algorithm = parts[algorithmIndex]
        if (!algorithm.startsWith("ssh-") && !algorithm.startsWith("ecdsa-") && !algorithm.startsWith("sk-")) continue

        val keyBlob = try {
            Base64.decode(parts[algorithmIndex + 1].toCharArray())
        } catch (_: Exception) {
            continue
        }
        if (keyBlob.size < 5) continue

        val algorithmLength = ((keyBlob[0].toInt() and 0xff) shl 24) or
            ((keyBlob[1].toInt() and 0xff) shl 16) or
            ((keyBlob[2].toInt() and 0xff) shl 8) or
            (keyBlob[3].toInt() and 0xff)
        if (algorithmLength !in 1..(keyBlob.size - 4)) continue

        val embeddedAlgorithm = keyBlob.copyOfRange(4, 4 + algorithmLength).toString(Charsets.US_ASCII)
        if (embeddedAlgorithm == algorithm) return ParsedHostKey(algorithm, keyBlob)
    }
    return null
}

@HiltViewModel
class KnownHostListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: HostRepository,
    private val dispatchers: CoroutineDispatchers,
) : ViewModel() {
    private val hostId: Long = checkNotNull(savedStateHandle["hostId"])
    private val _uiState = MutableStateFlow(KnownHostListUiState())
    val uiState: StateFlow<KnownHostListUiState> = _uiState.asStateFlow()

    init {
        observeKnownHosts()
    }

    private fun observeKnownHosts() {
        viewModelScope.launch {
            repository.observeKnownHostsForHost(hostId)
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

    fun importKnownHost(value: String): Boolean {
        val parsedKey = parseOpenSshHostKey(value)
        if (parsedKey == null) {
            _uiState.update { it.copy(importError = KnownHostImportError.INVALID_KEY) }
            return false
        }

        _uiState.update { it.copy(importError = null) }
        viewModelScope.launch {
            try {
                withContext(dispatchers.io) {
                    val host = repository.findHostById(hostId)
                        ?: error("Host no longer exists")
                    repository.saveKnownHost(
                        host = host,
                        hostname = host.hostname,
                        port = host.port,
                        serverHostKeyAlgorithm = parsedKey.algorithm,
                        serverHostKey = parsedKey.keyBlob,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(importError = KnownHostImportError.IMPORT_FAILED) }
            }
        }
        return true
    }

    fun clearImportError() {
        _uiState.update { it.copy(importError = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(hasError = false) }
    }
}
