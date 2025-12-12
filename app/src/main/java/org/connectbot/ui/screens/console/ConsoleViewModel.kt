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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.terminal.ProgressState
import javax.inject.Inject

data class ConsoleUiState(
    val bridges: List<TerminalBridge> = emptyList(),
    val currentBridgeIndex: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    // Add a revision counter to force recomposition when bridge state changes
    val revision: Int = 0,
    // Progress state from OSC 9;4 escape sequences
    val progressState: ProgressState? = null,
    val progressValue: Int = 0,
    // Session counts for hosts that have multiple sessions (for tab display)
    val sessionCounts: Map<Long, Int> = emptyMap()
)

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {
    private val hostId: Long = savedStateHandle.get<Long>("hostId") ?: -1L
    private val initialSessionId: Long = savedStateHandle.get<Long>("sessionId") ?: -1L
    private var terminalManager: TerminalManager? = null

    private val _uiState = MutableStateFlow(ConsoleUiState())
    val uiState: StateFlow<ConsoleUiState> = _uiState.asStateFlow()

    fun setTerminalManager(manager: TerminalManager) {
        if (terminalManager != manager) {
            terminalManager = manager
            // Observe bridges flow from TerminalManager
            viewModelScope.launch {
                manager.bridgesFlow.collect { bridges ->
                    updateBridges(bridges)
                    subscribeToActiveBridgeBells(bridges)
                    subscribeToActiveBridgeProgress(bridges)
                }
            }

            // First, try to find or create the bridge for this host
            if (hostId != -1L) {
                viewModelScope.launch {
                    ensureBridgeExists()
                }
            }
        }
    }

    private fun subscribeToActiveBridgeBells(bridges: List<TerminalBridge>) {
        viewModelScope.launch {
            bridges.forEach { bridge ->
                launch {
                    bridge.bellEvents.collect {
                        val currentIndex = _uiState.value.currentBridgeIndex
                        val currentBridge = _uiState.value.bridges.getOrNull(currentIndex)

                        if (currentBridge == bridge) {
                            // The bridge is visible, play the beep
                            terminalManager?.playBeep()
                        } else {
                            // The bridge is not visible, send a notification
                            currentBridge?.host?.let {
                                terminalManager?.sendActivityNotification(it)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun subscribeToActiveBridgeProgress(bridges: List<TerminalBridge>) {
        viewModelScope.launch {
            bridges.forEach { bridge ->
                launch {
                    bridge.progressState.collect { progressInfo ->
                        val currentIndex = _uiState.value.currentBridgeIndex
                        val currentBridge = _uiState.value.bridges.getOrNull(currentIndex)

                        if (currentBridge == bridge) {
                            // Update progress state for the visible bridge
                            _uiState.update {
                                if (progressInfo == null || progressInfo.state == ProgressState.HIDDEN) {
                                    it.copy(progressState = null, progressValue = 0)
                                } else {
                                    it.copy(
                                        progressState = progressInfo.state,
                                        progressValue = progressInfo.progress
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun ensureBridgeExists() {
        withContext(dispatchers.io) {
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

        // Calculate session counts for each host
        val sessionCounts = allBridges.groupBy { it.host.id }
            .mapValues { it.value.size }


        _uiState.update {
            val newBridges = filteredBridges.ifEmpty { allBridges }

            // Determine the initial index
            val newIndex = when {
                // First time loading and we have a specific session to navigate to
                it.bridges.isEmpty() && initialSessionId != -1L -> {
                    newBridges.indexOfFirst { bridge -> bridge.sessionId == initialSessionId }
                        .takeIf { idx -> idx >= 0 } ?: 0
                }
                // First time loading - try to find last-used session
                it.bridges.isEmpty() && hostId != -1L -> {
                    val lastUsed = terminalManager?.getLastUsedBridge(hostId)
                    if (lastUsed != null) {
                        newBridges.indexOfFirst { bridge -> bridge.sessionId == lastUsed.sessionId }
                            .takeIf { idx -> idx >= 0 } ?: 0
                    } else {
                        0
                    }
                }
                // Adjust index if it's now out of range
                it.currentBridgeIndex >= newBridges.size -> {
                    (newBridges.size - 1).coerceAtLeast(0)
                }
                else -> it.currentBridgeIndex
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
                error = null,
                sessionCounts = sessionCounts
            )
        }

        // Mark the current session as used
        val currentBridge = _uiState.value.bridges.getOrNull(_uiState.value.currentBridgeIndex)
        currentBridge?.let { terminalManager?.markSessionUsed(it.sessionId) }
    }

    fun selectBridge(index: Int) {
        if (index in _uiState.value.bridges.indices) {
            _uiState.update { it.copy(currentBridgeIndex = index) }
            // Mark the selected session as used
            val bridge = _uiState.value.bridges.getOrNull(index)
            bridge?.let { terminalManager?.markSessionUsed(it.sessionId) }
        }
    }

    /**
     * Select a bridge by its session ID.
     */
    fun selectBridgeBySessionId(sessionId: Long) {
        val index = _uiState.value.bridges.indexOfFirst { it.sessionId == sessionId }
        if (index >= 0) {
            selectBridge(index)
        }
    }

    /**
     * Get the current bridge (if any).
     */
    fun getCurrentBridge(): TerminalBridge? {
        return _uiState.value.bridges.getOrNull(_uiState.value.currentBridgeIndex)
    }

    /**
     * Open a new session to the current host and navigate to it.
     */
    fun openNewSession() {
        val currentBridge = getCurrentBridge() ?: return
        viewModelScope.launch {
            try {
                val newBridge = withContext(Dispatchers.IO) {
                    terminalManager?.openConnectionForHostId(currentBridge.host.id)
                }
                // Navigate to the new session
                newBridge?.let { bridge ->
                    selectBridgeBySessionId(bridge.sessionId)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to open new session")
                }
            }
        }
    }

    /**
     * Get all sessions for the current host (for switch session menu).
     */
    fun getSessionsForCurrentHost(): List<TerminalBridge> {
        val currentBridge = getCurrentBridge() ?: return emptyList()
        return _uiState.value.bridges.filter { it.host.id == currentBridge.host.id }
    }

    /**
     * Disconnect all sessions for the current host.
     */
    fun disconnectAllSessionsForCurrentHost() {
        val sessions = getSessionsForCurrentHost()
        sessions.forEach { bridge ->
            bridge.dispatchDisconnect(true)
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
