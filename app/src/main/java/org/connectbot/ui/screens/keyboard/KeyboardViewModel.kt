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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.Host
import org.connectbot.data.keyboard.KeyboardConfiguration
import org.connectbot.data.keyboard.KeyboardRepository
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class KeyboardViewModel @Inject constructor(val repository: KeyboardRepository, private val database: ConnectBotDatabase) : ViewModel() {
    val configuration = repository.configuration.stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardConfiguration())
    val error = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun layoutSelection(host: Host): Flow<UUID?> = (
        if (host.id > 0) database.hostDao().observeById(host.id) else flowOf(host)
        ).flatMapLatest { database.profileDao().observeById(it?.profileId ?: 1L) }.map { it?.keyboardLayoutId }

    fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message
            }
        }
    }
}
