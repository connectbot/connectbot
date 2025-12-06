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
import org.connectbot.R
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.service.ServiceError
import org.connectbot.service.TerminalManager

enum class ConnectionState {
    UNKNOWN,
    CONNECTED,
    DISCONNECTED
}

data class HostListUiState(
    val hosts: List<Host> = emptyList(),
    val connectionStates: Map<Long, ConnectionState> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sortedByColor: Boolean = false
)

class HostListViewModel(
    private val context: Context,
    private val terminalManager: TerminalManager?,
    private val repository: HostRepository = HostRepository.get(context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(HostListUiState(isLoading = true))
    val uiState: StateFlow<HostListUiState> = _uiState.asStateFlow()

    init {
        loadHosts()
        // Observe host status changes from Flow
        observeHostStatusChanges()
        // Collect service errors from TerminalManager
        collectServiceErrors()
    }

    private fun observeHostStatusChanges() {
        terminalManager?.let { manager ->
            viewModelScope.launch {
                manager.hostStatusChangedFlow.collect {
                    // Update connection states when terminal manager notifies us of changes
                    updateConnectionStates(_uiState.value.hosts)
                }
            }
        }
    }

    private fun collectServiceErrors() {
        terminalManager?.let { manager ->
            viewModelScope.launch {
                manager.serviceErrors.collect { error ->
                    val errorMessage = formatServiceError(error)
                    _uiState.update { it.copy(error = errorMessage) }
                }
            }
        }
    }

    private fun formatServiceError(error: ServiceError): String {
        return when (error) {
            is ServiceError.KeyLoadFailed -> {
                context.getString(R.string.error_key_load_failed, error.keyName, error.reason)
            }
            is ServiceError.ConnectionFailed -> {
                context.getString(
                    R.string.error_connection_failed,
                    error.hostNickname,
                    error.hostname,
                    error.reason
                )
            }
            is ServiceError.PortForwardLoadFailed -> {
                context.getString(
                    R.string.error_port_forward_load_failed,
                    error.hostNickname,
                    error.reason
                )
            }
            is ServiceError.HostSaveFailed -> {
                context.getString(R.string.error_host_save_failed, error.hostNickname, error.reason)
            }
            is ServiceError.ColorSchemeLoadFailed -> {
                context.getString(R.string.error_color_scheme_load_failed, error.reason)
            }
        }
    }


    fun loadHosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val hosts = repository.getHosts(_uiState.value.sortedByColor)
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

    private fun updateConnectionStates(hosts: List<Host>) {
        val states = hosts.associate { host ->
            host.id to getConnectionState(host)
        }
        _uiState.update { it.copy(connectionStates = states) }
    }

    private fun getConnectionState(host: Host): ConnectionState {
        if (terminalManager == null) {
            return ConnectionState.UNKNOWN
        }

        // Check if connected by nickname
        if (terminalManager.getConnectedBridge(host.nickname) != null) {
            return ConnectionState.CONNECTED
        }

        // Check if in disconnected list by comparing nickname
        if (terminalManager.disconnected.any { it.nickname == host.nickname }) {
            return ConnectionState.DISCONNECTED
        }

        return ConnectionState.UNKNOWN
    }

    fun toggleSortOrder() {
        _uiState.update { it.copy(sortedByColor = !it.sortedByColor) }
        loadHosts()
    }

    fun connectToHost(host: Host) {
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

    fun deleteHost(host: Host) {
        viewModelScope.launch {
            try {
                repository.deleteHost(host)
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

    fun disconnectHost(host: Host) {
        terminalManager?.getConnectedBridge(host.nickname)?.dispatchDisconnect(true)
    }

    fun quickConnect(uri: String) {
        viewModelScope.launch {
            try {
                val host = withContext(Dispatchers.IO) {
                    // Parse URI and create a temporary host with defaults from Host entity
                    val parsedUri = android.net.Uri.parse(uri)
                    Host(
                        protocol = parsedUri.scheme ?: "ssh",
                        hostname = parsedUri.host ?: "",
                        port = if (parsedUri.port > 0) parsedUri.port else 22,
                        username = parsedUri.userInfo ?: "",
                        nickname = "${parsedUri.userInfo}@${parsedUri.host}:${if (parsedUri.port > 0) parsedUri.port else 22}",
                        lastConnect = System.currentTimeMillis()
                    )
                }
                connectToHost(host)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Invalid URI")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
