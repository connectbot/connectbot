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

package org.connectbot.service.tmux

/**
 * Immutable snapshot of everything ConnectBot knows about tmux on one host.
 * All ids keep tmux's prefixes: sessions `$n`, windows `@n`, panes `%n` —
 * commands always target ids, never names (names may contain anything).
 */
data class TmuxHostState(
    val availability: TmuxAvailability = TmuxAvailability.UNKNOWN,
    val version: TmuxVersion? = null,
    val sessions: List<TmuxSessionInfo> = emptyList(),
    /** True when tmux is usable but has no sessions — drives the offer UI. */
    val offerSession: Boolean = false,
) {
    fun session(sessionId: String): TmuxSessionInfo? = sessions.find { it.id == sessionId }

    fun updateSession(sessionId: String, transform: (TmuxSessionInfo) -> TmuxSessionInfo): TmuxHostState =
        copy(sessions = sessions.map { if (it.id == sessionId) transform(it) else it })
}

enum class TmuxAvailability {
    /** Probe has not run. */
    UNKNOWN,

    /** Probe in flight. */
    PROBING,

    /** tmux missing, probe failed, or disabled for this host. */
    UNAVAILABLE,

    /** tmux present but older than the minimum supported version. */
    UNSUPPORTED_VERSION,

    /** tmux usable; [TmuxHostState.sessions] is meaningful. */
    READY,
}

enum class TmuxAttachState {
    /** No live control channel; the session keeps running server-side. */
    DETACHED,

    /** Control channel being established and state dump in flight. */
    ATTACHING,

    /** Live control channel; window/pane state and output are current. */
    ATTACHED,
}

data class TmuxSessionInfo(
    val id: String,
    val name: String,
    val attachState: TmuxAttachState = TmuxAttachState.DETACHED,
    val windows: List<TmuxWindow> = emptyList(),
    val activeWindowId: String? = null,
    /** Number of clients attached server-side (including this app). */
    val attachedCount: Int = 0,
    /** Rendered text of the active pane, for the dimmed unattached tab. */
    val snapshot: List<String>? = null,
) {
    val activeWindow: TmuxWindow?
        get() = windows.find { it.id == activeWindowId } ?: windows.firstOrNull()
}

data class TmuxWindow(
    val id: String,
    val index: Int,
    val name: String,
    val active: Boolean = false,
    val bell: Boolean = false,
    val activity: Boolean = false,
    val panes: List<TmuxPaneRef> = emptyList(),
    val activePaneId: String? = null,
) {
    val activePane: TmuxPaneRef?
        get() = panes.find { it.id == activePaneId } ?: panes.firstOrNull()
}

data class TmuxPaneRef(
    val id: String,
    val index: Int,
    val width: Int,
    val height: Int,
    val left: Int = 0,
    val top: Int = 0,
    val active: Boolean = false,
    /** Output stream paused by tmux flow control (≥3.2). */
    val paused: Boolean = false,
)

/**
 * A parsed `tmux -V` version with capability gates.
 * Handles forms like `tmux 3.7b`, `tmux 3.3a`, `tmux next-3.6`,
 * `tmux master`, and distro suffixes.
 */
data class TmuxVersion(val major: Int, val minor: Int, val raw: String) {
    fun atLeast(major: Int, minor: Int): Boolean =
        this.major > major || (this.major == major && this.minor >= minor)

    /** `%pause`/`%continue`, `refresh-client -f pause-after`, `-A %p:on/off`. */
    val supportsFlowControl: Boolean
        get() = atLeast(3, 2)

    /** `refresh-client -C w,h` comma syntax (older versions use `-C w,h` too via x). */
    val supportsClientSize: Boolean
        get() = atLeast(2, 9)

    val isSupported: Boolean
        get() = atLeast(MIN_MAJOR, MIN_MINOR)

    companion object {
        const val MIN_MAJOR = 2
        const val MIN_MINOR = 6

        private val VERSION_REGEX = Regex("""(\d+)\.(\d+)""")

        /**
         * Parses `tmux -V` output. `master`/`next-X.Y` builds are treated as
         * the newest known version.
         */
        fun parse(versionLine: String): TmuxVersion? {
            val trimmed = versionLine.trim()
            if (!trimmed.startsWith("tmux ")) return null
            val rest = trimmed.removePrefix("tmux ").trim()
            if (rest == "master") return TmuxVersion(Int.MAX_VALUE, 0, trimmed)
            val match = VERSION_REGEX.find(rest) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            return TmuxVersion(major, minor, trimmed)
        }
    }
}

/** The pane the user is (or was last) looking at within a session. */
data class TmuxTarget(
    val sessionId: String,
    val windowId: String,
    val paneId: String,
    val sessionName: String,
) {
    /** Encodes for Host.tmuxLastTarget persistence. */
    fun encode(): String = "$sessionId|$windowId|$paneId|$sessionName"

    companion object {
        fun decode(value: String?): TmuxTarget? {
            if (value.isNullOrEmpty()) return null
            val parts = value.split('|', limit = 4)
            if (parts.size < 4) return null
            if (!parts[0].startsWith('$') || !parts[1].startsWith('@') || !parts[2].startsWith('%')) return null
            return TmuxTarget(parts[0], parts[1], parts[2], parts[3])
        }
    }
}
