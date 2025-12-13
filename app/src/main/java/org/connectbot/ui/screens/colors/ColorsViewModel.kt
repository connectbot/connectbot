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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.data.ColorSchemePresets
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.data.entity.ColorScheme
import org.connectbot.util.HostConstants

data class ColorsUiState(
    val currentSchemeId: Long = -1,
    val currentSchemeName: String = "Default",
    val availableSchemes: List<ColorScheme> = emptyList(),
    val foregroundColorIndex: Int = HostConstants.DEFAULT_FG_COLOR,
    val backgroundColorIndex: Int = HostConstants.DEFAULT_BG_COLOR,
    val currentPalette: IntArray = ColorSchemePresets.default.colors,
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
        var result = currentSchemeId.toInt()
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

@HiltViewModel
class ColorsViewModel @Inject constructor(
    private val repository: ColorSchemeRepository
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
     * Switch to a different color scheme for viewing/previewing.
     * Note: Built-in schemes (negative IDs) are immutable and cannot be edited.
     * To customize a built-in scheme, create a custom scheme based on it.
     */
    fun switchToScheme(schemeId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Update UI state to display the selected scheme
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
        if (colorIndex !in 0..15) return

        val currentSchemeId = _uiState.value.currentSchemeId
        if (currentSchemeId < 0) {
            // Cannot modify built-in schemes
            _uiState.update {
                it.copy(error = "Cannot modify built-in schemes. Please create a custom scheme first.")
            }
            return
        }

        _uiState.update { it.copy(foregroundColorIndex = colorIndex) }
        saveColors()
    }

    fun updateBackgroundColor(colorIndex: Int) {
        if (colorIndex !in 0..15) return

        val currentSchemeId = _uiState.value.currentSchemeId
        if (currentSchemeId < 0) {
            // Cannot modify built-in schemes
            _uiState.update {
                it.copy(error = "Cannot modify built-in schemes. Please create a custom scheme first.")
            }
            return
        }

        _uiState.update { it.copy(backgroundColorIndex = colorIndex) }
        saveColors()
    }

    private fun saveColors() {
        viewModelScope.launch {
            try {
                val currentSchemeId = _uiState.value.currentSchemeId
                if (currentSchemeId < 0) {
                    // Should not happen due to checks above, but be defensive
                    _uiState.update { it.copy(error = "Cannot save built-in schemes") }
                    return@launch
                }

                repository.setDefaultColorsForScheme(
                    currentSchemeId,
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
                val currentSchemeId = _uiState.value.currentSchemeId
                if (currentSchemeId < 0) {
                    // Cannot reset built-in schemes - they're already defaults
                    _uiState.update {
                        it.copy(error = "Built-in schemes cannot be reset. They are already defaults.")
                    }
                    return@launch
                }

                repository.resetSchemeToDefaults(currentSchemeId)
                loadColors()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to reset colors")
                }
            }
        }
    }
}
