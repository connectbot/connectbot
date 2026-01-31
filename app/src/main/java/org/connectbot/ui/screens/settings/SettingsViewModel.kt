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

package org.connectbot.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.data.ProfileRepository
import org.connectbot.data.entity.Profile
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.LocalFontProvider
import org.connectbot.util.PreferenceConstants
import org.connectbot.util.TerminalFontProvider
import timber.log.Timber
import javax.inject.Inject

data class SettingsUiState(
    val memkeys: Boolean = true,
    val connPersist: Boolean = true,
    val wifilock: Boolean = true,
    val backupkeys: Boolean = false,
    val scrollback: String = "140",
    val rotation: String = "Default",
    val titlebarhide: Boolean = false,
    val fullscreen: Boolean = false,
    val pgupdngesture: Boolean = false,
    val volumefont: Boolean = true,
    val keepalive: Boolean = true,
    val alwaysvisible: Boolean = false,
    val shiftfkeys: Boolean = false,
    val ctrlfkeys: Boolean = false,
    val stickymodifiers: String = "no",
    val keymode: String = "none",
    val camera: String = "Ctrl+A then Space",
    val bumpyarrows: Boolean = true,
    val bell: Boolean = true,
    val bellVolume: Float = 0.5f,
    val bellVibrate: Boolean = true,
    val bellNotification: Boolean = false,
    val fontFamily: String = "SYSTEM_DEFAULT",
    val customFonts: List<String> = emptyList(),
    val customTerminalTypes: List<String> = emptyList(),
    val localFonts: List<Pair<String, String>> = emptyList(),
    val fontValidationInProgress: Boolean = false,
    val fontValidationError: String? = null,
    val fontImportInProgress: Boolean = false,
    val fontImportError: String? = null,
    val fontDownloadInProgress: Boolean = false,
    val defaultProfileId: Long = 0L,
    val availableProfiles: List<Profile> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val profileRepository: ProfileRepository,
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {
    private val fontProvider = TerminalFontProvider(context, dispatchers.io)
    private val localFontProvider = LocalFontProvider(context)
    private val _uiState = MutableStateFlow(loadSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _requestNotificationPermission = Channel<Unit>(Channel.CONFLATED)
    val requestNotificationPermission = _requestNotificationPermission.receiveAsFlow()

    private val _showPermissionDeniedDialog = Channel<Unit>(Channel.CONFLATED)
    val showPermissionDeniedDialog = _showPermissionDeniedDialog.receiveAsFlow()

    // Persist permission denial state in SharedPreferences
    private var wasPermissionDenied: Boolean
        get() = prefs.getBoolean(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED, false)
        set(value) {
            prefs.edit { putBoolean(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED, value) }
        }

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            val profiles = profileRepository.getAll()
            _uiState.update { it.copy(availableProfiles = profiles) }
        }
    }

    private fun loadSettings(): SettingsUiState {
        val customFontsString = prefs.getString("customFonts", "") ?: ""
        val customFonts = if (customFontsString.isBlank()) {
            emptyList()
        } else {
            customFontsString.split(",").filter { it.isNotBlank() }
        }
        val customTerminalTypesString = prefs.getString("customTerminalTypes", "") ?: ""
        val customTerminalTypes = if (customTerminalTypesString.isBlank()) {
            emptyList()
        } else {
            customTerminalTypesString.split(",").filter { it.isNotBlank() }
        }
        val localFonts = localFontProvider.getImportedFonts()

        return SettingsUiState(
            memkeys = prefs.getBoolean("memkeys", true),
            connPersist = prefs.getBoolean(PreferenceConstants.CONNECTION_PERSIST, true),
            wifilock = prefs.getBoolean("wifilock", true),
            backupkeys = prefs.getBoolean("backupkeys", false),
            scrollback = prefs.getString("scrollback", "140") ?: "140",
            rotation = prefs.getString("rotation", "Default") ?: "Default",
            titlebarhide = prefs.getBoolean("titlebarhide", false),
            fullscreen = prefs.getBoolean("fullscreen", false),
            pgupdngesture = prefs.getBoolean("pgupdngesture", false),
            volumefont = prefs.getBoolean("volumefont", true),
            keepalive = prefs.getBoolean("keepalive", true),
            alwaysvisible = prefs.getBoolean("alwaysvisible", false),
            shiftfkeys = prefs.getBoolean("shiftfkeys", false),
            ctrlfkeys = prefs.getBoolean("ctrlfkeys", false),
            stickymodifiers = prefs.getString("stickymodifiers", "no") ?: "no",
            keymode = prefs.getString("keymode", "none") ?: "none",
            camera = prefs.getString("camera", "Ctrl+A then Space") ?: "Ctrl+A then Space",
            bumpyarrows = prefs.getBoolean("bumpyarrows", true),
            bell = prefs.getBoolean("bell", true),
            bellVolume = prefs.getFloat("bellVolume", 0.5f),
            bellVibrate = prefs.getBoolean("bellVibrate", true),
            bellNotification = prefs.getBoolean("bellNotification", false),
            fontFamily = prefs.getString("fontFamily", "SYSTEM_DEFAULT") ?: "SYSTEM_DEFAULT",
            customFonts = customFonts,
            customTerminalTypes = customTerminalTypes,
            localFonts = localFonts,
            defaultProfileId = prefs.getLong("defaultProfileId", 0L),
        )
    }

    fun updateMemkeys(value: Boolean) {
        updateBooleanPref("memkeys", value) { copy(memkeys = value) }
    }

    fun updateConnPersist(value: Boolean) {
        // If turning ON (from OFF), request notification permission
        val currentValue = _uiState.value.connPersist
        if (!currentValue && value) {
            // Turning ON - check if permission was previously denied
            if (wasPermissionDenied) {
                // Permission was denied before, show dialog to go to settings
                viewModelScope.launch {
                    _showPermissionDeniedDialog.send(Unit)
                }
            } else {
                // First time or permission not denied yet - optimistically update to ON
                // and request permission. If denied, onNotificationPermissionResult will revert to OFF.
                updateBooleanPref(PreferenceConstants.CONNECTION_PERSIST, true) { copy(connPersist = true) }
                viewModelScope.launch {
                    _requestNotificationPermission.send(Unit)
                }
            }
        } else {
            // Turning OFF or already ON - just update the preference
            updateBooleanPref(PreferenceConstants.CONNECTION_PERSIST, value) { copy(connPersist = value) }
        }
    }

    /**
     * Called with the result of the notification permission request.
     * If permission is granted, enable connPersist. If denied, keep it OFF.
     */
    fun onNotificationPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            Timber.d("Notification permission granted, enabling connPersist")
            wasPermissionDenied = false
            updateBooleanPref(PreferenceConstants.CONNECTION_PERSIST, true) { copy(connPersist = true) }
        } else {
            // Permission denied - keep it OFF and mark as denied
            Timber.d("Notification permission denied, keeping connPersist OFF")
            wasPermissionDenied = true
            updateBooleanPref(PreferenceConstants.CONNECTION_PERSIST, false) { copy(connPersist = false) }
        }
    }

    fun updateWifilock(value: Boolean) {
        updateBooleanPref("wifilock", value) { copy(wifilock = value) }
    }

    fun updateBackupkeys(value: Boolean) {
        updateBooleanPref("backupkeys", value) { copy(backupkeys = value) }
    }

    fun updateFullscreen(value: Boolean) {
        updateBooleanPref("fullscreen", value) { copy(fullscreen = value) }
    }

    fun updateKeepAlive(value: Boolean) {
        updateBooleanPref("keepalive", value) { copy(keepalive = value) }
    }

    fun updateBell(value: Boolean) {
        updateBooleanPref("bell", value) { copy(bell = value) }
    }

    fun updateBellVibrate(value: Boolean) {
        updateBooleanPref("bellVibrate", value) { copy(bellVibrate = value) }
    }

    fun updateBellNotification(value: Boolean) {
        updateBooleanPref("bellNotification", value) { copy(bellNotification = value) }
    }

    fun updateTitleBarHide(value: Boolean) {
        updateBooleanPref("titlebarhide", value) { copy(titlebarhide = value) }
    }

    fun updatePgUpDnGesture(value: Boolean) {
        updateBooleanPref("pgupdngesture", value) { copy(pgupdngesture = value) }
    }

    fun updateVolumeFont(value: Boolean) {
        updateBooleanPref("volumefont", value) { copy(volumefont = value) }
    }

    fun updateAlwaysVisible(value: Boolean) {
        updateBooleanPref("alwaysvisible", value) { copy(alwaysvisible = value) }
    }

    fun updateShiftFkeys(value: Boolean) {
        updateBooleanPref("shiftfkeys", value) { copy(shiftfkeys = value) }
    }

    fun updateCtrlFkeys(value: Boolean) {
        updateBooleanPref("ctrlfkeys", value) { copy(ctrlfkeys = value) }
    }

    fun updateBumpyArrows(value: Boolean) {
        updateBooleanPref("bumpyarrows", value) { copy(bumpyarrows = value) }
    }

    fun updateScrollback(value: String) {
        updateStringPref("scrollback", value) { copy(scrollback = value) }
    }

    fun updateStickyModifiers(value: String) {
        updateStringPref("stickymodifiers", value) { copy(stickymodifiers = value) }
    }

    fun updateKeyMode(value: String) {
        updateStringPref("keymode", value) { copy(keymode = value) }
    }

    fun updateCamera(value: String) {
        updateStringPref("camera", value) { copy(camera = value) }
    }

    fun updateRotation(value: String) {
        updateStringPref("rotation", value) { copy(rotation = value) }
    }

    fun updateBellVolume(value: Float) {
        updateFloatPref("bellVolume", value) { copy(bellVolume = value) }
    }

    fun updateFontFamily(value: String) {
        updateStringPref("fontFamily", value) { copy(fontFamily = value) }
        // Preload the font so it's cached when the Terminal opens
        preloadFont(value)
    }

    fun updateDefaultProfile(profileId: Long) {
        viewModelScope.launch {
            prefs.edit { putLong("defaultProfileId", profileId) }
            _uiState.update { it.copy(defaultProfileId = profileId) }
        }
    }

    fun addCustomTerminalType(terminalType: String) {
        if (terminalType.isBlank()) return
        val currentTypes = _uiState.value.customTerminalTypes
        if (currentTypes.contains(terminalType)) return

        viewModelScope.launch {
            val updatedTypes = currentTypes + terminalType
            val typesString = updatedTypes.joinToString(",")
            prefs.edit { putString("customTerminalTypes", typesString) }
            _uiState.update { it.copy(customTerminalTypes = updatedTypes) }
        }
    }

    fun removeCustomTerminalType(terminalType: String) {
        viewModelScope.launch {
            val currentTypes = _uiState.value.customTerminalTypes.toMutableList()
            if (currentTypes.remove(terminalType)) {
                val typesString = currentTypes.joinToString(",")
                prefs.edit { putString("customTerminalTypes", typesString) }
                _uiState.update { it.copy(customTerminalTypes = currentTypes) }
            }
        }
    }

    private fun preloadFont(storedValue: String) {
        if (LocalFontProvider.isLocalFont(storedValue)) return
        val googleFontName = org.connectbot.util.TerminalFont.getGoogleFontName(storedValue)
        if (googleFontName.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(fontDownloadInProgress = true) }
            fontProvider.loadFontByNameSuspend(googleFontName)
            _uiState.update { it.copy(fontDownloadInProgress = false) }
        }
    }

    fun addCustomFont(fontName: String) {
        if (fontName.isBlank()) return
        val currentFonts = _uiState.value.customFonts
        if (currentFonts.contains(fontName)) {
            _uiState.update { it.copy(fontValidationError = "Font already added") }
            return
        }

        // Validate font by attempting to load it
        _uiState.update { it.copy(fontValidationInProgress = true, fontValidationError = null) }

        fontProvider.loadFontByName(fontName) { typeface ->
            viewModelScope.launch {
                if (typeface != Typeface.MONOSPACE) {
                    // Font loaded successfully, add it to the list
                    val updatedFonts = currentFonts + fontName
                    val fontsString = updatedFonts.joinToString(",")
                    prefs.edit { putString("customFonts", fontsString) }
                    _uiState.update {
                        it.copy(
                            customFonts = updatedFonts,
                            fontValidationInProgress = false,
                            fontValidationError = null
                        )
                    }
                } else {
                    // Font failed to load
                    _uiState.update {
                        it.copy(
                            fontValidationInProgress = false,
                            fontValidationError = "Font not found in Google Fonts"
                        )
                    }
                }
            }
        }
    }

    fun clearFontValidationError() {
        _uiState.update { it.copy(fontValidationError = null) }
    }

    fun removeCustomFont(fontName: String) {
        viewModelScope.launch {
            val currentFonts = _uiState.value.customFonts.toMutableList()
            if (currentFonts.remove(fontName)) {
                val fontsString = currentFonts.joinToString(",")
                prefs.edit { putString("customFonts", fontsString) }
                _uiState.update { it.copy(customFonts = currentFonts) }

                // If the removed font was the selected font, reset to system default
                if (_uiState.value.fontFamily == "custom:$fontName") {
                    updateFontFamily("SYSTEM_DEFAULT")
                }
            }
        }
    }

    fun importLocalFont(uri: Uri, displayName: String) {
        if (displayName.isBlank()) return

        _uiState.update { it.copy(fontImportInProgress = true, fontImportError = null) }

        viewModelScope.launch {
            val fileName = withContext(dispatchers.io) {
                localFontProvider.importFont(uri, displayName)
            }

            if (fileName != null) {
                val updatedLocalFonts = localFontProvider.getImportedFonts()
                _uiState.update {
                    it.copy(
                        localFonts = updatedLocalFonts,
                        fontImportInProgress = false,
                        fontImportError = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        fontImportInProgress = false,
                        fontImportError = "Failed to import font"
                    )
                }
            }
        }
    }

    fun deleteLocalFont(fileName: String) {
        viewModelScope.launch {
            val deleted = withContext(dispatchers.io) {
                localFontProvider.deleteFont(fileName)
            }

            if (deleted) {
                val updatedLocalFonts = localFontProvider.getImportedFonts()
                _uiState.update { it.copy(localFonts = updatedLocalFonts) }

                // If the removed font was the selected font, reset to system default
                if (_uiState.value.fontFamily == "${LocalFontProvider.LOCAL_PREFIX}$fileName") {
                    updateFontFamily("SYSTEM_DEFAULT")
                }
            }
        }
    }

    fun clearFontImportError() {
        _uiState.update { it.copy(fontImportError = null) }
    }

    private fun updateBooleanPref(key: String, value: Boolean, updateState: SettingsUiState.() -> SettingsUiState) {
        viewModelScope.launch {
            prefs.edit { putBoolean(key, value) }
            _uiState.update { it.updateState() }
        }
    }

    private fun updateStringPref(key: String, value: String, updateState: SettingsUiState.() -> SettingsUiState) {
        viewModelScope.launch {
            prefs.edit { putString(key, value) }
            _uiState.update { it.updateState() }
        }
    }

    private fun updateFloatPref(key: String, value: Float, updateState: SettingsUiState.() -> SettingsUiState) {
        viewModelScope.launch {
            prefs.edit { putFloat(key, value) }
            _uiState.update { it.updateState() }
        }
    }
}
