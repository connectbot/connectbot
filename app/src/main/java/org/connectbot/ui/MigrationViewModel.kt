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

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.connectbot.data.migration.DatabaseMigrator
import org.connectbot.data.migration.MigrationResult
import org.connectbot.data.migration.MigrationState

/**
 * ViewModel for managing database migration on app startup.
 *
 * Checks if migration is needed and performs it automatically,
 * exposing the migration state to the UI.
 */
class MigrationViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MigrationViewModel"
    }

    private val migrator = DatabaseMigrator.get(application)

    private val _uiState = MutableStateFlow<MigrationUiState>(MigrationUiState.Checking)
    val uiState: StateFlow<MigrationUiState> = _uiState.asStateFlow()

    private var latestMigrationState: MigrationState? = null

    init {
        checkAndMigrate()
    }

    private fun checkAndMigrate() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Checking if migration is needed")
                val needsMigration = migrator.isMigrationNeeded()

                if (!needsMigration) {
                    Log.d(TAG, "No migration needed")
                    _uiState.value = MigrationUiState.Completed
                    return@launch
                }

                Log.i(TAG, "Migration needed, starting migration")
                _uiState.value = MigrationUiState.InProgress(MigrationState())

                // Collect migration state updates
                launch {
                    migrator.migrationState.collect { state ->
                        latestMigrationState = state
                        _uiState.value = MigrationUiState.InProgress(state)
                    }
                }

                // Perform migration
                val result = migrator.migrate()

                when (result) {
                    is MigrationResult.Success -> {
                        Log.i(TAG, "Migration completed successfully: $result")
                        _uiState.value = MigrationUiState.Completed
                    }

                    is MigrationResult.Failure -> {
                        Log.e(TAG, "Migration failed", result.error)
                        _uiState.value = MigrationUiState.Failed(
                            error = result.error.message ?: "Unknown error",
                            debugLog = latestMigrationState?.debugLog ?: emptyList()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during migration check/execution", e)
                _uiState.value = MigrationUiState.Failed(
                    error = e.message ?: "Unknown error",
                    debugLog = latestMigrationState?.debugLog ?: emptyList()
                )
            }
        }
    }

    /**
     * Retry migration after a failure.
     */
    fun retryMigration() {
        migrator.resetMigrationState()
        _uiState.value = MigrationUiState.Checking
        checkAndMigrate()
    }
}

/**
 * UI state for the migration process.
 */
sealed class MigrationUiState {
    /** Checking if migration is needed */
    data object Checking : MigrationUiState()

    /** Migration is in progress */
    data class InProgress(val state: MigrationState) : MigrationUiState()

    /** Migration completed successfully (or was not needed) */
    data object Completed : MigrationUiState()

    /** Migration failed */
    data class Failed(val error: String, val debugLog: List<String>) : MigrationUiState()
}
