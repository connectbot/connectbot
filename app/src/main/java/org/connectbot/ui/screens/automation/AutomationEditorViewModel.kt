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

package org.connectbot.ui.screens.automation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.R
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.AutomationAction
import org.connectbot.data.entity.AutomationActionType
import org.connectbot.data.entity.AutomationFailurePolicy
import org.connectbot.data.entity.PortForward
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.automation.AutomationKeySupport
import org.connectbot.service.automation.AutomationRegex
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class AutomationEditorState(
    val actions: List<AutomationAction> = emptyList(),
    val saved: List<AutomationAction> = emptyList(),
    val forwards: List<PortForward> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: Int? = null,
    val editing: AutomationAction? = null,
    val editError: Int? = null,
    val hostExists: Boolean = true,
    val supportsForwarding: Boolean = true,
) {
    val dirty: Boolean get() = actions != saved
}

internal object AutomationDraftCodec {
    fun encode(actions: List<AutomationAction>): String = JSONArray().apply {
        actions.forEach { a ->
            put(
                JSONObject().apply {
                    put("id", a.id)
                    put("host", a.hostId)
                    put("type", a.type.name)
                    put("text", a.text)
                    put("regex", a.regex)
                    put("duration", a.durationMs)
                    put("key", a.key)
                    put("modifiers", a.modifiers)
                    put("forward", a.forwardId ?: JSONObject.NULL)
                    put("failure", a.failurePolicy.name)
                },
            )
        }
    }.toString()

    fun decode(json: String): List<AutomationAction> {
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            val a = array.getJSONObject(index)
            AutomationAction(
                id = a.getString("id"), hostId = a.getLong("host"), position = index,
                type = AutomationActionType.valueOf(a.getString("type")),
                text = a.getString("text"), regex = a.getBoolean("regex"),
                durationMs = a.getLong("duration"), key = a.getInt("key"), modifiers = a.getInt("modifiers"),
                forwardId = if (a.isNull("forward")) null else a.getLong("forward"),
                failurePolicy = AutomationFailurePolicy.valueOf(a.getString("failure")),
            )
        }
    }
}

@HiltViewModel
class AutomationEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: HostRepository,
    private val dispatchers: CoroutineDispatchers,
) : ViewModel() {
    private val hostId = checkNotNull(savedStateHandle.get<Long>("hostId"))
    private val mutableState = MutableStateFlow(
        AutomationEditorState(editing = savedStateHandle.get<String>("editing")?.let { AutomationDraftCodec.decode(it).single() }),
    )
    val state = mutableState.asStateFlow()
    private var initialized = false

    init {
        viewModelScope.launch {
            try {
                val host = withContext(dispatchers.io) { repository.findHostById(hostId) }
                mutableState.update { it.copy(hostExists = host != null, supportsForwarding = host?.protocol == "ssh") }
                repository.observeAutomation(hostId).collect { actions ->
                    val draft = if (!initialized) {
                        savedStateHandle.get<String>("draft")?.let(AutomationDraftCodec::decode) ?: actions
                    } else if (mutableState.value.dirty) {
                        mutableState.value.actions
                    } else {
                        actions
                    }
                    initialized = true
                    mutableState.update { it.copy(actions = draft, saved = actions, loading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = R.string.automation_load_failed, hostExists = false) }
            }
        }
        viewModelScope.launch {
            repository.observePortForwardsForHost(hostId).collect { forwards ->
                mutableState.update { it.copy(forwards = forwards) }
            }
        }
    }

    private fun change(actions: List<AutomationAction>) {
        if (mutableState.value.saving) return
        val ordered = actions.mapIndexed { index, action -> action.copy(position = index) }
        savedStateHandle["draft"] = AutomationDraftCodec.encode(ordered)
        mutableState.update { it.copy(actions = ordered, error = null) }
    }

    fun add(type: AutomationActionType) {
        edit(AutomationAction(hostId = hostId, type = type, durationMs = if (type == AutomationActionType.DELAY) 1000 else 30_000))
    }

    fun edit(action: AutomationAction) {
        savedStateHandle["editing"] = AutomationDraftCodec.encode(listOf(action))
        mutableState.update { it.copy(editing = action, editError = null) }
    }
    fun dismissEdit() {
        savedStateHandle.remove<String>("editing")
        mutableState.update { it.copy(editing = null, editError = null) }
    }
    fun delete(id: String) = change(mutableState.value.actions.filterNot { it.id == id })

    fun move(id: String, target: Int) {
        val list = mutableState.value.actions.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0 || target !in list.indices || from == target) return
        list.add(target, list.removeAt(from))
        change(list)
    }

    fun applyEdit(action: AutomationAction) {
        viewModelScope.launch {
            val valid = withContext(dispatchers.default) {
                runCatching {
                    when (action.type) {
                        AutomationActionType.WAIT_FOR_TEXT -> {
                            require(action.text.isNotEmpty() && action.durationMs > 0)
                            if (action.regex) AutomationRegex(action.text)
                        }

                        AutomationActionType.SEND_TEXT -> require(action.text.isNotEmpty())

                        AutomationActionType.DELAY -> require(action.durationMs > 0)

                        AutomationActionType.SEND_KEY -> require(AutomationKeySupport.isSupported(action.key, action.modifiers))

                        AutomationActionType.ENABLE_FORWARD, AutomationActionType.DISABLE_FORWARD ->
                            require(mutableState.value.forwards.any { it.id == action.forwardId } && mutableState.value.supportsForwarding)

                        else -> Unit
                    }
                }.isSuccess
            }
            if (!valid) {
                mutableState.update { it.copy(editError = R.string.automation_invalid_action) }
                return@launch
            }
            val list = mutableState.value.actions.toMutableList()
            val index = list.indexOfFirst { it.id == action.id }
            if (index < 0) list.add(action) else list[index] = action
            change(list)
            dismissEdit()
        }
    }

    suspend fun save(): Boolean {
        val current = mutableState.value
        if (current.saving || current.loading || !current.hostExists) return false
        mutableState.update { it.copy(saving = true, error = null) }
        return try {
            withContext(dispatchers.io) { repository.saveAutomation(hostId, current.actions) }
            savedStateHandle.remove<String>("draft")
            mutableState.update { it.copy(saved = current.actions, saving = false) }
            true
        } catch (e: CancellationException) {
            mutableState.update { it.copy(saving = false) }
            throw e
        } catch (e: Exception) {
            mutableState.update { it.copy(saving = false, error = R.string.automation_save_failed) }
            false
        }
    }
}
