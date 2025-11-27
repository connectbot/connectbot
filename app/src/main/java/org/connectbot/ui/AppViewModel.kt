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

package org.connectbot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.connectbot.data.migration.MigrationState
import org.connectbot.service.TerminalManager
import javax.inject.Inject

/**
 * Unified app state that coordinates migration and service binding.
 */
sealed class AppUiState {
    data object Loading : AppUiState()
    data class MigrationInProgress(val state: MigrationState) : AppUiState()
    data class MigrationFailed(val error: String, val debugLog: List<String>) : AppUiState()
    data class Ready(val terminalManager: TerminalManager) : AppUiState()
}

/**
 * ViewModel that coordinates both migration state and service binding state
 * into a unified app state machine. This eliminates intermediate states and
 * ensures smooth, flicker-free UI transitions.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val migrationViewModel: MigrationViewModel
) : ViewModel() {

    private val _terminalManager = MutableStateFlow<TerminalManager?>(null)

    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Loading)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                migrationViewModel.uiState,
                _terminalManager
            ) { migrationState, terminalMgr ->
                when (migrationState) {
                    is MigrationUiState.Checking -> AppUiState.Loading
                    is MigrationUiState.InProgress -> AppUiState.MigrationInProgress(migrationState.state)
                    is MigrationUiState.Failed -> AppUiState.MigrationFailed(
                        migrationState.error,
                        migrationState.debugLog
                    )
                    is MigrationUiState.Completed -> {
                        if (terminalMgr != null) {
                            AppUiState.Ready(terminalMgr)
                        } else {
                            AppUiState.Loading
                        }
                    }
                }
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    /**
     * Called by MainActivity when TerminalManager service is bound or unbound.
     */
    fun setTerminalManager(manager: TerminalManager?) {
        _terminalManager.value = manager
    }

    /**
     * Retry migration after a failure.
     */
    fun retryMigration() {
        migrationViewModel.retryMigration()
    }
}
