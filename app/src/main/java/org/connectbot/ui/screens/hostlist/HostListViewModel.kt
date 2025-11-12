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

package org.connectbot.ui.screens.hostlist

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
import org.connectbot.service.OnHostStatusChangedListener
import org.connectbot.service.TerminalManager
import org.connectbot.util.HostDatabase

enum class ConnectionState {
    UNKNOWN,
    CONNECTED,
    DISCONNECTED
}

data class HostListUiState(
    val hosts: List<HostBean> = emptyList(),
    val connectionStates: Map<Long, ConnectionState> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sortedByColor: Boolean = false
)

class HostListViewModel(
    private val context: Context,
    private val terminalManager: TerminalManager?
) : ViewModel(), OnHostStatusChangedListener {
    private val database: HostDatabase = HostDatabase.get(context)

    private val _uiState = MutableStateFlow(HostListUiState(isLoading = true))
    val uiState: StateFlow<HostListUiState> = _uiState.asStateFlow()

    init {
        loadHosts()
        // Register listener to get notified when host status changes
        terminalManager?.registerOnHostStatusChangedListener(this)
    }

    override fun onCleared() {
        super.onCleared()
        // Unregister listener when ViewModel is cleared
        terminalManager?.unregisterOnHostStatusChangedListener(this)
    }

    override fun onHostStatusChanged() {
        // Update connection states when terminal manager notifies us of changes
        updateConnectionStates(_uiState.value.hosts)
    }

    fun loadHosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val hosts = withContext(Dispatchers.IO) {
                    database.getHosts(_uiState.value.sortedByColor)
                }
                updateConnectionStates(hosts)
                _uiState.update {
                    it.copy(hosts = hosts, isLoading = false, error = null)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load hosts")
                }
            }
        }
    }

    private fun updateConnectionStates(hosts: List<HostBean>) {
        val states = hosts.associate { host ->
            host.id to getConnectionState(host)
        }
        _uiState.update { it.copy(connectionStates = states) }
    }

    private fun getConnectionState(host: HostBean): ConnectionState {
        if (terminalManager == null) {
            return ConnectionState.UNKNOWN
        }

        if (terminalManager.getConnectedBridge(host) != null) {
            return ConnectionState.CONNECTED
        }

        if (terminalManager.disconnected.contains(host)) {
            return ConnectionState.DISCONNECTED
        }

        return ConnectionState.UNKNOWN
    }

    fun toggleSortOrder() {
        _uiState.update { it.copy(sortedByColor = !it.sortedByColor) }
        loadHosts()
    }

    fun connectToHost(host: HostBean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Build URI from host
                    val uri = android.net.Uri.Builder()
                        .scheme(host.protocol)
                        .encodedAuthority("${host.username}@${host.hostname}:${host.port}")
                        .build()
                    terminalManager?.openConnection(uri)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to connect")
                }
            }
        }
    }

    fun deleteHost(host: HostBean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.deleteHost(host)
                }
                loadHosts()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete host")
                }
            }
        }
    }

    fun disconnectAll() {
        terminalManager?.disconnectAll(true, false)
    }

    fun quickConnect(uri: String) {
        viewModelScope.launch {
            try {
                val hostBean = withContext(Dispatchers.IO) {
                    HostBean().apply {
                        // Parse URI and populate host
                        val parsedUri = android.net.Uri.parse(uri)
                        protocol = parsedUri.scheme ?: "ssh"
                        hostname = parsedUri.host
                        port = if (parsedUri.port > 0) parsedUri.port else 22
                        username = parsedUri.userInfo
                        nickname = "$username@$hostname:$port"
                    }
                }
                connectToHost(hostBean)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Invalid URI")
                }
            }
        }
    }
}
