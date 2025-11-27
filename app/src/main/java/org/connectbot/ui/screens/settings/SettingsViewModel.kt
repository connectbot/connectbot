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

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val memkeys: Boolean = true,
    val connPersist: Boolean = true,
    val wifilock: Boolean = true,
    val backupkeys: Boolean = false,
    val emulation: String = "xterm-256color",
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(loadSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadSettings(): SettingsUiState {
        return SettingsUiState(
            memkeys = prefs.getBoolean("memkeys", true),
            connPersist = prefs.getBoolean("connPersist", true),
            wifilock = prefs.getBoolean("wifilock", true),
            backupkeys = prefs.getBoolean("backupkeys", false),
            emulation = prefs.getString("emulation", "xterm-256color") ?: "xterm-256color",
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
        )
    }

    fun updateMemkeys(value: Boolean) {
        updateBooleanPref("memkeys", value) { copy(memkeys = value) }
    }

    fun updateConnPersist(value: Boolean) {
        updateBooleanPref("connPersist", value) { copy(connPersist = value) }
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

    fun updateEmulation(value: String) {
        updateStringPref("emulation", value) { copy(emulation = value) }
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

    private fun updateBooleanPref(key: String, value: Boolean, updateState: SettingsUiState.() -> SettingsUiState) {
        viewModelScope.launch {
            prefs.edit().putBoolean(key, value).apply()
            _uiState.update { it.updateState() }
        }
    }

    private fun updateStringPref(key: String, value: String, updateState: SettingsUiState.() -> SettingsUiState) {
        viewModelScope.launch {
            prefs.edit().putString(key, value).apply()
            _uiState.update { it.updateState() }
        }
    }

    private fun updateFloatPref(key: String, value: Float, updateState: SettingsUiState.() -> SettingsUiState) {
        viewModelScope.launch {
            prefs.edit().putFloat(key, value).apply()
            _uiState.update { it.updateState() }
        }
    }
}
