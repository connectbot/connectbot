/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.PortForward
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalManager
import org.connectbot.util.HostConstants
import javax.inject.Inject

data class PortForwardEditorUiState(
    val forwardId: Long = 0L,
    val nickname: String = "",
    val type: String = HostConstants.PORTFORWARD_LOCAL,
    val sourcePort: String = "",
    val sourceAddressOption: SourceAddressOption = SourceAddressOption.LOCALHOST,
    val specificAddress: String = "",
    val destination: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val error: String? = null,
) {
    val isRemote: Boolean get() = type == HostConstants.PORTFORWARD_REMOTE
    val needsDestination: Boolean get() = type != HostConstants.PORTFORWARD_DYNAMIC5
    val isSourcePortValid: Boolean get() = sourcePort.ifEmpty { "8080" }.toIntOrNull()?.let { it in 1..65535 } == true
    val isDestinationValid: Boolean
        get() {
            if (!needsDestination) return true
            val parts = destination.ifEmpty { "localhost:80" }.split(":")
            return parts.size == 2 && parts[0].isNotBlank() && parts[1].toIntOrNull()?.let { it in 1..65535 } == true
        }

    val canSave: Boolean
        get() {
            if (!isSourcePortValid || !isDestinationValid) return false
            if (isRemote && sourceAddressOption == SourceAddressOption.SPECIFIC && specificAddress.isBlank()) return false
            return true
        }
}

@HiltViewModel
class PortForwardEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: HostRepository,
    private val dispatchers: CoroutineDispatchers,
) : ViewModel() {
    private val hostId: Long = savedStateHandle.get<Long>("hostId") ?: -1L
    private val forwardId: Long = savedStateHandle.get<Long>("forwardId") ?: 0L
    private var original: PortForward? = null
    private var terminalManager: TerminalManager? = null

    private val _uiState = MutableStateFlow(PortForwardEditorUiState(forwardId = forwardId))
    val uiState = _uiState.asStateFlow()

    init {
        if (forwardId == 0L) {
            _uiState.update { it.copy(isLoading = false, hasUnsavedChanges = true) }
        } else {
            viewModelScope.launch {
                try {
                    val forward = withContext(dispatchers.io) { repository.getPortForwardById(forwardId) }
                    if (forward == null || forward.hostId != hostId) {
                        _uiState.update { it.copy(isLoading = false, error = "Port forward not found") }
                    } else {
                        original = forward
                        val sourceOption = SourceAddressOption.fromSshValue(forward.sourceAddr)
                        _uiState.update {
                            it.copy(
                                nickname = forward.nickname,
                                type = forward.type,
                                sourcePort = forward.sourcePort.toString(),
                                sourceAddressOption = sourceOption,
                                specificAddress = if (sourceOption == SourceAddressOption.SPECIFIC) forward.sourceAddr else "",
                                destination = if (forward.destAddr != null && forward.destPort > 0) {
                                    "${forward.destAddr}:${forward.destPort}"
                                } else {
                                    forward.destAddr ?: ""
                                },
                                isLoading = false,
                            )
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load port forward") }
                }
            }
        }
    }

    fun setTerminalManager(manager: TerminalManager?) {
        terminalManager = manager
    }

    fun updateNickname(value: String) = update { it.copy(nickname = value) }
    fun updateType(value: String) = update { it.copy(type = value) }
    fun updateSourcePort(value: String) = update { it.copy(sourcePort = value) }
    fun updateSourceAddressOption(value: SourceAddressOption) = update { it.copy(sourceAddressOption = value) }
    fun updateSpecificAddress(value: String) = update { it.copy(specificAddress = value) }
    fun updateDestination(value: String) = update { it.copy(destination = value) }

    private fun update(change: (PortForwardEditorUiState) -> PortForwardEditorUiState) {
        _uiState.update { change(it).copy(hasUnsavedChanges = true, error = null) }
    }

    fun save(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || !state.hasUnsavedChanges || !state.canSave || (forwardId != 0L && original == null)) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(dispatchers.io) {
                    val destination = state.destination.ifEmpty { "localhost:80" }.split(":")
                    val sourceAddress = if (state.isRemote) {
                        if (state.sourceAddressOption == SourceAddressOption.SPECIFIC) state.specificAddress else state.sourceAddressOption.sshValue
                    } else {
                        "localhost"
                    }
                    val portForward = PortForward(
                        id = original?.id ?: 0L,
                        hostId = hostId,
                        nickname = state.nickname,
                        type = state.type,
                        sourceAddr = sourceAddress,
                        sourcePort = state.sourcePort.ifEmpty { "8080" }.toInt(),
                        destAddr = if (state.needsDestination) destination[0] else null,
                        destPort = if (state.needsDestination) destination[1].toInt() else 0,
                    )
                    val saved = repository.savePortForward(portForward)
                    val bridge = terminalManager?.bridgesFlow?.value?.find { it.host.id == hostId }
                    if (bridge?.transport?.isConnected() == true) {
                        val oldForward = original?.let { old -> bridge.portForwards.find { it.id == old.id } }
                        val wasEnabled = oldForward?.isEnabled() ?: false
                        oldForward?.let { bridge.transport?.removePortForward(it) }
                        bridge.transport?.addPortForward(saved)
                        if (original == null || wasEnabled) bridge.transport?.enablePortForward(saved)
                    }
                }
                _uiState.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Failed to save port forward") }
            }
        }
    }
}
