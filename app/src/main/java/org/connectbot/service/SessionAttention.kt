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

package org.connectbot.service

/** Attention state belongs to a session, so reconnecting cannot inherit an old notice. */
class SessionAttention {
    private var visible = false
    private var viewedAfterEnd = false
    private var state = SessionAttentionState()

    @Synchronized
    fun snapshot(): SessionAttentionState = state

    @Synchronized
    fun onOutput(): Boolean {
        val wasUnread = state.unreadOutput
        if (!visible) state = state.copy(unreadOutput = true)
        return !wasUnread && state.unreadOutput && state.reason != null
    }

    @Synchronized
    fun onEnded(reason: DisconnectReason) {
        viewedAfterEnd = visible
        state = state.copy(reason = state.reason ?: reason, acknowledged = false)
    }

    @Synchronized
    fun onOpened() {
        visible = true
        viewedAfterEnd = state.reason != null
        state = state.copy(unreadOutput = false)
    }

    @Synchronized
    fun onOutputDiscarded() {
        state = state.copy(unreadOutput = false)
    }

    @Synchronized
    fun onHidden() {
        visible = false
    }

    @Synchronized
    fun onConsoleClosed() {
        visible = false
        if (viewedAfterEnd) state = state.copy(acknowledged = true)
    }

    @Synchronized
    fun onReconnected() {
        viewedAfterEnd = false
        state = SessionAttentionState()
    }
}

data class SessionAttentionState(
    val reason: DisconnectReason? = null,
    val unreadOutput: Boolean = false,
    val acknowledged: Boolean = false,
)
