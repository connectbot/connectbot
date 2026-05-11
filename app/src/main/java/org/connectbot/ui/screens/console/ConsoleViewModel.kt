/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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

import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.terminal.ProgressState
import org.connectbot.util.NotificationPermissionHelper
import org.connectbot.util.PreferenceConstants
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
)

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val dispatchers: CoroutineDispatchers,
    private val prefs: SharedPreferences,
    private val notificationPermissionHelper: NotificationPermissionHelper,
) : ViewModel() {
    private val hostId: Long = savedStateHandle.get<Long>("hostId") ?: -1L
    private var terminalManager: TerminalManager? = null
    private var pendingInitialHostId: Long? = hostId.takeIf { it != -1L }
    private var selectedHostId: Long? = null

    private val bellJobs = mutableMapOf<Long, Job>()
    private val progressJobs = mutableMapOf<Long, Job>()
    private val networkStatusJobs = mutableMapOf<Long, Job>()

    private val _uiState = MutableStateFlow(ConsoleUiState())
    val uiState: StateFlow<ConsoleUiState> = _uiState.asStateFlow()

    private val _networkStatusMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val networkStatusMessages: SharedFlow<String> = _networkStatusMessages.asSharedFlow()

    fun shouldShowNotificationWarning(): Boolean {
        if (!prefs.contains(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED)) return false
        val connPersist = prefs.getBoolean(PreferenceConstants.CONNECTION_PERSIST, true)
        return !connPersist || !notificationPermissionHelper.isGranted()
    }

    fun setTerminalManager(manager: TerminalManager) {
        if (terminalManager != manager) {
            terminalManager = manager

            viewModelScope.launch {
                manager.bridgesFlow.collect { bridges ->
                    updateBridges(bridges)
                    syncBridgeBellSubscriptions(bridges)
                    syncBridgeProgressSubscriptions(bridges)
                    syncBridgeNetworkStatusSubscriptions(bridges)
                }
            }

            viewModelScope.launch {
                manager.hostStatusChangedFlow.collect {
                    _uiState.update { it.copy(revision = it.revision + 1) }
                }
            }

            if (hostId != -1L) {
                viewModelScope.launch {
                    ensureBridgeExists()
                }
            }
        }
    }

    private fun syncBridgeBellSubscriptions(bridges: List<TerminalBridge>) {
        syncBridgeJobs(bridges, bellJobs) { bridge ->
            viewModelScope.launch {
                bridge.bellEvents.collect {
                    val currentBridge = _uiState.value.bridges.getOrNull(_uiState.value.currentBridgeIndex)

                    if (currentBridge == bridge) {
                        terminalManager?.playBeep()
                    } else {
                        terminalManager?.sendActivityNotification(bridge.host)
                    }
                }
            }
        }
    }

    private fun syncBridgeProgressSubscriptions(bridges: List<TerminalBridge>) {
        syncBridgeJobs(bridges, progressJobs) { bridge ->
            viewModelScope.launch {
                bridge.progressState.collect { progressInfo ->
                    val currentBridge = _uiState.value.bridges.getOrNull(_uiState.value.currentBridgeIndex)
                    if (currentBridge == bridge) {
                        updateProgressState(progressInfo)
                    }
                }
            }
        }
    }

    private fun syncBridgeNetworkStatusSubscriptions(bridges: List<TerminalBridge>) {
        syncBridgeJobs(bridges, networkStatusJobs) { bridge ->
            viewModelScope.launch {
                bridge.networkStatusMessages.collect { message ->
                    val currentBridge = _uiState.value.bridges.getOrNull(_uiState.value.currentBridgeIndex)
                    if (currentBridge == bridge) {
                        _networkStatusMessages.emit(message)
                    }
                }
            }
        }
    }

    private fun syncBridgeJobs(
        bridges: List<TerminalBridge>,
        jobs: MutableMap<Long, Job>,
        createJob: (TerminalBridge) -> Job,
    ) {
        val activeHostIds = bridges.map { it.host.id }.toSet()

        val removedIds = jobs.keys.filter { it !in activeHostIds }
        removedIds.forEach { hostId ->
            jobs.remove(hostId)?.cancel()
        }

        bridges.forEach { bridge ->
            jobs.getOrPut(bridge.host.id) {
                createJob(bridge)
            }
        }
    }

    private fun updateProgressState(progressInfo: TerminalBridge.ProgressInfo?) {
        _uiState.update {
            if (progressInfo == null || progressInfo.state == ProgressState.HIDDEN) {
                it.copy(progressState = null, progressValue = 0)
            } else {
                it.copy(
                    progressState = progressInfo.state,
                    progressValue = progressInfo.progress,
                )
            }
        }
    }

    private fun updateCurrentBridgeProgress(
        bridges: List<TerminalBridge> = _uiState.value.bridges,
        currentIndex: Int = _uiState.value.currentBridgeIndex,
    ) {
        val progressInfo = bridges.getOrNull(currentIndex)?.progressState?.value
        updateProgressState(progressInfo)
    }

    private fun findBridgeIndex(bridges: List<TerminalBridge>, bridgeHostId: Long?): Int {
        if (bridgeHostId == null) {
            return -1
        }

        return bridges.indexOfFirst { bridge ->
            bridge.host.id == bridgeHostId
        }
    }

    private fun stepBridgeSelection(offset: Int) {
        val bridges = _uiState.value.bridges
        if (bridges.isEmpty()) {
            return
        }

        val newIndex = (_uiState.value.currentBridgeIndex + offset).coerceIn(0, bridges.lastIndex)
        if (newIndex != _uiState.value.currentBridgeIndex) {
            selectBridge(newIndex)
        }
    }

    fun selectNextBridge() {
        stepBridgeSelection(1)
    }

    fun selectPreviousBridge() {
        stepBridgeSelection(-1)
    }

    private fun handleInitialSelectionError(message: String) {
        pendingInitialHostId = null
        _uiState.update {
            it.copy(
                isLoading = false,
                error = message,
            )
        }
    }

    private suspend fun ensureBridgeExists() {
        withContext(dispatchers.io) {
            try {
                val allBridges = terminalManager?.bridgesFlow?.value ?: emptyList()
                val existingBridge = allBridges.find { bridge ->
                    bridge.host.id == hostId
                }

                if (existingBridge == null) {
                    if (hostId < 0L) {
                        handleInitialSelectionError("Temporary connection not found")
                    } else {
                        val bridge = terminalManager?.openConnectionForHostId(hostId)
                        if (bridge == null) {
                            handleInitialSelectionError("Failed to open connection: host not found")
                        }
                    }
                }
            } catch (e: Exception) {
                handleInitialSelectionError(e.message ?: "Failed to create connection")
            }
        }
    }

    private fun updateBridges(allBridges: List<TerminalBridge>) {
        val currentState = _uiState.value
        val selectedIndex = findBridgeIndex(allBridges, selectedHostId)
        val requestedIndex = findBridgeIndex(allBridges, pendingInitialHostId)
        val requestedBridgeAvailable = requestedIndex != -1

        val newIndex = when {
            requestedBridgeAvailable -> requestedIndex
            selectedIndex != -1 -> selectedIndex
            currentState.currentBridgeIndex >= allBridges.size -> (allBridges.size - 1).coerceAtLeast(0)
            else -> currentState.currentBridgeIndex
        }

        if (requestedBridgeAvailable) {
            pendingInitialHostId = null
            selectedHostId = allBridges.getOrNull(newIndex)?.host?.id
        } else if (pendingInitialHostId == null) {
            selectedHostId = allBridges.getOrNull(newIndex)?.host?.id
        }

        val waitingForRequestedHost = pendingInitialHostId != null

        _uiState.update {
            it.copy(
                bridges = allBridges,
                currentBridgeIndex = newIndex,
                isLoading = waitingForRequestedHost,
                error = null,
            )
        }

        updateCurrentBridgeProgress(allBridges, newIndex)
    }

    fun selectBridge(index: Int) {
        if (index in _uiState.value.bridges.indices) {
            selectedHostId = _uiState.value.bridges[index].host.id
            _uiState.update { it.copy(currentBridgeIndex = index) }
            updateCurrentBridgeProgress()
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

    /**
     * Request a reconnection for the given bridge.
     */
    fun reconnect(bridge: TerminalBridge) {
        terminalManager?.requestReconnect(bridge)
        _uiState.update { it.copy(revision = it.revision + 1) }
    }
}
