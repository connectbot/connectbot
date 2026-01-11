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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.PortForward
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.util.HostConstants
import timber.log.Timber
import javax.inject.Inject

data class PortForwardListUiState(
    val portForwards: List<PortForward> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasLiveConnection: Boolean = false
)

@HiltViewModel
class PortForwardListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: HostRepository,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {
    private val hostId: Long = savedStateHandle.get<Long>("hostId") ?: -1L
    private val _terminalManager = MutableStateFlow<TerminalManager?>(null)
    private val _refreshTrigger = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(PortForwardListUiState(isLoading = true))
    val uiState: StateFlow<PortForwardListUiState> = _uiState.asStateFlow()

    init {
        // Observe port forwards from the repository and combine with bridge state from TerminalManager
        viewModelScope.launch {
            combine(
                repository.observePortForwardsForHost(hostId),
                _terminalManager,
                _refreshTrigger
            ) { portForwards, terminalManager, _ ->
                try {
                    // Check TerminalManager's bridges to see if there's an active connection for this host
                    val bridge = terminalManager?.bridgesFlow?.value?.find { it.host.id == hostId }
                    val hasLiveConnection = bridge != null && bridge.transport?.isConnected() == true

                    // Create new PortForward copies to ensure StateFlow detects changes
                    // (using toList() alone isn't enough - we need new object instances)
                    val updatedPortForwards = portForwards.map { pf ->
                        val copy = pf.copy()

                        if (hasLiveConnection) {
                            val bridgePf = bridge.portForwards.find { it.id == pf.id }
                            if (bridgePf != null) {
                                copy.setEnabled(bridgePf.isEnabled())
                                copy.setIdentifier(bridgePf.getIdentifier())
                            } else {
                                // Port forward exists in DB but not loaded in bridge
                                copy.setEnabled(false)
                                copy.setIdentifier(null)
                            }
                        } else {
                            // No active connection - mark all as disabled
                            copy.setEnabled(false)
                            copy.setIdentifier(null)
                        }

                        copy
                    }

                    PortForwardListUiState(
                        portForwards = updatedPortForwards,
                        isLoading = false,
                        error = null,
                        hasLiveConnection = hasLiveConnection
                    )
                } catch (e: Exception) {
                    PortForwardListUiState(
                        portForwards = emptyList(),
                        isLoading = false,
                        error = e.message ?: "Failed to load port forwards",
                        hasLiveConnection = false
                    )
                }
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun setTerminalManager(manager: TerminalManager) {
        _terminalManager.value = manager
    }

    fun addPortForward(nickname: String, type: String, sourcePort: String, destination: String) {
        viewModelScope.launch {
            try {
                val srcPort = validatePort(sourcePort, "source")
                val parsed = if (type == HostConstants.PORTFORWARD_DYNAMIC5) {
                    ParsedDestination(null, 0)
                } else {
                    parseDestination(destination)
                }

                val newPortForward = withContext(dispatchers.io) {
                    val portForward = PortForward(
                        hostId = hostId,
                        nickname = nickname,
                        type = type,
                        sourcePort = srcPort,
                        destAddr = parsed.address,
                        destPort = parsed.port
                    )
                    repository.savePortForward(portForward)
                }

                withActiveBridge { bridge ->
                    bridge.transport?.let {
                        it.addPortForward(newPortForward)
                        it.enablePortForward(newPortForward)
                    }

                    Timber.d("Added port forward ${newPortForward.nickname} to active connection")
                }
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
                val srcPort = validatePort(sourcePort, "source")
                val parsed = if (type == HostConstants.PORTFORWARD_DYNAMIC5) {
                    ParsedDestination(null, 0)
                } else {
                    parseDestination(destination)
                }

                val updated = withContext(dispatchers.io) {
                    val updatedPf = portForward.copy(
                        nickname = nickname,
                        type = type,
                        sourcePort = srcPort,
                        destAddr = parsed.address,
                        destPort = parsed.port
                    )
                    repository.savePortForward(updatedPf)
                    updatedPf
                }

                withActiveBridge { bridge ->
                    val oldPf = bridge.portForwards.find { it.id == portForward.id }
                    if (oldPf != null) {
                        val shouldEnable = oldPf.isEnabled()
                        bridge.transport?.let {
                            it.removePortForward(oldPf)
                            it.addPortForward(updated)
                            if (shouldEnable) {
                                it.enablePortForward(updated)
                            }
                            Timber.d("Updated port forward ${updated.nickname} in active connection")
                        }
                        _refreshTrigger.value += 1
                    }
                }
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
                withContext(dispatchers.io) {
                    repository.deletePortForward(portForward)
                }

                withActiveBridge { bridge ->
                    val bridgePf = bridge.portForwards.find { it.id == portForward.id }
                    if (bridgePf != null) {
                        bridge.transport?.removePortForward(bridgePf)
                        Timber.d("Removed port forward ${portForward.nickname} from active connection")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete port forward")
                }
            }
        }
    }

    fun enablePortForward(portForward: PortForward) {
        enableDisablePortForward(portForward, enable = true)
    }

    fun disablePortForward(portForward: PortForward) {
        enableDisablePortForward(portForward, enable = false)
    }

    private fun enableDisablePortForward(portForward: PortForward, enable: Boolean) {
        viewModelScope.launch {
            try {
                val bridge = findBridgeForHost()
                if (bridge == null) {
                    _uiState.update { it.copy(error = "No active connection for this host") }
                    return@launch
                }

                val bridgePortForward = bridge.portForwards.find { it.id == portForward.id }
                if (bridgePortForward == null) {
                    _uiState.update {
                        it.copy(error = "Port forward ${portForward.nickname} not found in active connection")
                    }
                    return@launch
                }

                val success = withContext(dispatchers.io) {
                    if (enable) {
                        bridge.enablePortForward(bridgePortForward)
                    } else {
                        bridge.disablePortForward(bridgePortForward)
                    }
                }

                if (success) {
                    val action = if (enable) "enabled" else "disabled"
                    Timber.d("Port forward ${portForward.nickname} $action successfully")

                    // Trigger the combine Flow to re-emit and pick up the updated state from the bridge
                    _refreshTrigger.value += 1
                } else {
                    val action = if (enable) "enable" else "disable"
                    _uiState.update {
                        it.copy(error = "Failed to $action port forward ${portForward.nickname}")
                    }
                }
            } catch (e: Exception) {
                val action = if (enable) "enabling" else "disabling"
                Timber.e(e, "Error $action port forward")
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to ${if (enable) "enable" else "disable"} port forward")
                }
            }
        }
    }

    private fun findBridgeForHost(): TerminalBridge? = _terminalManager.value?.bridgesFlow?.value?.find { it.host.id == hostId }

    private fun validatePort(portString: String, portType: String): Int {
        val port = portString.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid $portType port: '$portString' is not a valid number")

        if (port !in 1..65535) {
            throw IllegalArgumentException("Invalid $portType port: $port must be between 1 and 65535")
        }

        return port
    }

    private data class ParsedDestination(val address: String?, val port: Int)

    private fun parseDestination(destination: String): ParsedDestination {
        val destSplit = destination.split(":")
        val destAddr = destSplit.firstOrNull()

        val destPort = if (destSplit.size > 1) {
            validatePort(destSplit.last(), "destination")
        } else {
            throw IllegalArgumentException("Destination must include a port (format: host:port)")
        }

        return ParsedDestination(destAddr, destPort)
    }

    private suspend fun withActiveBridge(action: suspend (TerminalBridge) -> Unit) {
        val bridge = findBridgeForHost()
        if (bridge != null && bridge.transport?.isConnected() == true) {
            withContext(dispatchers.io) {
                action(bridge)
            }
        }
    }
}
