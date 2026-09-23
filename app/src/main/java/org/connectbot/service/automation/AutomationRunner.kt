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

package org.connectbot.service.automation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import org.connectbot.R
import org.connectbot.data.entity.AutomationAction
import org.connectbot.data.entity.AutomationActionType
import org.connectbot.data.entity.AutomationFailurePolicy

data class AutomationState(
    val running: Boolean = false,
    val step: Int = 0,
    val total: Int = 0,
    val error: Int? = null,
    val failedStep: Int? = null,
    val completed: Boolean = false,
)

/** Implementations perform all terminal/transport calls off the main dispatcher. */
interface AutomationSession {
    val sessionOpen: Boolean
    suspend fun sendText(text: String)
    suspend fun sendKey(key: Int, modifiers: Int)
    suspend fun setForward(id: Long, enabled: Boolean)
    fun disconnect()
}

class AutomationFailure(val reason: Int) : Exception()

class AutomationRunner(
    private val session: AutomationSession,
    val output: AutomationOutput = AutomationOutput(),
) {
    private val mutableState = MutableStateFlow(AutomationState())
    val state = mutableState.asStateFlow()

    suspend fun run(actions: List<AutomationAction>) {
        if (actions.isEmpty()) return
        mutableState.value = AutomationState(running = true, total = actions.size)
        try {
            coroutineScope {
                val execution = async { execute(actions) }
                try {
                    select<Unit> {
                        output.overflow.onAwait { throw AutomationFailure(R.string.automation_buffer_overflow) }
                        execution.onAwait { }
                    }
                } finally {
                    execution.cancel()
                }
            }
            mutableState.value = mutableState.value.copy(running = false, completed = true)
        } catch (e: CancellationException) {
            mutableState.value = mutableState.value.copy(running = false)
            throw e
        } catch (e: Exception) {
            mutableState.value = mutableState.value.copy(
                running = false,
                error = (e as? AutomationFailure)?.reason ?: R.string.automation_action_failed,
                failedStep = mutableState.value.step,
            )
        }
    }

    private suspend fun execute(actions: List<AutomationAction>) {
        for ((index, action) in actions.withIndex()) {
            currentCoroutineContext().ensureActive()
            mutableState.value = mutableState.value.copy(step = index + 1)
            try {
                when (action.type) {
                    AutomationActionType.WAIT_FOR_TEXT -> {
                        requireSession()
                        require(action.durationMs > 0 && action.text.isNotEmpty())
                        val regex = if (action.regex) AutomationRegex(action.text) else null
                        withTimeout(action.durationMs) {
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val text = output.snapshot()
                                val end = if (regex != null) {
                                    regex.matchEnd(text)
                                } else {
                                    text.indexOf(action.text).takeIf { it >= 0 }?.plus(action.text.length)
                                }
                                if (end != null) {
                                    output.consume(end)
                                    break
                                }
                                output.changed.receive()
                            }
                        }
                    }

                    AutomationActionType.SEND_TEXT -> {
                        requireSession()
                        output.clear()
                        session.sendText(action.text)
                    }

                    AutomationActionType.SEND_KEY -> {
                        requireSession()
                        require(AutomationKeySupport.isSupported(action.key, action.modifiers))
                        output.clear()
                        session.sendKey(action.key, action.modifiers)
                    }

                    AutomationActionType.DELAY -> {
                        require(action.durationMs > 0)
                        delay(action.durationMs)
                    }

                    AutomationActionType.DISCONNECT -> {
                        session.disconnect()
                        return
                    }

                    AutomationActionType.ENABLE_FORWARD, AutomationActionType.DISABLE_FORWARD -> {
                        val id = action.forwardId ?: throw AutomationFailure(R.string.automation_forward_missing)
                        session.setForward(id, action.type == AutomationActionType.ENABLE_FORWARD)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                val reason = when (e) {
                    is TimeoutCancellationException -> R.string.automation_timed_out
                    is AutomationFailure -> e.reason
                    else -> R.string.automation_action_failed
                }
                if (action.failurePolicy == AutomationFailurePolicy.STOP) throw AutomationFailure(reason)
                mutableState.value = mutableState.value.copy(error = reason, failedStep = index + 1)
            }
        }
    }

    private fun requireSession() {
        if (!session.sessionOpen) throw AutomationFailure(R.string.automation_no_session)
    }
}
