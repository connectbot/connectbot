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

package org.connectbot.ui.screens.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.service.BridgeDisconnectedListener
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager

data class ConsoleUiState(
    val bridges: List<TerminalBridge> = emptyList(),
    val currentBridgeIndex: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    // Add a revision counter to force recomposition when bridge state changes
    val revision: Int = 0
)

class ConsoleViewModel(
    private val terminalManager: TerminalManager?,
    private val hostId: Long
) : ViewModel(), BridgeDisconnectedListener {
    private val _uiState = MutableStateFlow(ConsoleUiState())
    val uiState: StateFlow<ConsoleUiState> = _uiState.asStateFlow()

    init {
        // Observe bridges flow from TerminalManager
        viewModelScope.launch {
            terminalManager?.bridgesFlow?.collect { bridges ->
                updateBridges(bridges)
            }
        }

        // First, try to find or create the bridge for this host
        if (hostId != -1L) {
            viewModelScope.launch {
                ensureBridgeExists()
            }
        }
    }

    private suspend fun ensureBridgeExists() {
        withContext(Dispatchers.IO) {
            try {
                val allBridges = terminalManager?.bridgesFlow?.value ?: emptyList()

                // Check if we already have a bridge for this host
                val existingBridge = allBridges.find { bridge ->
                    bridge.host.id == hostId
                }

                // If no bridge exists, create one using the service method
                if (existingBridge == null) {
                    if (hostId < 0L) {
                        // Temporary host - should already exist from MainActivity/URI handling
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Temporary connection not found"
                            )
                        }
                    } else {
                        // Permanent host - create from database
                        val bridge = terminalManager?.openConnectionForHostId(hostId)
                        if (bridge == null) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Failed to open connection: host not found"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to create connection"
                    )
                }
            }
        }
    }

    private fun updateBridges(allBridges: List<TerminalBridge>) {
        // If hostId is provided, try to find the bridge for this specific host
        val filteredBridges = if (hostId != -1L) {
            allBridges.filter { bridge ->
                bridge.host.id == hostId
            }
        } else {
            allBridges
        }

        // Register listeners for any new bridges
        filteredBridges.forEach { bridge ->
            bridge.addOnDisconnectedListener(this@ConsoleViewModel)
        }

        _uiState.update {
            val newBridges = filteredBridges.ifEmpty { allBridges }
            val newIndex = if (it.currentBridgeIndex >= newBridges.size) {
                // Adjust index if it's now out of range
                (newBridges.size - 1).coerceAtLeast(0)
            } else {
                it.currentBridgeIndex
            }

            // Stop loading when we have bridges, or when we're showing all bridges (hostId == -1)
            // If waiting for a specific host, keep loading until that bridge appears
            val shouldStopLoading = if (hostId != -1L) {
                filteredBridges.isNotEmpty()
            } else {
                true // Always stop loading when showing all bridges
            }

            it.copy(
                bridges = newBridges,
                currentBridgeIndex = newIndex,
                isLoading = if (shouldStopLoading) false else it.isLoading,
                error = null
            )
        }
    }

    override fun onDisconnected(bridge: TerminalBridge) {
        // Bridge disconnection is handled automatically via bridgesFlow
        // This callback is kept for backwards compatibility
    }

    override fun onCleared() {
        super.onCleared()
        // Unregister from all bridges when ViewModel is cleared
        _uiState.value.bridges.forEach { bridge ->
            bridge.removeOnDisconnectedListener(this)
        }
    }

    fun selectBridge(index: Int) {
        if (index in _uiState.value.bridges.indices) {
            _uiState.update { it.copy(currentBridgeIndex = index) }
        }
    }

    /**
     * Refresh the UI state to trigger recomposition.
     * This is needed because bridge state (isSessionOpen, isDisconnected, etc.)
     * changes asynchronously but doesn't trigger Compose recomposition automatically.
     */
    fun refreshMenuState() {
        _uiState.update { it.copy(revision = it.revision + 1) }
    }
}
