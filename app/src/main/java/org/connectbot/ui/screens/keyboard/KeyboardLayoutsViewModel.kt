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

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.data.KeyboardLayoutRepository
import org.connectbot.keyboard.DefaultKeyboardLayouts
import org.connectbot.keyboard.KeyboardLayoutSpec
import org.connectbot.util.PreferenceConstants
import javax.inject.Inject

/** One row in the layouts library. */
data class KeyboardLayoutListItem(
    val id: Long,
    val name: String,
    val isBuiltIn: Boolean,
)

data class KeyboardLayoutsUiState(
    val items: List<KeyboardLayoutListItem> = emptyList(),
    val defaultLayoutId: Long = DefaultKeyboardLayouts.DEFAULT_ID,
)

@HiltViewModel
class KeyboardLayoutsViewModel @Inject constructor(
    private val repository: KeyboardLayoutRepository,
    private val prefs: SharedPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        KeyboardLayoutsUiState(defaultLayoutId = readDefaultId()),
    )
    val uiState: StateFlow<KeyboardLayoutsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { layouts ->
                val custom = layouts.map { KeyboardLayoutListItem(it.id, it.name, isBuiltIn = false) }
                _uiState.update {
                    it.copy(items = builtInItems() + custom, defaultLayoutId = readDefaultId())
                }
            }
        }
    }

    private fun builtInItems() = listOf(
        KeyboardLayoutListItem(
            DefaultKeyboardLayouts.DEFAULT_ID,
            context.getString(R.string.keyboard_layout_name_default),
            isBuiltIn = true,
        ),
        KeyboardLayoutListItem(
            DefaultKeyboardLayouts.CLASSIC_ID,
            context.getString(R.string.keyboard_layout_name_classic),
            isBuiltIn = true,
        ),
    )

    private fun readDefaultId(): Long = prefs.getLong(PreferenceConstants.KEYBOARD_LAYOUT_ID, DefaultKeyboardLayouts.DEFAULT_ID)

    fun setDefault(layoutId: Long) {
        prefs.edit().putLong(PreferenceConstants.KEYBOARD_LAYOUT_ID, layoutId).apply()
        _uiState.update { it.copy(defaultLayoutId = layoutId) }
    }

    /** Create an empty-ish new layout (seeded from the built-in default). Returns its id. */
    suspend fun createLayout(): Long = createWithUniqueName(
        context.getString(R.string.keyboard_layouts_new),
        DefaultKeyboardLayouts.default,
    )

    /** Duplicate a built-in or custom layout into a new editable row. Returns its id. */
    suspend fun duplicate(item: KeyboardLayoutListItem): Long = createWithUniqueName(
        context.getString(R.string.keyboard_layouts_duplicate_name, item.name),
        repository.resolveSpec(item.id),
    )

    /**
     * Rename a layout. Returns false (leaving the layout unchanged) if the new
     * name is already taken.
     */
    suspend fun rename(layoutId: Long, newName: String): Boolean {
        val trimmed = newName.trim()
        if (repository.nameExists(trimmed, excludeLayoutId = layoutId)) return false
        return try {
            repository.rename(layoutId, trimmed)
            true
        } catch (_: SQLiteConstraintException) {
            // A concurrent writer took the name between the check and the update.
            false
        }
    }

    fun delete(layoutId: Long) {
        viewModelScope.launch {
            repository.delete(layoutId)
            // If the deleted layout was the global default, fall back to built-in.
            if (readDefaultId() == layoutId) {
                setDefault(DefaultKeyboardLayouts.DEFAULT_ID)
            }
        }
    }

    /** Number of hosts whose layout override points at [layoutId]. */
    suspend fun hostsUsing(layoutId: Long): Int = repository.countHostsUsing(layoutId)

    /**
     * Insert a layout under the first free "base", "base 2", ... name. The
     * unique-name check and the insert are not atomic, so retry from fresh
     * database state if a concurrent writer wins the name first.
     */
    private suspend fun createWithUniqueName(base: String, spec: KeyboardLayoutSpec): Long {
        while (true) {
            try {
                return repository.create(uniqueName(base), spec)
            } catch (_: SQLiteConstraintException) {
                // Name taken since the check; recompute against current rows.
            }
        }
    }

    private suspend fun uniqueName(base: String): String {
        if (!repository.nameExists(base)) return base
        var n = 2
        while (repository.nameExists("$base $n")) n++
        return "$base $n"
    }
}
