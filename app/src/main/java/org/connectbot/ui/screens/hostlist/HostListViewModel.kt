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

package org.connectbot.ui.screens.hostlist

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.connectbot.R
import org.connectbot.data.EncryptedExportBundle
import org.connectbot.data.HostRepository
import org.connectbot.data.ImportCounts
import org.connectbot.data.WrongPassphraseException
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.ServiceError
import org.connectbot.service.TerminalManager
import org.connectbot.util.DiscoveredSshServer
import org.connectbot.util.PreferenceConstants
import org.connectbot.util.SshDiscoveryEvent
import org.connectbot.util.SshServiceDiscovery
import javax.inject.Inject

enum class ConnectionState {
    UNKNOWN,
    CONNECTED,

    /**
     * A bridge exists but the connection attempt has not completed yet.
     * Shown distinctly from [CONNECTED] so a doomed attempt (e.g. with no
     * network) never flashes the host green before failing.
     * https://github.com/connectbot/connectbot/issues/386
     */
    CONNECTING,

    /**
     * A bridge for the host still exists but its connection has dropped
     * (failed, reconnecting, or in the network grace period). Unlike
     * [DISCONNECTED], the session can still be disconnected individually.
     */
    FAILED,
    DISCONNECTED,
}

data class HostListUiState(
    val hosts: List<Host> = emptyList(),
    val connectionStates: Map<Long, ConnectionState> = emptyMap(),
    val portForwards: Map<Long, List<PortForward>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sortedByColor: Boolean = false,
    val exportedJson: String? = null,
    val exportedEncrypted: Boolean = false,
    val exportResult: ExportResult? = null,
    val importResult: ImportResult? = null,
    val pendingEncryptedImport: String? = null,
    val importWrongPassphrase: Boolean = false,
    val startupKeyPrompt: Pubkey? = null,
    val startupKeyWrongPassword: Boolean = false,
    val showSshDiscovery: Boolean = false,
    val isDiscoveringSshServers: Boolean = false,
    val discoveredSshServers: List<DiscoveredSshServer> = emptyList(),
    val sshDiscoveryError: String? = null,
)

data class ImportResult(
    val hostsImported: Int,
    val hostsSkipped: Int,
    val profilesImported: Int,
    val profilesSkipped: Int,
)

data class ExportResult(
    val hostCount: Int,
    val profileCount: Int,
)

