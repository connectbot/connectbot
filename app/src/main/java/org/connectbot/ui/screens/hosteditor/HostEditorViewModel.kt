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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.data.HostRepository
import org.connectbot.data.KeyboardLayoutRepository
import org.connectbot.data.ProfileRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KeyboardLayout
import org.connectbot.data.entity.Profile
import org.connectbot.data.entity.Pubkey
import org.connectbot.transport.Transport
import org.connectbot.ui.navigation.NavArgs
import org.connectbot.util.SecurePasswordStorage
import javax.inject.Inject

data class HostEditorUiState(
    val hostId: Long = -1L,
    val quickConnect: String = "",
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
    val tmuxEnabled: Boolean = true,
    val stayConnected: Boolean = false,
    val connectOnStartup: Boolean = false,
    val quickDisconnect: Boolean = false,
    val keyboardSuggestions: Boolean = false,
    val keyboardLayoutId: Long? = null,
    val availableKeyboardLayouts: List<KeyboardLayout> = emptyList(),
    val postLogin: String = "",
    val jumpHostId: Long? = null,
    val availableJumpHosts: List<Host> = emptyList(),
    val ipVersion: String = "IPV4_AND_IPV6",
    val password: String = "",
    val hasExistingPassword: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val isNicknameMatching: Boolean
        get() {
            if (nickname.isBlank()) return true
            val defaultPort = (Transport.fromProtocol(protocol)?.defaultPort ?: 0).toString()
            val effectivePort = port.ifBlank { defaultPort }
            val repWithPort = if (username.isNotEmpty() && protocol == "ssh") "$username@$hostname:$effectivePort" else "$hostname:$effectivePort"
            val repWithoutPort = if (username.isNotEmpty() && protocol == "ssh") "$username@$hostname" else hostname
            return nickname == repWithPort || (effectivePort == defaultPort && nickname == repWithoutPort)
        }
}

