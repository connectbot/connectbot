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

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
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
        private const val TAG = "CB.AppViewModel"
    }

    private val _terminalManager = MutableStateFlow<TerminalManager?>(null)

    private val _migrationUiState = MutableStateFlow<MigrationUiState>(MigrationUiState.Checking)
    private var latestMigrationState: MigrationState? = null

    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Loading)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _showPermissionRationale = Channel<Unit>(Channel.CONFLATED)
    val showPermissionRationale = _showPermissionRationale.receiveAsFlow()

    private val _requestPermission = Channel<Unit>(Channel.CONFLATED)
    val requestPermission = _requestPermission.receiveAsFlow()

    private val _pendingConnectionUri = MutableStateFlow<Uri?>(null)
    val pendingConnectionUri: StateFlow<Uri?> = _pendingConnectionUri.asStateFlow()

    private val _pendingDisconnectAll = MutableStateFlow(false)
    val pendingDisconnectAll: StateFlow<Boolean> = _pendingDisconnectAll.asStateFlow()

    private val _finishActivity = Channel<Unit>(Channel.CONFLATED)
    val finishActivity = _finishActivity.receiveAsFlow()

    private var hasRequestedInitialPermission = false

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

    /**
     * Set the pending connection URI that should be opened after permission dialog is dismissed.
     */
    fun setPendingConnectionUri(uri: Uri?) {
        _pendingConnectionUri.value = uri
    }

    /**
     * Clear the pending connection URI.
     */
    fun clearPendingConnectionUri() {
        _pendingConnectionUri.value = null
    }

    /**
     * Set whether a disconnect all operation is pending.
     */
    fun setPendingDisconnectAll(pending: Boolean) {
        _pendingDisconnectAll.value = pending
    }

    /**
     * Execute pending disconnect all operation if ready.
     * Returns true if disconnect was executed and activity should finish.
     */
    fun executePendingDisconnectAllIfReady(): Boolean {
        val state = _uiState.value
        if (state is AppUiState.Ready && _pendingDisconnectAll.value) {
            Log.d(TAG, "Executing pending disconnectAll")
            state.terminalManager.disconnectAll(immediate = true, excludeLocal = false)
            _pendingDisconnectAll.value = false
            viewModelScope.launch {
                _finishActivity.send(Unit)
            }
            return true
        }
        return false
    }

    /**
     * Check if notification permission is needed and handle accordingly.
     * Returns true if the connection can proceed, false if permission needs to be requested.
     */
    fun checkAndRequestNotificationPermission(
        context: Context,
        uri: Uri,
        shouldShowRationale: Boolean
    ): Boolean {
        Log.d(TAG, "checkAndRequestNotificationPermission: uri=$uri, SDK=${Build.VERSION.SDK_INT}")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "SDK < TIRAMISU, allowing without permission")
            return true
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "Permission check: hasPermission=$hasPermission, shouldShowRationale=$shouldShowRationale")

        return when {
            hasPermission -> {
                Log.d(TAG, "Permission already granted, proceeding")
                true
            }
            shouldShowRationale -> {
                Log.d(TAG, "Showing rationale dialog")
                _pendingConnectionUri.value = uri
                viewModelScope.launch {
                    _showPermissionRationale.send(Unit)
                }
                false
            }
            else -> {
                Log.d(TAG, "Requesting permission")
                _pendingConnectionUri.value = uri
                viewModelScope.launch {
                    _requestPermission.send(Unit)
                }
                false
            }
        }
    }

    /**
     * Request notification permission on app startup if needed.
     * Should be called when the app becomes ready.
     */
    fun requestInitialNotificationPermissionIfNeeded(
        context: Context,
        shouldShowRationale: Boolean
    ) {
        if (hasRequestedInitialPermission) {
            Log.d(TAG, "Already requested initial permission, skipping")
            return
        }

        hasRequestedInitialPermission = true

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "SDK < TIRAMISU, no notification permission needed")
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "Initial permission check: hasPermission=$hasPermission, shouldShowRationale=$shouldShowRationale")

        if (!hasPermission) {
            viewModelScope.launch {
                if (shouldShowRationale) {
                    Log.d(TAG, "Showing permission rationale on startup")
                    _showPermissionRationale.send(Unit)
                } else {
                    Log.d(TAG, "Requesting permission on startup")
                    _requestPermission.send(Unit)
                }
            }
        }
    }

    /**
     * Handle the result of a notification permission request.
     * Returns the pending URI regardless of permission result, so navigation can proceed.
     * The app works fine without notification permission - connections just won't show notifications.
     */
    fun onNotificationPermissionResult(isGranted: Boolean): Uri? {
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
        } else {
            Log.d(TAG, "Notification permission denied - connections will work but without notifications")
        }

        // Return and clear pending URI so navigation can proceed
        val uri = _pendingConnectionUri.value
        _pendingConnectionUri.value = null
        return uri
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
