/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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

package org.connectbot.ui.screens.keyboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.data.KeyboardLayoutRepository
import org.connectbot.keyboard.DefaultKeyboardLayouts
import org.connectbot.keyboard.KeySpec
import org.connectbot.keyboard.KeyboardLayoutSpec
import javax.inject.Inject

data class KeyboardLayoutEditorUiState(
    val layoutId: Long = 0L,
    val name: String = "",
    val rows: List<List<KeySpec>> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Editor for one custom keyboard layout. Every mutation persists immediately so
 * there is no unsaved state to reconcile (mirrors the color palette editor).
 */
@HiltViewModel
class KeyboardLayoutEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: KeyboardLayoutRepository,
) : ViewModel() {

    private val layoutId: Long = savedStateHandle.get<Long>("layoutId") ?: 0L

    private val _uiState = MutableStateFlow(KeyboardLayoutEditorUiState(layoutId = layoutId))
    val uiState: StateFlow<KeyboardLayoutEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val layout = repository.getById(layoutId)
            val spec = layout?.let { repository.resolveSpec(it.id) } ?: DefaultKeyboardLayouts.default
            _uiState.update {
                it.copy(name = layout?.name ?: "", rows = spec.rows, isLoading = false)
            }
        }
    }

    private fun persist(newRows: List<List<KeySpec>>) {
        // Never drop below a single (possibly empty) row.
        val rows = if (newRows.isEmpty()) listOf(emptyList()) else newRows
        _uiState.update { it.copy(rows = rows) }
        viewModelScope.launch {
            repository.updateSpec(layoutId, KeyboardLayoutSpec(rows))
        }
    }

    fun addKey(rowIndex: Int, key: KeySpec) {
        val rows = _uiState.value.rows.toMutableRows()
        if (rowIndex in rows.indices) {
            rows[rowIndex] = rows[rowIndex] + key
            persist(rows)
        }
    }

    fun replaceKey(rowIndex: Int, keyIndex: Int, key: KeySpec) {
        val rows = _uiState.value.rows.toMutableRows()
        if (rowIndex in rows.indices && keyIndex in rows[rowIndex].indices) {
            rows[rowIndex] = rows[rowIndex].toMutableList().also { it[keyIndex] = key }
            persist(rows)
        }
    }

    fun removeKey(rowIndex: Int, keyIndex: Int) {
        val rows = _uiState.value.rows.toMutableRows()
        if (rowIndex in rows.indices && keyIndex in rows[rowIndex].indices) {
            rows[rowIndex] = rows[rowIndex].toMutableList().also { it.removeAt(keyIndex) }
            persist(rows)
        }
    }

    fun moveKey(rowIndex: Int, keyIndex: Int, delta: Int) {
        val rows = _uiState.value.rows.toMutableRows()
        if (rowIndex !in rows.indices) return
        val row = rows[rowIndex].toMutableList()
        val target = keyIndex + delta
        if (keyIndex in row.indices && target in row.indices) {
            val k = row.removeAt(keyIndex)
            row.add(target, k)
            rows[rowIndex] = row
            persist(rows)
        }
    }

    /** Move a key to the other row (creating the second row if needed). */
    fun moveKeyToRow(rowIndex: Int, keyIndex: Int, targetRow: Int) {
        val rows = _uiState.value.rows.toMutableRows()
        if (rowIndex !in rows.indices) return
        while (rows.size <= targetRow) rows.add(emptyList())
        val source = rows[rowIndex].toMutableList()
        if (keyIndex !in source.indices) return
        val k = source.removeAt(keyIndex)
        rows[rowIndex] = source
        rows[targetRow] = rows[targetRow] + k
        persist(rows)
    }

    fun addSecondRow() {
        val rows = _uiState.value.rows
        if (rows.size < 2) persist(rows + listOf(emptyList()))
    }

    /** Remove the second row, merging its keys back into the first. */
    fun removeSecondRow() {
        val rows = _uiState.value.rows
        if (rows.size >= 2) {
            persist(listOf(rows[0] + rows[1]))
        }
    }

    private fun List<List<KeySpec>>.toMutableRows(): MutableList<List<KeySpec>> = this.map { it }.toMutableList()
}
