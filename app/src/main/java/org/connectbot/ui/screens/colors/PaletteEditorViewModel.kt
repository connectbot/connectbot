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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.data.ColorSchemePresets
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.HostConstants
import javax.inject.Inject

data class PaletteEditorUiState(
    val schemeId: Long = -1,
    val schemeName: String = "",
    val schemeDescription: String = "",
    val palette: IntArray = ColorSchemePresets.default.colors,
    val editingColorIndex: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showResetAllDialog: Boolean = false,
    val foregroundColorIndex: Int = HostConstants.DEFAULT_FG_COLOR,
    val backgroundColorIndex: Int = HostConstants.DEFAULT_BG_COLOR,
    val isBuiltIn: Boolean = false,
    val showDuplicateDialog: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PaletteEditorUiState

        if (schemeId != other.schemeId) return false
        if (schemeName != other.schemeName) return false
        if (schemeDescription != other.schemeDescription) return false
        if (!palette.contentEquals(other.palette)) return false
        if (editingColorIndex != other.editingColorIndex) return false
        if (isLoading != other.isLoading) return false
        if (error != other.error) return false
        if (showResetAllDialog != other.showResetAllDialog) return false
        if (foregroundColorIndex != other.foregroundColorIndex) return false
        if (backgroundColorIndex != other.backgroundColorIndex) return false
        if (isBuiltIn != other.isBuiltIn) return false
        if (showDuplicateDialog != other.showDuplicateDialog) return false

        return true
    }

    override fun hashCode(): Int {
        var result = schemeId.toInt()
        result = 31 * result + schemeName.hashCode()
        result = 31 * result + schemeDescription.hashCode()
        result = 31 * result + palette.contentHashCode()
        result = 31 * result + (editingColorIndex ?: 0)
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + showResetAllDialog.hashCode()
        result = 31 * result + foregroundColorIndex
        result = 31 * result + backgroundColorIndex
        result = 31 * result + isBuiltIn.hashCode()
        result = 31 * result + showDuplicateDialog.hashCode()
        return result
    }
}

@HiltViewModel
class PaletteEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ColorSchemeRepository,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {

    private val schemeId = savedStateHandle.get<Long>("schemeId") ?: 0
    private val _uiState = MutableStateFlow(PaletteEditorUiState(schemeId = schemeId))
    val uiState: StateFlow<PaletteEditorUiState> = _uiState.asStateFlow()

    private val _navigateToDuplicate = MutableSharedFlow<Long>()
    val navigateToDuplicate: SharedFlow<Long> = _navigateToDuplicate.asSharedFlow()

    init {
        loadPalette()
    }

    private fun loadPalette() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val palette = repository.getSchemeColors(schemeId)
                val schemes = repository.getAllSchemes()
                val scheme = schemes.find { it.id == schemeId }
                val defaults = repository.getSchemeDefaults(schemeId)

                _uiState.update {
                    it.copy(
                        palette = palette,
                        schemeName = scheme?.name ?: "Unknown",
                        schemeDescription = scheme?.description ?: "",
                        foregroundColorIndex = defaults.first,
                        backgroundColorIndex = defaults.second,
                        isBuiltIn = scheme?.isBuiltIn ?: false,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load palette"
                    )
                }
            }
        }
    }

    fun editColor(colorIndex: Int) {
        _uiState.update { it.copy(editingColorIndex = colorIndex) }
    }

    fun closeColorEditor() {
        _uiState.update { it.copy(editingColorIndex = null) }
    }

    fun updateColor(colorIndex: Int, newColor: Int) {
        viewModelScope.launch {
            try {
                // Update in database
                repository.setColorForScheme(schemeId, colorIndex, newColor)

                // Update in local state
                val newPalette = _uiState.value.palette.clone()
                newPalette[colorIndex] = newColor

                _uiState.update {
                    it.copy(
                        palette = newPalette,
                        editingColorIndex = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to update color")
                }
            }
        }
    }

    fun resetColor(colorIndex: Int) {
        viewModelScope.launch {
            try {
                // Get the default color
                val defaultColor = ColorSchemePresets.default.colors[colorIndex]

                // Update in database
                repository.setColorForScheme(schemeId, colorIndex, defaultColor)

                // Update in local state
                val newPalette = _uiState.value.palette.clone()
                newPalette[colorIndex] = defaultColor

                _uiState.update { it.copy(palette = newPalette) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to reset color")
                }
            }
        }
    }

    fun showResetAllDialog() {
        _uiState.update { it.copy(showResetAllDialog = true) }
    }

    fun hideResetAllDialog() {
        _uiState.update { it.copy(showResetAllDialog = false) }
    }

    fun resetAllColors() {
        viewModelScope.launch {
            try {
                // Reset to defaults in database
                repository.resetSchemeToDefaults(schemeId)

                // Reload palette
                loadPalette()

                _uiState.update { it.copy(showResetAllDialog = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to reset palette",
                        showResetAllDialog = false
                    )
                }
            }
        }
    }

    fun updateForegroundColor(colorIndex: Int) {
        if (_uiState.value.isBuiltIn) {
            _uiState.update { it.copy(error = "Cannot modify built-in schemes. Duplicate to customize.") }
            return
        }
        _uiState.update { it.copy(foregroundColorIndex = colorIndex) }
        saveFgBg()
    }

    fun updateBackgroundColor(colorIndex: Int) {
        if (_uiState.value.isBuiltIn) {
            _uiState.update { it.copy(error = "Cannot modify built-in schemes. Duplicate to customize.") }
            return
        }
        _uiState.update { it.copy(backgroundColorIndex = colorIndex) }
        saveFgBg()
    }

    private fun saveFgBg() {
        viewModelScope.launch {
            try {
                repository.setDefaultColorsForScheme(
                    schemeId,
                    _uiState.value.foregroundColorIndex,
                    _uiState.value.backgroundColorIndex
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to save colors") }
            }
        }
    }

    fun updateName(newName: String) {
        _uiState.update { it.copy(schemeName = newName) }
    }

    fun updateDescription(newDescription: String) {
        _uiState.update { it.copy(schemeDescription = newDescription) }
    }

    fun saveNameAndDescription() {
        if (_uiState.value.isBuiltIn) return
        viewModelScope.launch {
            try {
                repository.renameScheme(
                    schemeId,
                    _uiState.value.schemeName,
                    _uiState.value.schemeDescription
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to save name") }
            }
        }
    }

    fun showDuplicateDialog() {
        _uiState.update { it.copy(showDuplicateDialog = true) }
    }

    fun hideDuplicateDialog() {
        _uiState.update { it.copy(showDuplicateDialog = false) }
    }

    fun duplicateScheme(newName: String) {
        viewModelScope.launch {
            try {
                val newId = repository.duplicateScheme(schemeId, newName)
                _uiState.update { it.copy(showDuplicateDialog = false) }
                _navigateToDuplicate.emit(newId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to duplicate scheme") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
