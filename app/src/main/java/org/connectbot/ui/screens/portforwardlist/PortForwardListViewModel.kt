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

package org.connectbot.ui.screens.portforwardlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.bean.PortForwardBean
import org.connectbot.util.HostDatabase

data class PortForwardListUiState(
    val portForwards: List<PortForwardBean> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PortForwardListViewModel(
    private val context: Context,
    private val hostId: Long
) : ViewModel() {
    private val database: HostDatabase = HostDatabase.get(context)

    private val _uiState = MutableStateFlow(PortForwardListUiState(isLoading = true))
    val uiState: StateFlow<PortForwardListUiState> = _uiState.asStateFlow()

    init {
        loadPortForwards()
    }

    fun loadPortForwards() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val portForwards = withContext(Dispatchers.IO) {
                    val host = database.findHostById(hostId)
                    if (host != null) {
                        database.getPortForwardsForHost(host)
                    } else {
                        emptyList()
                    }
                }
                _uiState.update {
                    it.copy(portForwards = portForwards, isLoading = false, error = null)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load port forwards")
                }
            }
        }
    }

    fun addPortForward(nickname: String, type: String, sourcePort: String, destination: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val portForward = PortForwardBean(
                        hostId,
                        nickname,
                        type,
                        sourcePort,
                        destination
                    )
                    database.savePortForward(portForward)
                }
                loadPortForwards()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to add port forward")
                }
            }
        }
    }

    fun updatePortForward(
        portForward: PortForwardBean,
        nickname: String,
        type: String,
        sourcePort: String,
        destination: String
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    portForward.nickname = nickname
                    portForward.type = type
                    portForward.setSourcePort(sourcePort.toInt())
                    portForward.setDest(destination)
                    database.savePortForward(portForward)
                }
                loadPortForwards()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to update port forward")
                }
            }
        }
    }

    fun deletePortForward(portForward: PortForwardBean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.deletePortForward(portForward)
                }
                loadPortForwards()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete port forward")
                }
            }
        }
    }
}
