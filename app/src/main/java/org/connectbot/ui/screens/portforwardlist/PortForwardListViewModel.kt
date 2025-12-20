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

package org.connectbot.ui.screens.portforwardlist

import android.content.Context
import timber.log.Timber
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.PortForward
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager

data class PortForwardListUiState(
    val portForwards: List<PortForward> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasLiveConnection: Boolean = false
)

@HiltViewModel
class PortForwardListViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: HostRepository
) : ViewModel() {

    companion object {
        private const val TAG = "CB.PortForwardListVM"
    }

    private val hostId: Long = savedStateHandle.get<Long>("hostId") ?: -1L
    private var terminalManager: TerminalManager? = null

    private val _uiState = MutableStateFlow(PortForwardListUiState(isLoading = true))
    val uiState: StateFlow<PortForwardListUiState> = _uiState.asStateFlow()

    init {
        loadPortForwards()
    }

    fun setTerminalManager(manager: TerminalManager) {
        if (terminalManager != manager) {
            terminalManager = manager
            checkLiveConnection()
            // Reload to sync enabled state from active bridge
            loadPortForwards()
        }
    }

    private fun checkLiveConnection() {
        val bridge = findBridgeForHost()
        _uiState.update { it.copy(hasLiveConnection = bridge != null && bridge.transport?.isConnected() == true) }
    }

    private fun findBridgeForHost(): TerminalBridge? {
        return terminalManager?.bridgesFlow?.value?.find { it.host.id == hostId }
    }

    fun loadPortForwards() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val portForwards = repository.getPortForwardsForHost(hostId)

                // Sync enabled state from active bridge if available
                val bridge = findBridgeForHost()
                if (bridge != null && bridge.transport?.isConnected() == true) {
                    val activePfs = bridge.portForwards
                    // Match port forwards by ID and sync enabled state
                    portForwards.forEach { pf ->
                        val activePf = activePfs.find { it.id == pf.id }
                        if (activePf != null) {
                            pf.setEnabled(activePf.isEnabled())
                            pf.setIdentifier(activePf.getIdentifier())
                        }
                    }
                }

                _uiState.update {
                    it.copy(portForwards = portForwards, isLoading = false, error = null)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load port forwards")
                }
            }
        }
    }

    fun addPortForward(nickname: String, type: String, sourcePort: String, destination: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Parse destination in "host:port" format
                    val destSplit = destination.split(":")
                    val destAddr = destSplit.firstOrNull()
                    val destPort = if (destSplit.size > 1) {
                        destSplit.last().toIntOrNull() ?: 0
                    } else {
                        0
                    }

                    val portForward = PortForward(
                        hostId = hostId,
                        nickname = nickname,
                        type = type,
                        sourcePort = sourcePort.toIntOrNull() ?: 0,
                        destAddr = destAddr,
                        destPort = destPort
                    )
                    repository.savePortForward(portForward)
                }
                loadPortForwards()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to add port forward")
                }
            }
        }
    }

    fun updatePortForward(
        portForward: PortForward,
        nickname: String,
        type: String,
        sourcePort: String,
        destination: String
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Parse destination in "host:port" format
                    val destSplit = destination.split(":")
                    val destAddr = destSplit.firstOrNull()
                    val destPort = if (destSplit.size > 1) {
                        destSplit.last().toIntOrNull() ?: 0
                    } else {
                        0
                    }

                    // Create updated port forward with new values
                    val updated = portForward.copy(
                        nickname = nickname,
                        type = type,
                        sourcePort = sourcePort.toIntOrNull() ?: 0,
                        destAddr = destAddr,
                        destPort = destPort
                    )
                    repository.savePortForward(updated)
                }
                loadPortForwards()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to update port forward")
                }
            }
        }
    }

    fun deletePortForward(portForward: PortForward) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.deletePortForward(portForward)
                }
                loadPortForwards()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete port forward")
                }
            }
        }
    }

    fun enablePortForward(portForward: PortForward) {
        viewModelScope.launch {
            try {
                val bridge = findBridgeForHost()
                if (bridge == null) {
                    _uiState.update { it.copy(error = "No active connection for this host") }
                    return@launch
                }

                val success = bridge.portForwards.find { it.id == portForward.id }?.let {
                    withContext(Dispatchers.IO) {
                        bridge.enablePortForward(it)
                    }
                }

                if (success == true) {
                    Timber.d("Port forward ${portForward.nickname} enabled successfully")
                    loadPortForwards() // Reload to update UI
                } else {
                    _uiState.update {
                        it.copy(error = "Failed to enable port forward ${portForward.nickname}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error enabling port forward")
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to enable port forward")
                }
            }
        }
    }

    fun disablePortForward(portForward: PortForward) {
        viewModelScope.launch {
            try {
                val bridge = findBridgeForHost()
                if (bridge == null) {
                    _uiState.update { it.copy(error = "No active connection for this host") }
                    return@launch
                }

                val success = bridge.portForwards.find { it.id == portForward.id }?.let {
                    withContext(Dispatchers.IO) {
                        bridge.disablePortForward(it)
                    }
                }

                if (success == true) {
                    Timber.d("Port forward ${portForward.nickname} disabled successfully")
                    loadPortForwards() // Reload to update UI
                } else {
                    _uiState.update {
                        it.copy(error = "Failed to disable port forward ${portForward.nickname}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error disabling port forward")
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to disable port forward")
                }
            }
        }
    }
}