@HiltViewModel
class HostListViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: HostRepository,
    private val dispatchers: CoroutineDispatchers,
    private val sharedPreferences: SharedPreferences,
    private val sshServiceDiscovery: SshServiceDiscovery,
) : ViewModel() {

    private var terminalManager: TerminalManager? = null
    private var sshDiscoveryJob: Job? = null
    private var sshDiscoveryGeneration = 0L
    private val allPortForwards = MutableStateFlow<List<PortForward>>(emptyList())
    private val _uiState = MutableStateFlow(
        HostListUiState(
            isLoading = true,
            sortedByColor = sharedPreferences.getBoolean(PreferenceConstants.SORT_BY_COLOR, false),
        ),
    )
    val uiState: StateFlow<HostListUiState> = _uiState.asStateFlow()

    init {
        observeHosts()
        observePortForwards()
    }

    fun setTerminalManager(manager: TerminalManager) {
        if (terminalManager != manager) {
            terminalManager = manager
            // Observe host status changes from Flow
            observeHostStatusChanges()
            // Collect service errors from TerminalManager
            collectServiceErrors()
            // Surface any encrypted keys that are waiting for a passphrase to be entered
            observePendingStartupKeyPrompts()
            // Update initial connection states
            updateConnectionStates(_uiState.value.hosts)
            updatePortForwardStates()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeHosts() {
        viewModelScope.launch {
            _uiState
                .map { it.sortedByColor }
                .distinctUntilChanged()
                .flatMapLatest { sortedByColor ->
                    if (sortedByColor) {
                        repository.observeHostsSortedByColor()
                    } else {
                        repository.observeHosts()
                    }
                }
                .collect { hosts ->
                    updateConnectionStates(hosts)
                    _uiState.update {
                        it.copy(hosts = hosts, isLoading = false, error = null)
                    }
                }
        }
    }

    private fun observeHostStatusChanges() {
        val manager = terminalManager ?: return
        viewModelScope.launch {
            manager.hostStatusChangedFlow.collect {
                // Update connection states when terminal manager notifies us of changes
                updateConnectionStates(_uiState.value.hosts)
                updatePortForwardStates()
            }
        }
    }

    private fun observePortForwards() {
        viewModelScope.launch {
            repository.observeAllPortForwards().collect { portForwards ->
                allPortForwards.value = portForwards
                updatePortForwardStates()
            }
        }
    }

    private fun collectServiceErrors() {
        val manager = terminalManager ?: return
        viewModelScope.launch {
            manager.serviceErrors.collect { error ->
                val errorMessage = formatServiceError(error)
                _uiState.update { it.copy(error = errorMessage) }
            }
        }
    }

    private fun formatServiceError(error: ServiceError): String = when (error) {
        is ServiceError.KeyLoadFailed -> {
            context.getString(R.string.error_key_load_failed, error.keyName, error.reason)
        }

        is ServiceError.ConnectionFailed -> {
            context.getString(
                R.string.error_connection_failed,
                error.hostNickname,
                error.hostname,
                error.reason,
            )
        }

        is ServiceError.PortForwardLoadFailed -> {
            context.getString(
                R.string.error_port_forward_load_failed,
                error.hostNickname,
                error.reason,
            )
        }

        is ServiceError.HostSaveFailed -> {
            context.getString(R.string.error_host_save_failed, error.hostNickname, error.reason)
        }

        is ServiceError.ColorSchemeLoadFailed -> {
            context.getString(R.string.error_color_scheme_load_failed, error.reason)
        }
    }

    private fun updateConnectionStates(hosts: List<Host>) {
        val states = hosts.associate { host ->
            host.id to getConnectionState(host)
        }
        _uiState.update { it.copy(connectionStates = states) }
    }

    private fun getConnectionState(host: Host): ConnectionState {
        val manager = terminalManager ?: return ConnectionState.UNKNOWN

        // Check if host has an active bridge
        val bridge = manager.bridgesFlow.value.find { it.host.id == host.id }
        if (bridge != null) {
            // Bridge exists but may be disconnected or in grace period
            return when {
                bridge.disconnected || bridge.isInGracePeriod() -> ConnectionState.FAILED
                bridge.isConnecting -> ConnectionState.CONNECTING
                else -> ConnectionState.CONNECTED
            }
        }

        // Check if in disconnected list by comparing ID
        if (manager.disconnectedFlow.value.any { it.id == host.id }) {
            return ConnectionState.DISCONNECTED
        }

        return ConnectionState.UNKNOWN
    }

    private fun updatePortForwardStates() {
        val bridges = terminalManager?.bridgesFlow?.value.orEmpty()
        val grouped = allPortForwards.value.groupBy { it.hostId }.mapValues { (hostId, forwards) ->
            val bridge = bridges.find { it.host.id == hostId }
            val bridgeForwards = if (bridge != null && bridge.transport?.isConnected() == true) {
                bridge.portForwards
            } else {
                null
            }

            // Create new PortForward copies so StateFlow equality checks detect changes
            forwards.map { pf ->
                pf.copy().apply {
                    setEnabled(bridgeForwards?.find { it.id == pf.id }?.isEnabled() ?: false)
                }
            }
        }
        _uiState.update { it.copy(portForwards = grouped) }
    }

    fun togglePortForward(portForward: PortForward, enable: Boolean) {
        viewModelScope.launch {
            try {
                val bridge = terminalManager?.bridgesFlow?.value?.find { it.host.id == portForward.hostId }
                if (bridge == null || bridge.transport?.isConnected() != true) {
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
                    updatePortForwardStates()
                } else {
                    val action = if (enable) "enable" else "disable"
                    _uiState.update {
                        it.copy(error = "Failed to $action port forward ${portForward.nickname}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to toggle port forward")
                }
            }
        }
    }

    fun toggleSortOrder() {
        val newSortedByColor = !_uiState.value.sortedByColor
        sharedPreferences.edit { putBoolean(PreferenceConstants.SORT_BY_COLOR, newSortedByColor) }
        _uiState.update { it.copy(sortedByColor = newSortedByColor) }
    }

    fun deleteHost(host: Host) {
        viewModelScope.launch {
            try {
                terminalManager?.disconnectHost(host.id)
                repository.deleteHost(host)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete host")
                }
            }
        }
    }

    fun duplicateHost(host: Host) {
        viewModelScope.launch {
            try {
                // Create new host with reset fields
                val newHost = host.copy(
                    id = 0L,
                    nickname = context.getString(R.string.host_duplicate_nickname, host.nickname),
                    lastConnect = 0,
                    hostKeyAlgo = null,
                )
                val savedHost = repository.saveHost(newHost)

                // Copy port forwards
                val portForwards = repository.getPortForwardsForHost(host.id)
                for (pf in portForwards) {
                    repository.savePortForward(pf.copy(id = 0L, hostId = savedHost.id))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to duplicate host")
                }
            }
        }
    }

    fun forgetHostKeys(host: Host) {
        viewModelScope.launch {
            try {
                repository.deleteKnownHostsForHost(host.id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to forget host keys")
                }
            }
        }
    }

    fun disconnectAll() {
        terminalManager?.disconnectAll(excludeLocal = false)
    }

    fun disconnectHost(host: Host) {
        terminalManager?.disconnectHost(host.id)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun startSshDiscovery() {
        val generation = ++sshDiscoveryGeneration
        sshDiscoveryJob?.cancel()
        _uiState.update {
            it.copy(
                showSshDiscovery = true,
                isDiscoveringSshServers = true,
                discoveredSshServers = emptyList(),
                sshDiscoveryError = null,
            )
        }
        sshDiscoveryJob = viewModelScope.launch {
            try {
                withTimeoutOrNull(SSH_DISCOVERY_DURATION_MILLIS) {
                    sshServiceDiscovery.discover().collect { event ->
                        when (event) {
                            is SshDiscoveryEvent.Found -> {
                                _uiState.update { state ->
                                    val servers = state.discoveredSshServers
                                        .filterNot { it.key == event.server.key }
                                        .plus(event.server)
                                        .sortedBy { it.serviceName.lowercase() }
                                    state.copy(discoveredSshServers = servers)
                                }
                            }

                            is SshDiscoveryEvent.Lost -> {
                                _uiState.update { state ->
                                    state.copy(
                                        discoveredSshServers = state.discoveredSshServers
                                            .filterNot { it.key == event.key },
                                    )
                                }
                            }

                            is SshDiscoveryEvent.Failed -> {
                                _uiState.update {
                                    it.copy(
                                        isDiscoveringSshServers = false,
                                        sshDiscoveryError = context.getString(R.string.host_discovery_failed),
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(sshDiscoveryError = context.getString(R.string.host_discovery_failed))
                }
            } finally {
                if (generation == sshDiscoveryGeneration) {
                    _uiState.update { it.copy(isDiscoveringSshServers = false) }
                }
            }
        }
    }

    fun dismissSshDiscovery() {
        sshDiscoveryGeneration++
        sshDiscoveryJob?.cancel()
        sshDiscoveryJob = null
        _uiState.update {
            it.copy(
                showSshDiscovery = false,
                isDiscoveringSshServers = false,
                sshDiscoveryError = null,
            )
        }
    }

    fun showSshDiscoveryPermissionDenied() {
        _uiState.update {
            it.copy(error = context.getString(R.string.host_discovery_permission_denied))
        }
    }

    fun exportHosts() {
        viewModelScope.launch {
            try {
                val (json, exportCounts) = withContext(dispatchers.io) {
                    repository.exportHostsToJson()
                }
                val exportResult = ExportResult(
                    hostCount = exportCounts.hostCount,
                    profileCount = exportCounts.profileCount,
                )
                _uiState.update { it.copy(exportedJson = json, exportedEncrypted = false, exportResult = exportResult) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to export hosts")
                }
            }
        }
    }

    private companion object {
        const val SSH_DISCOVERY_DURATION_MILLIS = 10_000L
    }

    fun exportHostsEncrypted(passphrase: String) {
        viewModelScope.launch {
            try {
                val (bundle, exportCounts) = withContext(dispatchers.io) {
                    repository.exportHostsToEncryptedBundle(passphrase.toCharArray())
                }
                val exportResult = ExportResult(
                    hostCount = exportCounts.hostCount,
                    profileCount = exportCounts.profileCount,
                )
                _uiState.update { it.copy(exportedJson = bundle, exportedEncrypted = true, exportResult = exportResult) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to export hosts")
                }
            }
        }
    }

    fun clearExportedJson() {
        _uiState.update { it.copy(exportedJson = null, exportedEncrypted = false, exportResult = null) }
    }

    fun importHosts(jsonString: String) {
        if (EncryptedExportBundle.isEncryptedBundle(jsonString)) {
            // Hold the bundle and ask the UI to prompt for the passphrase
            _uiState.update {
                it.copy(pendingEncryptedImport = jsonString, importWrongPassphrase = false)
            }
            return
        }
        viewModelScope.launch {
            try {
                val importCounts = withContext(dispatchers.io) {
                    repository.importHostsFromJson(jsonString)
                }
                _uiState.update { it.copy(importResult = importCounts.toImportResult()) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to import hosts")
                }
            }
        }
    }

    fun submitImportPassphrase(passphrase: String) {
        val bundle = _uiState.value.pendingEncryptedImport ?: return
        viewModelScope.launch {
            try {
                val importCounts = withContext(dispatchers.io) {
                    repository.importHostsFromEncryptedBundle(bundle, passphrase.toCharArray())
                }
                _uiState.update {
                    it.copy(
                        importResult = importCounts.toImportResult(),
                        pendingEncryptedImport = null,
                        importWrongPassphrase = false,
                    )
                }
            } catch (e: WrongPassphraseException) {
                _uiState.update { it.copy(importWrongPassphrase = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to import hosts",
                        pendingEncryptedImport = null,
                        importWrongPassphrase = false,
                    )
                }
            }
        }
    }

    fun cancelEncryptedImport() {
        _uiState.update { it.copy(pendingEncryptedImport = null, importWrongPassphrase = false) }
    }

    private fun ImportCounts.toImportResult() = ImportResult(
        hostsImported = hostsImported,
        hostsSkipped = hostsSkipped,
        profilesImported = profilesImported,
        profilesSkipped = profilesSkipped,
    )

    fun clearImportResult() {
        _uiState.update { it.copy(importResult = null) }
    }

    private fun observePendingStartupKeyPrompts() {
        val manager = terminalManager ?: return
        viewModelScope.launch {
            manager.pendingStartupKeyPrompts.collect { queue ->
                val head = queue.firstOrNull()
                _uiState.update { state ->
                    state.copy(
                        startupKeyPrompt = head,
                        // Reset wrong-password flag whenever the head of the queue changes
                        startupKeyWrongPassword = if (head?.id != state.startupKeyPrompt?.id) {
                            false
                        } else {
                            state.startupKeyWrongPassword
                        },
                    )
                }
            }
        }
    }

    fun submitStartupKeyPassword(password: String) {
        val manager = terminalManager ?: return
        val pubkey = _uiState.value.startupKeyPrompt ?: return
        viewModelScope.launch {
            val unlocked = withContext(dispatchers.default) {
                manager.unlockPendingStartupKey(pubkey, password)
            }
            if (!unlocked) {
                _uiState.update { it.copy(startupKeyWrongPassword = true) }
            }
        }
    }

    fun dismissStartupKeyPrompt() {
        val manager = terminalManager ?: return
        val pubkey = _uiState.value.startupKeyPrompt ?: return
        manager.dismissPendingStartupKey(pubkey)
    }
}
