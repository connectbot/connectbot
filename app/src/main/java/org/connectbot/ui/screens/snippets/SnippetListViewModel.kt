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

package org.connectbot.ui.screens.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.data.HostRepository
import org.connectbot.data.SnippetRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Snippet
import javax.inject.Inject

data class SnippetListUiState(
    val snippets: List<Snippet> = emptyList(),
    val hosts: List<Host> = emptyList(),
    val isLoading: Boolean = true,
    val selectedTag: String? = null,
    val editorSnippet: Snippet? = null,
    val showDeleteDialog: Snippet? = null,
) {
    /**
     * All distinct tags across snippets, sorted alphabetically.
     */
    val allTags: List<String>
        get() = snippets.flatMap { it.tagList }.distinct().sortedBy { it.lowercase() }

    /**
     * Snippets filtered by the selected tag (all snippets when no tag selected).
     */
    val filteredSnippets: List<Snippet>
        get() = selectedTag?.let { tag -> snippets.filter { tag in it.tagList } } ?: snippets
}

@HiltViewModel
class SnippetListViewModel @Inject constructor(
    private val snippetRepository: SnippetRepository,
    private val hostRepository: HostRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnippetListUiState())
    val uiState: StateFlow<SnippetListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            snippetRepository.observeAll().collect { snippets ->
                _uiState.update { state ->
                    val selectedTag = state.selectedTag?.takeIf { tag ->
                        snippets.any { tag in it.tagList }
                    }
                    state.copy(snippets = snippets, selectedTag = selectedTag, isLoading = false)
                }
            }
        }
        viewModelScope.launch {
            hostRepository.observeHosts().collect { hosts ->
                _uiState.update { it.copy(hosts = hosts) }
            }
        }
    }

    fun selectTag(tag: String?) {
        _uiState.update { it.copy(selectedTag = tag) }
    }

    fun openEditor(snippet: Snippet) {
        _uiState.update { it.copy(editorSnippet = snippet) }
    }

    fun closeEditor() {
        _uiState.update { it.copy(editorSnippet = null) }
    }

    fun saveSnippet(snippet: Snippet) {
        viewModelScope.launch {
            snippetRepository.save(snippet)
            _uiState.update { it.copy(editorSnippet = null) }
        }
    }

    fun showDeleteDialog(snippet: Snippet) {
        _uiState.update { it.copy(showDeleteDialog = snippet) }
    }

    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = null) }
    }

    fun deleteSnippet(snippet: Snippet) {
        viewModelScope.launch {
            snippetRepository.delete(snippet.id)
            _uiState.update { it.copy(showDeleteDialog = null) }
        }
    }
}
