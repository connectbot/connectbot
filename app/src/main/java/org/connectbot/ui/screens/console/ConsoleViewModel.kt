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
        // Start polling for bridges to appear (handles async connection)
        startBridgePolling()
        // Register as a listener for all current bridges
        registerDisconnectListeners()
    }

    private fun startBridgePolling() {
        viewModelScope.launch {
            // First, try to find or create the bridge for this host
            if (hostId != -1L) {
                ensureBridgeExists()
            }

            // Poll for bridges to appear for up to 5 seconds
            var attempts = 0
            while (attempts < 10) {
                loadBridges()
                if (_uiState.value.bridges.isNotEmpty()) {
                    break
                }
                delay(500)
                attempts++
            }
            // After polling, set loading to false if no bridges found
            if (_uiState.value.bridges.isEmpty()) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun ensureBridgeExists() {
        withContext(Dispatchers.IO) {
            try {
                val allBridges = terminalManager?.bridges?.toList() ?: emptyList()

                // Check if we already have a bridge for this host
                val existingBridge = allBridges.find { bridge ->
                    bridge.host.id == hostId
                }

                // If no bridge exists, create one using the service method
                if (existingBridge == null) {
                    terminalManager?.openConnectionForHostId(hostId)
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

    private fun registerDisconnectListeners() {
        terminalManager?.bridges?.forEach { bridge ->
            bridge.addOnDisconnectedListener(this)
        }
    }

    override fun onDisconnected(bridge: TerminalBridge) {
        // Refresh the bridge list when a bridge disconnects
        viewModelScope.launch {
            delay(100) // Small delay to allow TerminalManager cleanup
            loadBridges()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Unregister from all bridges when ViewModel is cleared
        terminalManager?.bridges?.forEach { bridge ->
            bridge.removeOnDisconnectedListener(this)
        }
    }

    private fun loadBridges() {
        viewModelScope.launch {
            try {
                // Get all bridges from TerminalManager
                val allBridges = terminalManager?.bridges?.toList() ?: emptyList()

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
                    it.copy(
                        bridges = newBridges,
                        currentBridgeIndex = newIndex,
                        isLoading = if (filteredBridges.isNotEmpty() || allBridges.isNotEmpty()) false else it.isLoading,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load terminal"
                    )
                }
            }
        }
    }

    fun selectBridge(index: Int) {
        if (index in _uiState.value.bridges.indices) {
            _uiState.update { it.copy(currentBridgeIndex = index) }
        }
    }

    fun refreshBridges() {
        loadBridges()
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
