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

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.connectbot.data.migration.DatabaseMigrator
import org.connectbot.data.migration.MigrationResult
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
    private val migrator: DatabaseMigrator
) : ViewModel() {

    companion object {
        private const val TAG = "AppViewModel"
    }

    private val _terminalManager = MutableStateFlow<TerminalManager?>(null)

    private val _migrationUiState = MutableStateFlow<MigrationUiState>(MigrationUiState.Checking)
    private var latestMigrationState: MigrationState? = null

    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Loading)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        checkAndMigrate()

        viewModelScope.launch {
            combine(
                _migrationUiState,
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

    private fun checkAndMigrate() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Checking if migration is needed")
                val needsMigration = migrator.isMigrationNeeded()

                if (!needsMigration) {
                    Log.d(TAG, "No migration needed")
                    _migrationUiState.value = MigrationUiState.Completed
                    return@launch
                }

                Log.i(TAG, "Migration needed, starting migration")
                _migrationUiState.value = MigrationUiState.InProgress(MigrationState())

                launch {
                    migrator.migrationState.collect { state ->
                        latestMigrationState = state
                        _migrationUiState.value = MigrationUiState.InProgress(state)
                    }
                }

                val result = migrator.migrate()

                when (result) {
                    is MigrationResult.Success -> {
                        Log.i(TAG, "Migration completed successfully: $result")
                        _migrationUiState.value = MigrationUiState.Completed
                    }

                    is MigrationResult.Failure -> {
                        Log.e(TAG, "Migration failed", result.error)
                        _migrationUiState.value = MigrationUiState.Failed(
                            error = result.error.message ?: "Unknown error",
                            debugLog = latestMigrationState?.debugLog ?: emptyList()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during migration check/execution", e)
                _migrationUiState.value = MigrationUiState.Failed(
                    error = e.message ?: "Unknown error",
                    debugLog = latestMigrationState?.debugLog ?: emptyList()
                )
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
        migrator.resetMigrationState()
        _migrationUiState.value = MigrationUiState.Checking
        checkAndMigrate()
    }
}

/**
 * UI state for the migration process.
 */
sealed class MigrationUiState {
    data object Checking : MigrationUiState()
    data class InProgress(val state: MigrationState) : MigrationUiState()
    data object Completed : MigrationUiState()
    data class Failed(val error: String, val debugLog: List<String>) : MigrationUiState()
}