@HiltViewModel
class HostEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: HostRepository,
    private val pubkeyRepository: PubkeyRepository,
    private val profileRepository: ProfileRepository,
    private val keyboardLayoutRepository: KeyboardLayoutRepository,
    private val prefs: android.content.SharedPreferences,
    private val securePasswordStorage: SecurePasswordStorage,
) : ViewModel() {

    private val hostId: Long = savedStateHandle.get<Long>("hostId") ?: -1L
    private val _uiState = MutableStateFlow(HostEditorUiState(hostId = hostId))
    val uiState: StateFlow<HostEditorUiState> = _uiState.asStateFlow()

    /**
     * Gates mirroring edits into [SavedStateHandle]: an existing host must
     * not have the pre-load defaults recorded over its real values, so
     * persistence only starts once its fields have been loaded (or restored).
     */
    private var persistEditsEnabled = hostId == -1L

    init {
        observePubkeys()
        observeJumpHosts()
        observeProfiles()
        observeKeyboardLayouts()
        val restored = restoreEditsAfterProcessDeath()
        when {
            restored != null -> {
                persistEditsEnabled = true
                _uiState.value = restored
            }

            hostId != -1L -> loadHost()

            else -> initializeNewHost()
        }
        viewModelScope.launch {
            uiState.collect { state ->
                if (persistEditsEnabled && !state.isLoading) {
                    persistEditsForProcessDeath(state)
                }
            }
        }
    }

    /**
     * Mirror the user-editable fields into [SavedStateHandle] so in-progress
     * edits survive the OS killing the backgrounded process (config changes
     * are already covered by the retained ViewModel). The password field is
     * deliberately not persisted: saved instance state is written to disk
     * unencrypted. https://github.com/connectbot/connectbot/issues/1060
     */
    private fun persistEditsForProcessDeath(state: HostEditorUiState) {
        savedStateHandle[KEY_EDITS_SAVED] = true
        savedStateHandle[KEY_QUICK_CONNECT] = state.quickConnect
        savedStateHandle[KEY_NICKNAME] = state.nickname
        savedStateHandle[KEY_PROTOCOL] = state.protocol
        savedStateHandle[KEY_USERNAME] = state.username
        savedStateHandle[KEY_HOSTNAME] = state.hostname
        savedStateHandle[KEY_PORT] = state.port
        savedStateHandle[KEY_COLOR] = state.color
        savedStateHandle[KEY_PUBKEY_ID] = state.pubkeyId
        savedStateHandle[KEY_PROFILE_ID] = state.profileId
        savedStateHandle[KEY_USE_AUTH_AGENT] = state.useAuthAgent
        savedStateHandle[KEY_COMPRESSION] = state.compression
        savedStateHandle[KEY_WANT_SESSION] = state.wantSession
        savedStateHandle[KEY_TMUX_ENABLED] = state.tmuxEnabled
        savedStateHandle[KEY_STAY_CONNECTED] = state.stayConnected
        savedStateHandle[KEY_CONNECT_ON_STARTUP] = state.connectOnStartup
        savedStateHandle[KEY_QUICK_DISCONNECT] = state.quickDisconnect
        savedStateHandle[KEY_KEYBOARD_SUGGESTIONS] = state.keyboardSuggestions
        savedStateHandle[KEY_KEYBOARD_LAYOUT_ID] = state.keyboardLayoutId
        savedStateHandle[KEY_POST_LOGIN] = state.postLogin
        savedStateHandle[KEY_JUMP_HOST_ID] = state.jumpHostId
        savedStateHandle[KEY_IP_VERSION] = state.ipVersion
        savedStateHandle[KEY_HAS_EXISTING_PASSWORD] = state.hasExistingPassword
    }

    private fun restoreEditsAfterProcessDeath(): HostEditorUiState? {
        if (savedStateHandle.get<Boolean>(KEY_EDITS_SAVED) != true) return null
        return HostEditorUiState(
            hostId = hostId,
            quickConnect = savedStateHandle[KEY_QUICK_CONNECT] ?: "",
            nickname = savedStateHandle[KEY_NICKNAME] ?: "",
            protocol = savedStateHandle[KEY_PROTOCOL] ?: "ssh",
            username = savedStateHandle[KEY_USERNAME] ?: "",
            hostname = savedStateHandle[KEY_HOSTNAME] ?: "",
            port = savedStateHandle[KEY_PORT] ?: "22",
            color = savedStateHandle[KEY_COLOR] ?: "gray",
            pubkeyId = savedStateHandle[KEY_PUBKEY_ID] ?: -1L,
            profileId = savedStateHandle[KEY_PROFILE_ID],
            useAuthAgent = savedStateHandle[KEY_USE_AUTH_AGENT] ?: "no",
            compression = savedStateHandle[KEY_COMPRESSION] ?: false,
            wantSession = savedStateHandle[KEY_WANT_SESSION] ?: true,
            tmuxEnabled = savedStateHandle[KEY_TMUX_ENABLED] ?: true,
            stayConnected = savedStateHandle[KEY_STAY_CONNECTED] ?: false,
            connectOnStartup = savedStateHandle[KEY_CONNECT_ON_STARTUP] ?: false,
            quickDisconnect = savedStateHandle[KEY_QUICK_DISCONNECT] ?: false,
            keyboardSuggestions = savedStateHandle[KEY_KEYBOARD_SUGGESTIONS] ?: false,
            keyboardLayoutId = savedStateHandle[KEY_KEYBOARD_LAYOUT_ID],
            postLogin = savedStateHandle[KEY_POST_LOGIN] ?: "",
            jumpHostId = savedStateHandle[KEY_JUMP_HOST_ID],
            ipVersion = savedStateHandle[KEY_IP_VERSION] ?: "IPV4_AND_IPV6",
            hasExistingPassword = savedStateHandle[KEY_HAS_EXISTING_PASSWORD] ?: false,
        )
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

    private fun observeKeyboardLayouts() {
        viewModelScope.launch {
            keyboardLayoutRepository.observeAll()
                .catch { _uiState.update { it.copy(availableKeyboardLayouts = emptyList()) } }
                .collect { layouts ->
                    _uiState.update { it.copy(availableKeyboardLayouts = layouts) }
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

    private fun initializeNewHost() {
        val defaultProfileId = prefs.getLong("defaultProfileId", 0L).takeIf { it > 0 }
        val hostname = savedStateHandle.get<String>(NavArgs.HOSTNAME).orEmpty().trim()
        val username = savedStateHandle.get<String>(NavArgs.USERNAME).orEmpty().trim()
        val port = savedStateHandle.get<String>(NavArgs.PORT)
            ?.toIntOrNull()
            ?.takeIf { it in 1..65535 }
            ?.toString()
            ?: getDefaultPort("ssh")
        val quickConnect = getHostRepresentation(username, hostname, port, "ssh")

        _uiState.update {
            it.copy(
                quickConnect = quickConnect,
                nickname = quickConnect,
                username = username,
                hostname = hostname,
                port = port,
                profileId = defaultProfileId ?: it.profileId,
            )
        }
    }

    private fun getHostRepresentation(username: String, hostname: String, port: String, protocol: String): String {
        val defaultPort = getDefaultPort(protocol)
        return buildString {
            if (username.isNotEmpty() && protocol == "ssh") {
                append(username)
                append('@')
            }
            append(hostname)
            if (port.isNotEmpty() && port != defaultPort) {
                append(':')
                append(port)
            }
        }
    }

    private fun updateNicknameIfMatching(
        oldState: HostEditorUiState,
        newState: HostEditorUiState,
    ): HostEditorUiState {
        val newRep = getHostRepresentation(newState.username, newState.hostname, newState.port, newState.protocol)
        return if (oldState.isNicknameMatching) {
            newState.copy(nickname = newRep, quickConnect = newRep)
        } else {
            newState.copy(quickConnect = newRep)
        }
    }

    private fun loadHost() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val host = repository.findHostById(hostId)
                if (host != null) {
                    val hasPassword = securePasswordStorage.hasPassword(hostId)
                    val tempState = HostEditorUiState(
                        nickname = host.nickname,
                        protocol = host.protocol,
                        username = host.username,
                        hostname = host.hostname,
                        port = host.port.toString(),
                    )
                    val quickConnect = if (tempState.isNicknameMatching) {
                        host.nickname
                    } else {
                        getHostRepresentation(
                            host.username,
                            host.hostname,
                            host.port.toString(),
                            host.protocol,
                        )
                    }
                    _uiState.update {
                        it.copy(
                            nickname = host.nickname,
                            protocol = host.protocol,
                            username = host.username,
                            hostname = host.hostname,
                            port = host.port.toString(),
                            quickConnect = quickConnect,
                            color = host.color ?: "gray",
                            pubkeyId = host.pubkeyId,
                            profileId = host.profileId,
                            useAuthAgent = host.useAuthAgent ?: "no",
                            compression = host.compression,
                            wantSession = host.wantSession,
                            tmuxEnabled = host.tmuxMode != Host.TMUX_MODE_OFF,
                            stayConnected = host.stayConnected,
                            connectOnStartup = host.connectOnStartup,
                            quickDisconnect = host.quickDisconnect,
                            keyboardSuggestions = host.keyboardSuggestions,
                            keyboardLayoutId = host.keyboardLayoutId,
                            postLogin = host.postLogin ?: "",
                            jumpHostId = host.jumpHostId,
                            ipVersion = host.ipVersion,
                            hasExistingPassword = hasPassword,
                            isLoading = false,
                        )
                    }
                    persistEditsEnabled = true
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

    private fun parseQuickConnect(value: String): Triple<String, String, String>? {
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

    fun updateNickname(value: String, isExpanded: Boolean = false) {
        _uiState.update { it.copy(nickname = value) }

        if (isExpanded) {
            return
        }

        val parsed = parseQuickConnect(value)
        if (parsed != null) {
            val (username, hostname, port) = parsed
            _uiState.update { state ->
                val parsedPort = port.ifBlank { getDefaultPort(state.protocol) }
                val newRep = getHostRepresentation(username, hostname, parsedPort, state.protocol)
                state.copy(
                    username = username,
                    hostname = hostname,
                    port = parsedPort,
                    quickConnect = newRep,
                )
            }
        }
    }

    fun updateProtocol(value: String) {
        _uiState.update { old ->
            val updated = old.copy(protocol = value)
            updateNicknameIfMatching(old, updated)
        }
    }

    fun updateUsername(value: String) {
        _uiState.update { old ->
            val updated = old.copy(username = value)
            updateNicknameIfMatching(old, updated)
        }
    }

    fun updateHostname(value: String) {
        _uiState.update { old ->
            val updated = old.copy(hostname = value)
            updateNicknameIfMatching(old, updated)
        }
    }

    fun updatePort(value: String) {
        // Only allow numeric input
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.update { old ->
                val updated = old.copy(port = value)
                updateNicknameIfMatching(old, updated)
            }
        }
    }

    fun updateQuickConnect(value: String) {
        _uiState.update { it.copy(quickConnect = value, nickname = value) }

        val parsed = parseQuickConnect(value)
        if (parsed != null) {
            val (username, hostname, port) = parsed
            _uiState.update { state ->
                state.copy(
                    username = username,
                    hostname = hostname,
                    port = port.ifBlank { getDefaultPort(state.protocol) },
                )
            }
        }
    }

    fun updateColor(value: String) {
        _uiState.update { it.copy(color = value) }
    }

    fun updatePubkeyId(value: Long) {
        _uiState.update { it.copy(pubkeyId = value) }
    }

    fun updateProfileId(value: Long?) {
        _uiState.update { it.copy(profileId = value) }
    }

    fun updateUseAuthAgent(value: String) {
        _uiState.update { it.copy(useAuthAgent = value) }
    }

    fun updateCompression(value: Boolean) {
        _uiState.update { it.copy(compression = value) }
    }

    fun updateWantSession(value: Boolean) {
        _uiState.update { it.copy(wantSession = value) }
    }

    fun updateTmuxEnabled(value: Boolean) {
        _uiState.update { it.copy(tmuxEnabled = value) }
    }

    fun updateStayConnected(value: Boolean) {
        _uiState.update { it.copy(stayConnected = value) }
    }

    fun updateConnectOnStartup(value: Boolean) {
        _uiState.update { it.copy(connectOnStartup = value) }
    }

    fun updateQuickDisconnect(value: Boolean) {
        _uiState.update { it.copy(quickDisconnect = value) }
    }

    fun updateKeyboardSuggestions(value: Boolean) {
        _uiState.update { it.copy(keyboardSuggestions = value) }
    }

    fun updateKeyboardLayoutId(value: Long?) {
        _uiState.update { it.copy(keyboardLayoutId = value) }
    }

    fun updatePostLogin(value: String) {
        _uiState.update { it.copy(postLogin = value) }
    }

    fun updateJumpHostId(value: Long?) {
        _uiState.update { it.copy(jumpHostId = value) }
    }

    fun updateIpVersion(value: String) {
        _uiState.update { it.copy(ipVersion = value) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun clearSavedPassword() {
        _uiState.update { it.copy(password = "", hasExistingPassword = false) }
    }

    fun saveHost(useExpandedMode: Boolean) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val existingHost = if (hostId != -1L) {
                    repository.findHostById(hostId)
                } else {
                    null
                }

                // In quick connect mode, use the quickConnect string as the nickname
                val nickname = if (!useExpandedMode && state.quickConnect.isNotBlank()) {
                    state.quickConnect
                } else {
                    state.nickname
                }

                // Only SSH hosts can have a jump host
                val jumpHostId = if (state.protocol == "ssh") state.jumpHostId else null

                val host = Host(
                    id = existingHost?.id ?: 0L,
                    nickname = nickname,
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
                    tmuxMode = if (state.tmuxEnabled) Host.TMUX_MODE_AUTO else Host.TMUX_MODE_OFF,
                    stayConnected = state.stayConnected,
                    connectOnStartup = state.connectOnStartup,
                    quickDisconnect = state.quickDisconnect,
                    keyboardSuggestions = state.keyboardSuggestions,
                    keyboardLayoutId = state.keyboardLayoutId,
                    postLogin = state.postLogin.ifBlank { null },
                    lastConnect = existingHost?.lastConnect ?: System.currentTimeMillis(),
                    hostKeyAlgo = existingHost?.hostKeyAlgo,
                    useKeys = existingHost?.useKeys ?: true,
                    scrollbackLines = existingHost?.scrollbackLines ?: 140,
                    useCtrlAltAsMetaKey = existingHost?.useCtrlAltAsMetaKey ?: false,
                    tmuxLastTarget = existingHost?.tmuxLastTarget,
                    tmuxOfferDismissed = existingHost?.tmuxOfferDismissed ?: false,
                    jumpHostId = jumpHostId,
                    ipVersion = state.ipVersion,
                )

                val savedHost = repository.saveHost(host)

                // Handle password storage (only for SSH protocol)
                if (state.protocol == "ssh") {
                    if (state.password.isNotEmpty()) {
                        // Save or update the password
                        securePasswordStorage.savePassword(savedHost.id, state.password)
                    } else if (!state.hasExistingPassword) {
                        // No password entered and no existing password - ensure it's cleared
                        securePasswordStorage.deletePassword(savedHost.id)
                    }
                    // If password is empty but hasExistingPassword is true, keep existing
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to save host")
                }
            }
        }
    }

    private companion object {
        const val KEY_EDITS_SAVED = "edit_saved"
        const val KEY_QUICK_CONNECT = "edit_quickConnect"
        const val KEY_NICKNAME = "edit_nickname"
        const val KEY_PROTOCOL = "edit_protocol"
        const val KEY_USERNAME = "edit_username"
        const val KEY_HOSTNAME = "edit_hostname"
        const val KEY_PORT = "edit_port"
        const val KEY_COLOR = "edit_color"
        const val KEY_PUBKEY_ID = "edit_pubkeyId"
        const val KEY_PROFILE_ID = "edit_profileId"
        const val KEY_USE_AUTH_AGENT = "edit_useAuthAgent"
        const val KEY_COMPRESSION = "edit_compression"
        const val KEY_WANT_SESSION = "edit_wantSession"
        const val KEY_TMUX_ENABLED = "edit_tmuxEnabled"
        const val KEY_STAY_CONNECTED = "edit_stayConnected"
        const val KEY_CONNECT_ON_STARTUP = "edit_connectOnStartup"
        const val KEY_QUICK_DISCONNECT = "edit_quickDisconnect"
        const val KEY_KEYBOARD_SUGGESTIONS = "edit_keyboardSuggestions"
        const val KEY_KEYBOARD_LAYOUT_ID = "edit_keyboardLayoutId"
        const val KEY_POST_LOGIN = "edit_postLogin"
        const val KEY_JUMP_HOST_ID = "edit_jumpHostId"
        const val KEY_IP_VERSION = "edit_ipVersion"
        const val KEY_HAS_EXISTING_PASSWORD = "edit_hasExistingPassword"
    }
}
