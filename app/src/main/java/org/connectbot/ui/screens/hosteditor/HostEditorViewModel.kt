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

package org.connectbot.ui.screens.hosteditor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.data.HostRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Pubkey

data class HostEditorUiState(
    val hostId: Long = -1L,
    val quickConnect: String = "",
    val nickname: String = "",
    val protocol: String = "ssh",
    val username: String = "",
    val hostname: String = "",
    val port: String = "22",
    val color: String = "gray",
    val fontSize: Int = 10,
    val pubkeyId: Long = -1L,
    val availablePubkeys: List<Pubkey> = emptyList(),
    val delKey: String = "del",
    val encoding: String = "UTF-8",
    val useAuthAgent: String = "no",
    val compression: Boolean = false,
    val wantSession: Boolean = true,
    val stayConnected: Boolean = false,
    val quickDisconnect: Boolean = false,
    val postLogin: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class HostEditorViewModel(
    private val context: Context,
    private val hostId: Long,
    private val repository: HostRepository = HostRepository.get(context),
    private val pubkeyRepository: PubkeyRepository = PubkeyRepository.get(context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(HostEditorUiState(hostId = hostId))
    val uiState: StateFlow<HostEditorUiState> = _uiState.asStateFlow()

    init {
        loadPubkeys()
        if (hostId != -1L) {
            loadHost()
        }
    }

    private fun loadPubkeys() {
        viewModelScope.launch {
            try {
                val pubkeys = pubkeyRepository.getAll()
                _uiState.update { it.copy(availablePubkeys = pubkeys) }
            } catch (e: Exception) {
                // Don't fail the whole screen if pubkeys can't be loaded
                _uiState.update { it.copy(availablePubkeys = emptyList()) }
            }
        }
    }

    private fun loadHost() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val host = repository.findHostById(hostId)
                if (host != null) {
                    _uiState.update {
                        it.copy(
                            nickname = host.nickname,
                            protocol = host.protocol,
                            username = host.username,
                            hostname = host.hostname,
                            port = host.port.toString(),
                            color = host.color ?: "gray",
                            fontSize = host.fontSize,
                            pubkeyId = host.pubkeyId,
                            delKey = host.delKey,
                            encoding = host.encoding,
                            useAuthAgent = host.useAuthAgent ?: "no",
                            compression = host.compression,
                            wantSession = host.wantSession,
                            stayConnected = host.stayConnected,
                            quickDisconnect = host.quickDisconnect,
                            postLogin = host.postLogin ?: "",
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Host not found")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load host")
                }
            }
        }
    }

    fun updateNickname(value: String) {
        _uiState.update { it.copy(nickname = value) }
    }

    fun updateProtocol(value: String) {
        _uiState.update { it.copy(protocol = value) }
    }

    fun updateUsername(value: String) {
        _uiState.update { it.copy(username = value) }
    }

    fun updateHostname(value: String) {
        _uiState.update { it.copy(hostname = value) }
    }

    fun updatePort(value: String) {
        // Only allow numeric input
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.update { it.copy(port = value) }
        }
    }

    fun updateQuickConnect(value: String) {
        _uiState.update { it.copy(quickConnect = value) }

        // Parse quick connect string: [user@]hostname[:port]
        val regex = Regex("^(?:([^@]+)@)?([^:]+)(?::(\\d+))?$")
        val match = regex.find(value)

        if (match != null) {
            val (username, hostname, port) = match.destructured
            _uiState.update {
                it.copy(
                    username = username.ifBlank { "" },
                    hostname = hostname,
                    port = port.ifBlank { "22" }
                )
            }
        }
    }

    fun updateColor(value: String) {
        _uiState.update { it.copy(color = value) }
    }

    fun updateFontSize(value: Int) {
        _uiState.update { it.copy(fontSize = value) }
    }

    fun updatePubkeyId(value: Long) {
        _uiState.update { it.copy(pubkeyId = value) }
    }

    fun updateDelKey(value: String) {
        _uiState.update { it.copy(delKey = value) }
    }

    fun updateEncoding(value: String) {
        _uiState.update { it.copy(encoding = value) }
    }

    fun updateUseAuthAgent(value: String) {
        _uiState.update { it.copy(useAuthAgent = value) }
    }

    fun updateCompression(value: Boolean) {
        _uiState.update { it.copy(compression = value) }
    }

    fun updateWantSession(value: Boolean) {
        _uiState.update { it.copy(wantSession = value) }
    }

    fun updateStayConnected(value: Boolean) {
        _uiState.update { it.copy(stayConnected = value) }
    }

    fun updateQuickDisconnect(value: Boolean) {
        _uiState.update { it.copy(quickDisconnect = value) }
    }

    fun updatePostLogin(value: String) {
        _uiState.update { it.copy(postLogin = value) }
    }

    fun saveHost(useExpandedMode: Boolean) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val existingHost = if (hostId != -1L) {
                    repository.findHostById(hostId)
                } else {
                    null
                }

                // In quick connect mode, use the quickConnect string as the nickname
                val nickname = if (!useExpandedMode && state.quickConnect.isNotBlank()) {
                    state.quickConnect
                } else {
                    state.nickname
                }

                val host = Host(
                    id = existingHost?.id ?: 0L,
                    nickname = nickname,
                    protocol = state.protocol,
                    username = state.username,
                    hostname = state.hostname,
                    port = state.port.toIntOrNull() ?: 22,
                    color = state.color.takeIf { it != "gray" },
                    fontSize = state.fontSize,
                    pubkeyId = state.pubkeyId,
                    delKey = state.delKey,
                    encoding = state.encoding,
                    useAuthAgent = state.useAuthAgent.takeIf { it != "no" },
                    compression = state.compression,
                    wantSession = state.wantSession,
                    stayConnected = state.stayConnected,
                    quickDisconnect = state.quickDisconnect,
                    postLogin = state.postLogin.ifBlank { null },
                    lastConnect = existingHost?.lastConnect ?: System.currentTimeMillis(),
                    hostKeyAlgo = existingHost?.hostKeyAlgo,
                    useKeys = existingHost?.useKeys ?: true,
                    colorSchemeId = existingHost?.colorSchemeId ?: 1L,
                    scrollbackLines = existingHost?.scrollbackLines ?: 140,
                    useCtrlAltAsMetaKey = existingHost?.useCtrlAltAsMetaKey ?: false
                )

                repository.saveHost(host)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to save host")
                }
            }
        }
    }
}
