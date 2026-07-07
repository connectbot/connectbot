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

package org.connectbot.ui.screens.console

import org.connectbot.service.TerminalBridge
import org.connectbot.service.tmux.TmuxAttachState

/**
 * One tab in the console tab strip. Each connected host contributes its
 * plain shell tab plus one tab per tmux session discovered on it, grouped
 * adjacently and sharing the host's color.
 */
sealed interface ConsoleTab {
    /** Stable identity across recompositions and tab-list rebuilds. */
    val key: String

    /** The connection this tab lives on. */
    val bridge: TerminalBridge

    /** The host's regular shell — the original console view. */
    data class HostShell(
        override val bridge: TerminalBridge,
        /** A long-running command finished here while the tab was hidden. */
        val completionBadge: Boolean = false,
    ) : ConsoleTab {
        override val key: String = hostKey(bridge.host.id)
    }

    /** One tmux session on the host, live (attached) or dimmed (detached). */
    data class TmuxSession(
        override val bridge: TerminalBridge,
        val sessionId: String,
        val sessionName: String,
        val attachState: TmuxAttachState,
        val bellBadge: Boolean = false,
        val activityBadge: Boolean = false,
    ) : ConsoleTab {
        override val key: String = tmuxKey(bridge.host.id, sessionId)
    }

    companion object {
        fun hostKey(hostId: Long): String = "host:$hostId"

        fun tmuxKey(hostId: Long, sessionId: String): String = "tmux:$hostId:$sessionId"
    }
}
