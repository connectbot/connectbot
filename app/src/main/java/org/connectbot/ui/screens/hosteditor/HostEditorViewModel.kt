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

package org.connectbot.ui.screens.hosteditor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.data.HostRepository
import org.connectbot.data.ProfileRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Profile
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.transport.Transport
import org.connectbot.util.InstallMosh
import org.connectbot.util.SecurePasswordStorage
import javax.inject.Inject

data class HostEditorUiState(
    val hostId: Long = -1L,
    val nickname: String = "",
    val protocol: String = "ssh",
    val username: String = "",
    val hostname: String = "",
    val port: String = "22",
    val color: String = "gray",
    val pubkeyId: Long = -1L,
    val availablePubkeys: List<Pubkey> = emptyList(),
    val profileId: Long? = 1L,
    val availableProfiles: List<Profile> = emptyList(),
    val useAuthAgent: String = "no",
    val compression: Boolean = false,
    val wantSession: Boolean = true,
    val stayConnected: Boolean = false,
    val quickDisconnect: Boolean = false,
    val automationCount: Int = 0,
    val jumpHostId: Long? = null,
    val availableJumpHosts: List<Host> = emptyList(),
    val ipVersion: String = "IPV4_AND_IPV6",
    val password: String = "",
    val hasExistingPassword: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    // Mosh-specific fields
    val moshPort: String = "0",
    val moshServer: String = "",
    val locale: String = "en_US.UTF-8",
    val isMoshInstalling: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HostEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: HostRepository,
    private val pubkeyRepository: PubkeyRepository,
    private val profileRepository: ProfileRepository,
    private val prefs: android.content.SharedPreferences,
    private val securePasswordStorage: SecurePasswordStorage,
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) : ViewModel() {

    private val hostId: Long = savedStateHandle.get<Long>("hostId") ?: -1L
    private val _uiState = MutableStateFlow(
        HostEditorUiState(
            hostId = hostId,
        ),
    )
    val uiState: StateFlow<HostEditorUiState> = _uiState.asStateFlow()

    private var moshInstallJob: Job? = null
    private var preMoshProtocol: String = "ssh"
    private var nicknameAutofillEnabled = hostId == -1L
    private var hasEnteredNickname = false

    init {
        observePubkeys()
        observeJumpHosts()
        observeProfiles()
        if (hostId != -1L) {
            loadHost()
            viewModelScope.launch {
                repository.observeAutomation(hostId)
                    .catch { emit(emptyList()) }
                    .collect { actions -> _uiState.update { it.copy(automationCount = actions.size) } }
            }
        } else {
            // For new hosts, apply the default profile from settings
            val defaultProfileId = prefs.getLong("defaultProfileId", 0L)
            if (defaultProfileId > 0) {
                _uiState.update { it.copy(profileId = defaultProfileId) }
            }
        }
    }

    private fun observePubkeys() {
        viewModelScope.launch {
            pubkeyRepository.observeAll()
                .catch { _uiState.update { it.copy(availablePubkeys = emptyList()) } }
                .collect { pubkeys ->
                    _uiState.update { it.copy(availablePubkeys = pubkeys) }
                }
        }
    }

    private fun observeJumpHosts() {
        viewModelScope.launch {
            repository.observeSshHosts()
                .catch { _uiState.update { it.copy(availableJumpHosts = emptyList()) } }
                .collect { sshHosts ->
                    val filteredHosts = sshHosts.filter { it.id != hostId }
                    _uiState.update { it.copy(availableJumpHosts = filteredHosts) }
                }
        }
    }

    private fun observeProfiles() {
        viewModelScope.launch {
            profileRepository.observeAll()
                .catch { _uiState.update { it.copy(availableProfiles = emptyList()) } }
                .collect { profiles ->
                    _uiState.update { it.copy(availableProfiles = profiles) }
                }
        }
    }

    private fun getDefaultPort(protocol: String): String = (Transport.fromProtocol(protocol)?.defaultPort ?: 0).toString()

    private fun loadHost() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val host = repository.findHostById(hostId)
                if (host != null) {
                    val hasPassword = securePasswordStorage.hasPassword(hostId)
                    _uiState.update {
                        it.copy(
                            nickname = host.nickname,
                            protocol = host.protocol,
                            username = host.username,
                            hostname = host.hostname,
                            port = host.port.toString(),
                            color = host.color ?: "gray",
                            pubkeyId = host.pubkeyId,
                            profileId = host.profileId,
                            useAuthAgent = host.useAuthAgent ?: "no",
                            compression = host.compression,
                            wantSession = host.wantSession,
                            stayConnected = host.stayConnected,
                            quickDisconnect = host.quickDisconnect,
                            jumpHostId = host.jumpHostId,
                            ipVersion = host.ipVersion,
                            hasExistingPassword = hasPassword,
                            hasUnsavedChanges = false,
                            // Mosh-specific fields
                            moshPort = host.moshPort.toString(),
                            moshServer = host.moshServer ?: "",
                            locale = host.locale,
                            isLoading = false,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Host not found")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load host")
                }
            }
        }
    }

    private fun parseConnectionNickname(value: String): Triple<String, String, String>? {
        val regex = Regex("^(?:([^@]+)@)?((?:[0-9a-zA-Z._-]+)|(?:\\[[a-fA-F:0-9]+(?:%[-_.a-zA-Z0-9]+)?\\]))(?::(\\d+))?$")
        val match = regex.find(value) ?: return null
        val (username, hostname, port) = match.destructured
        val isValid = hostname.isNotBlank() && (
            (hostname.startsWith("[") && hostname.endsWith("]")) ||
                hostname.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
            )
        return if (isValid) {
            Triple(username.ifBlank { "" }, hostname, port)
        } else {
            null
        }
    }

    fun onNicknameFocusChanged(isFocused: Boolean) {
        // Only the first nickname entry for a new host can populate connection fields.
        if (!isFocused && hasEnteredNickname) {
            nicknameAutofillEnabled = false
        }
    }

    fun updateNickname(value: String) {
        if (value.isNotEmpty()) hasEnteredNickname = true
        _uiState.update { state ->
            val updated = state.copy(nickname = value, hasUnsavedChanges = true)
            if (state.protocol == "local" || !nicknameAutofillEnabled) {
                return@update updated
            }

            val parsed = parseConnectionNickname(value) ?: return@update updated
            val (username, hostname, port) = parsed
            updated.copy(
                username = username,
                hostname = hostname,
                port = port.ifBlank { getDefaultPort(state.protocol) },
            )
        }
    }

    fun updateProtocol(value: String) {
        val oldProtocol = _uiState.value.protocol
        _uiState.update { it.copy(protocol = value, hasUnsavedChanges = true) }

        if (value == "mosh" && !InstallMosh.isInstalled(context)) {
            preMoshProtocol = oldProtocol
            installMosh(oldProtocol)
        }
    }

    fun cancelMoshInstall() {
        moshInstallJob?.cancel()
        moshInstallJob = null
        _uiState.update { old ->
            old.copy(
                protocol = preMoshProtocol,
                isMoshInstalling = false,
            )
        }
    }

    private fun installMosh(fallbackProtocol: String) {
        moshInstallJob?.cancel()
        moshInstallJob = viewModelScope.launch {
            _uiState.update { it.copy(isMoshInstalling = true) }
            val result = withContext(dispatchers.io) {
                InstallMosh.installClient(context)
            }
            if (result.success) {
                _uiState.update { it.copy(isMoshInstalling = false) }
            } else {
                _uiState.update { old ->
                    old.copy(
                        protocol = fallbackProtocol,
                        isMoshInstalling = false,
                        error = result.errorMessage,
                    )
                }
            }
        }
    }

    fun updateUsername(value: String) {
        nicknameAutofillEnabled = false
        _uiState.update { it.copy(username = value, hasUnsavedChanges = true) }
    }

    fun updateHostname(value: String) {
        nicknameAutofillEnabled = false
        _uiState.update { it.copy(hostname = value, hasUnsavedChanges = true) }
    }

    fun updatePort(value: String) {
        // Only allow numeric input
        if (value.isEmpty() || value.all { it.isDigit() }) {
            nicknameAutofillEnabled = false
            _uiState.update { it.copy(port = value, hasUnsavedChanges = true) }
        }
    }

    fun updateColor(value: String) {
        _uiState.update { it.copy(color = value, hasUnsavedChanges = true) }
    }

    fun updatePubkeyId(value: Long) {
        _uiState.update { it.copy(pubkeyId = value, hasUnsavedChanges = true) }
    }

    fun updateProfileId(value: Long?) {
        _uiState.update { it.copy(profileId = value, hasUnsavedChanges = true) }
    }

    fun updateUseAuthAgent(value: String) {
        _uiState.update { it.copy(useAuthAgent = value, hasUnsavedChanges = true) }
    }

    fun updateCompression(value: Boolean) {
        _uiState.update { it.copy(compression = value, hasUnsavedChanges = true) }
    }

    fun updateWantSession(value: Boolean) {
        _uiState.update { it.copy(wantSession = value, hasUnsavedChanges = true) }
    }

    fun updateStayConnected(value: Boolean) {
        _uiState.update { it.copy(stayConnected = value, hasUnsavedChanges = true) }
    }

    fun updateQuickDisconnect(value: Boolean) {
        _uiState.update { it.copy(quickDisconnect = value, hasUnsavedChanges = true) }
    }

    fun updateJumpHostId(value: Long?) {
        _uiState.update { it.copy(jumpHostId = value, hasUnsavedChanges = true) }
    }

    fun updateIpVersion(value: String) {
        _uiState.update { it.copy(ipVersion = value, hasUnsavedChanges = true) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, hasUnsavedChanges = true) }
    }

    fun clearSavedPassword() {
        _uiState.update { it.copy(password = "", hasExistingPassword = false, hasUnsavedChanges = true) }
    }

    fun updateMoshPort(value: String) {
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.update { it.copy(moshPort = value, hasUnsavedChanges = true) }
        }
    }

    fun updateMoshServer(value: String) {
        _uiState.update { it.copy(moshServer = value, hasUnsavedChanges = true) }
    }

    fun updateLocale(value: String) {
        _uiState.update { it.copy(locale = value, hasUnsavedChanges = true) }
    }

    suspend fun saveHost(): Boolean {
        if (_uiState.value.isSaving) return false
        _uiState.update { it.copy(isSaving = true, error = null) }
        try {
            val state = _uiState.value
            val existingHost = if (hostId != -1L) {
                repository.findHostById(hostId)
            } else {
                null
            }

            // Only SSH and Mosh hosts can have a jump host
            val jumpHostId = if (state.protocol == "ssh" || state.protocol == "mosh") state.jumpHostId else null

            val host = Host(
                id = existingHost?.id ?: 0L,
                nickname = state.nickname,
                protocol = state.protocol,
                username = state.username,
                hostname = state.hostname,
                port = state.port.toIntOrNull() ?: getDefaultPort(state.protocol).toIntOrNull() ?: 22,
                color = state.color.takeIf { it != "gray" },
                pubkeyId = state.pubkeyId,
                profileId = state.profileId,
                useAuthAgent = state.useAuthAgent.takeIf { it != "no" },
                compression = state.compression,
                wantSession = state.wantSession,
                stayConnected = state.stayConnected,
                quickDisconnect = state.quickDisconnect,
                postLogin = null,
                lastConnect = existingHost?.lastConnect ?: System.currentTimeMillis(),
                hostKeyAlgo = existingHost?.hostKeyAlgo,
                useKeys = existingHost?.useKeys ?: true,
                scrollbackLines = existingHost?.scrollbackLines ?: 140,
                useCtrlAltAsMetaKey = existingHost?.useCtrlAltAsMetaKey ?: false,
                jumpHostId = jumpHostId,
                ipVersion = state.ipVersion,
                moshPort = state.moshPort.toIntOrNull() ?: 0,
                moshServer = state.moshServer.ifBlank { null },
                locale = state.locale.ifBlank { "en_US.UTF-8" },
            )

            val savedHost = repository.saveHost(host)

            // Handle password storage for SSH and Mosh
            if (state.protocol == "ssh" || state.protocol == "mosh") {
                if (state.password.isNotEmpty()) {
                    // Save or update the password
                    securePasswordStorage.savePassword(savedHost.id, state.password)
                } else if (!state.hasExistingPassword) {
                    // No password entered and no existing password - ensure it's cleared
                    securePasswordStorage.deletePassword(savedHost.id)
                }
                // If password is empty but hasExistingPassword is true, keep existing
            }
            return true
        } catch (e: Exception) {
            _uiState.update {
                it.copy(error = e.message ?: "Failed to save host")
            }
            return false
        } finally {
            _uiState.update { it.copy(isSaving = false) }
        }
    }
}
