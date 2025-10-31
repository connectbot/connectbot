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

package org.connectbot.ui.screens.colors

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.data.ColorScheme
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.util.Colors
import org.connectbot.util.HostDatabase

data class ColorsUiState(
    val currentSchemeId: Int = ColorScheme.DEFAULT_SCHEME_ID,
    val currentSchemeName: String = "Default",
    val availableSchemes: List<ColorScheme> = emptyList(),
    val foregroundColorIndex: Int = HostDatabase.DEFAULT_FG_COLOR,
    val backgroundColorIndex: Int = HostDatabase.DEFAULT_BG_COLOR,
    val currentPalette: IntArray = Colors.defaults,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ColorsUiState

        if (currentSchemeId != other.currentSchemeId) return false
        if (currentSchemeName != other.currentSchemeName) return false
        if (availableSchemes != other.availableSchemes) return false
        if (foregroundColorIndex != other.foregroundColorIndex) return false
        if (backgroundColorIndex != other.backgroundColorIndex) return false
        if (!currentPalette.contentEquals(other.currentPalette)) return false
        if (isLoading != other.isLoading) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = currentSchemeId
        result = 31 * result + currentSchemeName.hashCode()
        result = 31 * result + availableSchemes.hashCode()
        result = 31 * result + foregroundColorIndex
        result = 31 * result + backgroundColorIndex
        result = 31 * result + currentPalette.contentHashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        return result
    }
}

class ColorsViewModel(
    private val context: Context,
    private val repository: ColorSchemeRepository = ColorSchemeRepository.get(context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(ColorsUiState())
    val uiState: StateFlow<ColorsUiState> = _uiState.asStateFlow()

    init {
        loadAvailableSchemes()
        loadColors()
    }

    private fun loadAvailableSchemes() {
        viewModelScope.launch {
            try {
                val schemes = repository.getAllSchemes()
                _uiState.update { it.copy(availableSchemes = schemes) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to load schemes")
                }
            }
        }
    }

    private fun loadColors() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val currentSchemeId = _uiState.value.currentSchemeId
                val defaults = repository.getSchemeDefaults(currentSchemeId)
                val palette = repository.getSchemeColors(currentSchemeId)
                val schemeName = _uiState.value.availableSchemes
                    .find { it.id == currentSchemeId }?.name ?: "Default"

                _uiState.update {
                    it.copy(
                        foregroundColorIndex = defaults.first,
                        backgroundColorIndex = defaults.second,
                        currentPalette = palette,
                        currentSchemeName = schemeName,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load colors"
                    )
                }
            }
        }
    }

    /**
     * Switch to a different color scheme.
     */
    fun switchToScheme(schemeId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Load the scheme into the global default (scheme 0)
                repository.loadScheme(schemeId, ColorScheme.DEFAULT_SCHEME_ID)

                // Update UI state
                _uiState.update { it.copy(currentSchemeId = schemeId) }
                loadColors()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to switch scheme"
                    )
                }
            }
        }
    }

    fun updateForegroundColor(colorIndex: Int) {
        if (colorIndex < 0 || colorIndex >= Colors.defaults.size) return

        _uiState.update { it.copy(foregroundColorIndex = colorIndex) }
        saveColors()
    }

    fun updateBackgroundColor(colorIndex: Int) {
        if (colorIndex < 0 || colorIndex >= Colors.defaults.size) return

        _uiState.update { it.copy(backgroundColorIndex = colorIndex) }
        saveColors()
    }

    private fun saveColors() {
        viewModelScope.launch {
            try {
                repository.setDefaultColorsForScheme(
                    HostDatabase.DEFAULT_COLOR_SCHEME,
                    _uiState.value.foregroundColorIndex,
                    _uiState.value.backgroundColorIndex
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to save colors")
                }
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            try {
                repository.resetSchemeToDefaults(ColorScheme.DEFAULT_SCHEME_ID)
                loadColors()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to reset colors")
                }
            }
        }
    }
}
