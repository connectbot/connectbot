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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.bean.HostBean
import org.connectbot.bean.PubkeyBean
import org.connectbot.util.HostDatabase
import org.connectbot.util.PubkeyDatabase

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
    val availablePubkeys: List<PubkeyBean> = emptyList(),
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
    private val hostId: Long
) : ViewModel() {
    private val database: HostDatabase = HostDatabase.get(context)
    private val pubkeyDatabase: PubkeyDatabase = PubkeyDatabase.get(context)

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
                val pubkeys = withContext(Dispatchers.IO) {
                    pubkeyDatabase.allPubkeys()
                }
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
                val host = withContext(Dispatchers.IO) {
                    database.findHostById(hostId)
                }
                if (host != null) {
                    _uiState.update {
                        it.copy(
                            nickname = host.nickname ?: "",
                            protocol = host.protocol ?: "ssh",
                            username = host.username ?: "",
                            hostname = host.hostname ?: "",
                            port = host.port.toString(),
                            color = host.color ?: "gray",
                            fontSize = host.fontSize,
                            pubkeyId = host.pubkeyId,
                            delKey = host.delKey ?: "del",
                            encoding = host.encoding ?: "UTF-8",
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
                withContext(Dispatchers.IO) {
                    val state = _uiState.value
                    val host = if (hostId != -1L) {
                        database.findHostById(hostId) ?: HostBean()
                    } else {
                        HostBean()
                    }

                    // In quick connect mode, use the quickConnect string as the nickname
                    if (!useExpandedMode && state.quickConnect.isNotBlank()) {
                        host.nickname = state.quickConnect
                    } else {
                        host.nickname = state.nickname.ifBlank { null }
                    }

                    host.protocol = state.protocol
                    host.username = state.username
                    host.hostname = state.hostname
                    host.port = state.port.toIntOrNull() ?: 22
                    host.color = state.color
                    host.fontSize = state.fontSize
                    host.pubkeyId = state.pubkeyId
                    host.delKey = state.delKey
                    host.encoding = state.encoding
                    host.useAuthAgent = state.useAuthAgent
                    host.compression = state.compression
                    host.wantSession = state.wantSession
                    host.stayConnected = state.stayConnected
                    host.quickDisconnect = state.quickDisconnect
                    host.postLogin = state.postLogin.ifBlank { null }

                    database.saveHost(host)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to save host")
                }
            }
        }
    }
}
