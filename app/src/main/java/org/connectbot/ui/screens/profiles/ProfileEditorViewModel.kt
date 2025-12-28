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

package org.connectbot.ui.screens.profiles

import android.content.Context
import android.content.SharedPreferences
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
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.data.ProfileRepository
import org.connectbot.data.entity.ColorScheme
import org.connectbot.data.entity.Profile
import org.connectbot.util.LocalFontProvider
import org.connectbot.util.TerminalFont
import org.connectbot.util.TerminalFontProvider

data class ProfileEditorUiState(
    val profileId: Long = -1L,
    val name: String = "",
    val iconColor: String? = null,
    val colorSchemeId: Long = -1L,
    val availableColorSchemes: List<ColorScheme> = emptyList(),
    val fontFamily: String? = null,
    val fontSize: Int = 10,
    val delKey: String = "del",
    val encoding: String = "UTF-8",
    val emulation: String = "xterm-256color",
    val customTerminalTypes: List<String> = emptyList(),
    val customFonts: List<String> = emptyList(),
    val localFonts: List<Pair<String, String>> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveError: String? = null
)

@HiltViewModel
class ProfileEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profileRepository: ProfileRepository,
    private val colorSchemeRepository: ColorSchemeRepository,
    private val prefs: SharedPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val profileId: Long = savedStateHandle.get<Long>("profileId") ?: -1L
    private val localFontProvider = LocalFontProvider(context)
    private val fontProvider = TerminalFontProvider(context)

    private val _uiState = MutableStateFlow(ProfileEditorUiState(profileId = profileId))
    val uiState: StateFlow<ProfileEditorUiState> = _uiState.asStateFlow()

    init {
        loadCustomFonts()
        loadLocalFonts()
        loadCustomTerminalTypes()
        loadColorSchemes()
        if (profileId != -1L) {
            loadProfile()
        } else {
            _uiState.update { it.copy(isLoading = false) }
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

    private fun loadCustomTerminalTypes() {
        val customTerminalTypesString = prefs.getString("customTerminalTypes", "") ?: ""
        val customTerminalTypes = if (customTerminalTypesString.isBlank()) {
            emptyList()
        } else {
            customTerminalTypesString.split(",").filter { it.isNotBlank() }
        }
        _uiState.update { it.copy(customTerminalTypes = customTerminalTypes) }
    }

    private fun loadLocalFonts() {
        val localFonts = localFontProvider.getImportedFonts()
        _uiState.update { it.copy(localFonts = localFonts) }
    }

    private fun loadColorSchemes() {
        viewModelScope.launch {
            try {
                val schemes = colorSchemeRepository.getAllSchemes()
                _uiState.update { it.copy(availableColorSchemes = schemes) }
            } catch (e: Exception) {
                _uiState.update { it.copy(availableColorSchemes = emptyList()) }
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val profile = profileRepository.getById(profileId)
            if (profile != null) {
                _uiState.update {
                    it.copy(
                        name = profile.name,
                        iconColor = profile.iconColor,
                        colorSchemeId = profile.colorSchemeId,
                        fontFamily = profile.fontFamily,
                        fontSize = profile.fontSize,
                        delKey = profile.delKey,
                        encoding = profile.encoding,
                        emulation = profile.emulation,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value, saveError = null) }
    }

    fun updateIconColor(value: String?) {
        _uiState.update { it.copy(iconColor = value) }
    }

    fun updateColorSchemeId(value: Long) {
        _uiState.update { it.copy(colorSchemeId = value) }
    }

    fun updateFontFamily(value: String?) {
        _uiState.update { it.copy(fontFamily = value) }
        // Preload the font
        if (value != null) {
            preloadFont(value)
        }
    }

    private fun preloadFont(storedValue: String) {
        if (LocalFontProvider.isLocalFont(storedValue)) return
        val googleFontName = TerminalFont.getGoogleFontName(storedValue)
        if (googleFontName.isBlank()) return
        fontProvider.loadFontByName(googleFontName) { /* just cache it */ }
    }

    fun updateFontSize(value: Int) {
        _uiState.update { it.copy(fontSize = value) }
    }

    fun updateDelKey(value: String) {
        _uiState.update { it.copy(delKey = value) }
    }

    fun updateEncoding(value: String) {
        _uiState.update { it.copy(encoding = value) }
    }

    fun updateEmulation(value: String) {
        _uiState.update { it.copy(emulation = value) }
    }

    fun addCustomTerminalType(terminalType: String) {
        if (terminalType.isBlank()) return
        val currentTypes = _uiState.value.customTerminalTypes
        if (currentTypes.contains(terminalType)) return

        viewModelScope.launch {
            val updatedTypes = currentTypes + terminalType
            val typesString = updatedTypes.joinToString(",")
            prefs.edit().putString("customTerminalTypes", typesString).apply()
            _uiState.update { it.copy(customTerminalTypes = updatedTypes) }
        }
    }

    fun removeCustomTerminalType(terminalType: String) {
        viewModelScope.launch {
            val currentTypes = _uiState.value.customTerminalTypes.toMutableList()
            if (currentTypes.remove(terminalType)) {
                val typesString = currentTypes.joinToString(",")
                prefs.edit().putString("customTerminalTypes", typesString).apply()
                _uiState.update { it.copy(customTerminalTypes = currentTypes) }

                // If the removed type was the selected emulation, reset to default
                if (_uiState.value.emulation == terminalType) {
                    updateEmulation("xterm-256color")
                }
            }
        }
    }

    fun save(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value

            if (state.name.isBlank()) {
                _uiState.update { it.copy(saveError = "Name cannot be empty") }
                return@launch
            }

            // Check for duplicate name (excluding current profile)
            val excludeId = if (profileId != -1L) profileId else null
            if (profileRepository.nameExists(state.name, excludeId)) {
                _uiState.update { it.copy(saveError = "A profile with this name already exists") }
                return@launch
            }

            _uiState.update { it.copy(isSaving = true) }

            val profile = Profile(
                id = if (profileId != -1L) profileId else 0,
                name = state.name,
                iconColor = state.iconColor,
                colorSchemeId = state.colorSchemeId,
                fontFamily = state.fontFamily,
                fontSize = state.fontSize,
                delKey = state.delKey,
                encoding = state.encoding,
                emulation = state.emulation
            )

            profileRepository.save(profile)
            _uiState.update { it.copy(isSaving = false) }
            onSuccess()
        }
    }
}
