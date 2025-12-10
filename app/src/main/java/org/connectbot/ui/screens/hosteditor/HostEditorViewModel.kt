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

package org.connectbot.ui.screens.hosteditor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.data.HostRepository
import org.connectbot.data.ProfileRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Profile
import org.connectbot.data.entity.Pubkey
import org.connectbot.util.LocalFontProvider
import org.connectbot.util.TerminalFont
import org.connectbot.util.TerminalFontProvider

data class HostEditorUiState(
    val hostId: Long = -1L,
    val quickConnect: String = "",
    val nickname: String = "",
    val protocol: String = "ssh",
    val username: String = "",
    val hostname: String = "",
    val port: String = "22",
    val color: String = "gray",
    val fontSize: Int = 10,
    val fontFamily: String? = null,
    val customFonts: List<String> = emptyList(),
    val localFonts: List<Pair<String, String>> = emptyList(),
    val pubkeyId: Long = -1L,
    val availablePubkeys: List<Pubkey> = emptyList(),
    val profileId: Long? = null,
    val availableProfiles: List<Profile> = emptyList(),
    val delKey: String = "del",
    val encoding: String = "UTF-8",
    val useAuthAgent: String = "no",
    val compression: Boolean = false,
    val wantSession: Boolean = true,
    val stayConnected: Boolean = false,
    val quickDisconnect: Boolean = false,
    val postLogin: String = "",
    val jumpHostId: Long? = null,
    val availableJumpHosts: List<Host> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HostEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: HostRepository,
    private val pubkeyRepository: PubkeyRepository,
    private val profileRepository: ProfileRepository,
    private val prefs: android.content.SharedPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val hostId: Long = savedStateHandle.get<Long>("hostId") ?: -1L
    private val localFontProvider = LocalFontProvider(context)
    private val fontProvider = TerminalFontProvider(context)
    private val _uiState = MutableStateFlow(HostEditorUiState(hostId = hostId))
    val uiState: StateFlow<HostEditorUiState> = _uiState.asStateFlow()

    init {
        loadPubkeys()
        loadJumpHosts()
        loadProfiles()
        loadCustomFonts()
        loadLocalFonts()
        if (hostId != -1L) {
            loadHost()
        } else {
            // For new hosts, apply the default profile from settings
            val defaultProfileId = prefs.getLong("defaultProfileId", 0L)
            if (defaultProfileId > 0) {
                _uiState.update { it.copy(profileId = defaultProfileId) }
            }
        }
    }

    private fun loadCustomFonts() {
        val customFontsString = prefs.getString("customFonts", "") ?: ""
        val customFonts = if (customFontsString.isBlank()) {
            emptyList()
        } else {
            customFontsString.split(",").filter { it.isNotBlank() }
        }
        _uiState.update { it.copy(customFonts = customFonts) }
    }

    private fun loadLocalFonts() {
        val localFonts = localFontProvider.getImportedFonts()
        _uiState.update { it.copy(localFonts = localFonts) }
    }

    private fun loadPubkeys() {
        viewModelScope.launch {
            try {
                val pubkeys = pubkeyRepository.getAll()
                _uiState.update { it.copy(availablePubkeys = pubkeys) }
            } catch (e: Exception) {
                // Don't fail the whole screen if pubkeys can't be loaded
                _uiState.update { it.copy(availablePubkeys = emptyList()) }
            }
        }
    }

    private fun loadJumpHosts() {
        viewModelScope.launch {
            try {
                // Get all SSH hosts that can be used as jump hosts
                // Exclude the current host being edited to prevent circular references
                val sshHosts = repository.getSshHosts()
                    .filter { it.id != hostId }
                _uiState.update { it.copy(availableJumpHosts = sshHosts) }
            } catch (e: Exception) {
                // Don't fail the whole screen if jump hosts can't be loaded
                _uiState.update { it.copy(availableJumpHosts = emptyList()) }
            }
        }
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            try {
                val profiles = profileRepository.getAll()
                _uiState.update { it.copy(availableProfiles = profiles) }
            } catch (e: Exception) {
                // Don't fail the whole screen if profiles can't be loaded
                _uiState.update { it.copy(availableProfiles = emptyList()) }
            }
        }
    }

    private fun loadHost() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val host = repository.findHostById(hostId)
                if (host != null) {
                    _uiState.update {
                        it.copy(
                            nickname = host.nickname,
                            protocol = host.protocol,
                            username = host.username,
                            hostname = host.hostname,
                            port = host.port.toString(),
                            color = host.color ?: "gray",
                            fontSize = host.fontSize,
                            fontFamily = host.fontFamily,
                            pubkeyId = host.pubkeyId,
                            profileId = host.profileId,
                            delKey = host.delKey,
                            encoding = host.encoding,
                            useAuthAgent = host.useAuthAgent ?: "no",
                            compression = host.compression,
                            wantSession = host.wantSession,
                            stayConnected = host.stayConnected,
                            quickDisconnect = host.quickDisconnect,
                            postLogin = host.postLogin ?: "",
                            jumpHostId = host.jumpHostId,
                            isLoading = false
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

    fun updateNickname(value: String) {
        _uiState.update { it.copy(nickname = value) }
    }

    fun updateProtocol(value: String) {
        _uiState.update { it.copy(protocol = value) }
    }

    fun updateUsername(value: String) {
        _uiState.update { it.copy(username = value) }
    }

    fun updateHostname(value: String) {
        _uiState.update { it.copy(hostname = value) }
    }

    fun updatePort(value: String) {
        // Only allow numeric input
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.update { it.copy(port = value) }
        }
    }

    fun updateQuickConnect(value: String) {
        _uiState.update { it.copy(quickConnect = value) }

        // Parse quick connect string: [user@]hostname[:port]
        val regex = Regex("^(?:([^@]+)@)?([^:]+)(?::(\\d+))?$")
        val match = regex.find(value)

        if (match != null) {
            val (username, hostname, port) = match.destructured
            _uiState.update {
                it.copy(
                    username = username.ifBlank { "" },
                    hostname = hostname,
                    port = port.ifBlank { "22" }
                )
            }
        }
    }

    fun updateColor(value: String) {
        _uiState.update { it.copy(color = value) }
    }

    fun updateFontSize(value: Int) {
        _uiState.update { it.copy(fontSize = value) }
    }

    fun updateFontFamily(value: String?) {
        _uiState.update { it.copy(fontFamily = value) }
        // Preload the font so it's cached when the Terminal opens
        if (value != null) {
            preloadFont(value)
        }
    }

    private fun preloadFont(storedValue: String) {
        // Skip if it's a local font (already on device) or system default
        if (LocalFontProvider.isLocalFont(storedValue)) return
        val googleFontName = TerminalFont.getGoogleFontName(storedValue)
        if (googleFontName.isBlank()) return

        // Trigger font download/caching in background
        fontProvider.loadFontByName(googleFontName) { /* just cache it */ }
    }

    fun updatePubkeyId(value: Long) {
        _uiState.update { it.copy(pubkeyId = value) }
    }

    fun updateProfileId(value: Long?) {
        _uiState.update { it.copy(profileId = value) }
    }

    fun updateDelKey(value: String) {
        _uiState.update { it.copy(delKey = value) }
    }

    fun updateEncoding(value: String) {
        _uiState.update { it.copy(encoding = value) }
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

    fun updateStayConnected(value: Boolean) {
        _uiState.update { it.copy(stayConnected = value) }
    }

    fun updateQuickDisconnect(value: Boolean) {
        _uiState.update { it.copy(quickDisconnect = value) }
    }

    fun updatePostLogin(value: String) {
        _uiState.update { it.copy(postLogin = value) }
    }

    fun updateJumpHostId(value: Long?) {
        _uiState.update { it.copy(jumpHostId = value) }
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
                    port = state.port.toIntOrNull() ?: 22,
                    color = state.color.takeIf { it != "gray" },
                    fontSize = state.fontSize,
                    fontFamily = state.fontFamily,
                    pubkeyId = state.pubkeyId,
                    profileId = state.profileId,
                    delKey = state.delKey,
                    encoding = state.encoding,
                    useAuthAgent = state.useAuthAgent.takeIf { it != "no" },
                    compression = state.compression,
                    wantSession = state.wantSession,
                    stayConnected = state.stayConnected,
                    quickDisconnect = state.quickDisconnect,
                    postLogin = state.postLogin.ifBlank { null },
                    lastConnect = existingHost?.lastConnect ?: System.currentTimeMillis(),
                    hostKeyAlgo = existingHost?.hostKeyAlgo,
                    useKeys = existingHost?.useKeys ?: true,
                    colorSchemeId = existingHost?.colorSchemeId ?: 1L,
                    scrollbackLines = existingHost?.scrollbackLines ?: 140,
                    useCtrlAltAsMetaKey = existingHost?.useCtrlAltAsMetaKey ?: false,
                    jumpHostId = jumpHostId
                )

                repository.saveHost(host)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to save host")
                }
            }
        }
    }
}
