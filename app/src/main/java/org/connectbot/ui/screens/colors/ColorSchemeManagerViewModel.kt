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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.data.entity.ColorScheme

data class SchemeManagerUiState(
    val schemes: List<ColorScheme> = emptyList(),
    val selectedSchemeId: Long? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showNewSchemeDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val dialogError: String? = null
)

@HiltViewModel
class ColorSchemeManagerViewModel @Inject constructor(
    val repository: ColorSchemeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchemeManagerUiState())
    val uiState: StateFlow<SchemeManagerUiState> = _uiState.asStateFlow()

    init {
        loadSchemes()
    }

    private fun loadSchemes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val schemes = repository.getAllSchemes()
                _uiState.update {
                    it.copy(
                        schemes = schemes,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load schemes"
                    )
                }
            }
        }
    }

    fun selectScheme(schemeId: Long) {
        _uiState.update {
            it.copy(
                selectedSchemeId = if (it.selectedSchemeId == schemeId) null else schemeId
            )
        }
    }

    fun showNewSchemeDialog() {
        _uiState.update {
            it.copy(
                showNewSchemeDialog = true,
                dialogError = null
            )
        }
    }

    fun hideNewSchemeDialog() {
        _uiState.update {
            it.copy(
                showNewSchemeDialog = false,
                dialogError = null
            )
        }
    }

    fun showRenameDialog() {
        _uiState.update {
            it.copy(
                showRenameDialog = true,
                dialogError = null
            )
        }
    }

    fun hideRenameDialog() {
        _uiState.update {
            it.copy(
                showRenameDialog = false,
                dialogError = null
            )
        }
    }

    fun showDeleteDialog() {
        _uiState.update {
            it.copy(
                showDeleteDialog = true,
                dialogError = null
            )
        }
    }

    fun hideDeleteDialog() {
        _uiState.update {
            it.copy(
                showDeleteDialog = false,
                dialogError = null
            )
        }
    }

    fun createNewScheme(name: String, description: String, basedOnSchemeId: Long) {
        viewModelScope.launch {
            try {
                // Validate name
                if (name.isBlank()) {
                    _uiState.update { it.copy(dialogError = "Scheme name cannot be empty") }
                    return@launch
                }

                // Check for duplicate name
                if (repository.schemeNameExists(name)) {
                    _uiState.update { it.copy(dialogError = "A scheme with this name already exists") }
                    return@launch
                }

                // Create the scheme
                repository.createCustomScheme(name, description, basedOnSchemeId)

                // Reload schemes and close dialog
                loadSchemes()
                hideNewSchemeDialog()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(dialogError = e.message ?: "Failed to create scheme")
                }
            }
        }
    }

    fun renameScheme(schemeId: Long, newName: String, newDescription: String = "") {
        viewModelScope.launch {
            try {
                // Validate name
                if (newName.isBlank()) {
                    _uiState.update { it.copy(dialogError = "Scheme name cannot be empty") }
                    return@launch
                }

                // Check for duplicate name (excluding current scheme)
                if (repository.schemeNameExists(newName, excludeSchemeId = schemeId)) {
                    _uiState.update { it.copy(dialogError = "A scheme with this name already exists") }
                    return@launch
                }

                // Rename the scheme
                val success = repository.renameScheme(schemeId, newName, newDescription)
                if (success) {
                    // Reload schemes and close dialog
                    loadSchemes()
                    hideRenameDialog()
                } else {
                    _uiState.update {
                        it.copy(dialogError = "Failed to rename scheme")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(dialogError = e.message ?: "Failed to rename scheme")
                }
            }
        }
    }

    fun deleteScheme(schemeId: Long) {
        viewModelScope.launch {
            try {
                // Don't allow deleting built-in schemes
                val scheme = _uiState.value.schemes.find { it.id == schemeId }
                if (scheme?.isBuiltIn == true) {
                    _uiState.update {
                        it.copy(dialogError = "Cannot delete built-in schemes")
                    }
                    return@launch
                }

                // Delete the scheme
                repository.deleteCustomScheme(schemeId)

                // Reload schemes and close dialog
                loadSchemes()
                hideDeleteDialog()
                _uiState.update { it.copy(selectedSchemeId = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(dialogError = e.message ?: "Failed to delete scheme")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Refresh the scheme list (e.g., after importing a scheme externally).
     */
    fun refresh() {
        loadSchemes()
    }
}
