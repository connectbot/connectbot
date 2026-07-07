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

import java.io.IOException

/**
 * Asynchronous notifications emitted by a tmux control-mode (`tmux -C`)
 * client, one per `%`-prefixed line outside `%begin`/`%end` reply blocks.
 *
 * IDs keep tmux's prefixes: sessions are `$n`, windows `@n`, panes `%n`.
 */
sealed interface TmuxNotification {
    /** `%output %pane value` — new bytes from a pane (octal escapes decoded). */
    class Output(val paneId: String, val bytes: ByteArray) : TmuxNotification {
        override fun equals(other: Any?): Boolean =
            other is Output && other.paneId == paneId && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = 31 * paneId.hashCode() + bytes.contentHashCode()

        override fun toString(): String = "Output($paneId, ${bytes.size} bytes)"
    }

    /** `%window-add @window` — a window was linked into the attached session. */
    data class WindowAdd(val windowId: String) : TmuxNotification

    /** `%window-close @window` — a window in the attached session closed. */
    data class WindowClose(val windowId: String) : TmuxNotification

    /** `%window-renamed @window name` */
    data class WindowRenamed(val windowId: String, val name: String) : TmuxNotification

    /** `%window-pane-changed @window %pane` — active pane changed. */
    data class WindowPaneChanged(val windowId: String, val paneId: String) : TmuxNotification

    /**
     * `%layout-change @window layout [visible-layout [flags]]` — pane layout
     * of a window changed. Trailing fields appear on newer tmux versions.
     */
    data class LayoutChange(
        val windowId: String,
        val layout: String,
        val visibleLayout: String?,
        val flags: String?,
    ) : TmuxNotification

    /** `%session-changed $session name` — this client switched sessions. */
    data class SessionChanged(val sessionId: String, val name: String) : TmuxNotification

    /**
     * `%session-renamed [$session] name` — the attached session was renamed.
     * Older tmux versions omit the id.
     */
    data class SessionRenamed(val sessionId: String?, val name: String) : TmuxNotification

    /** `%sessions-changed` — a session was created or destroyed. */
    data object SessionsChanged : TmuxNotification

    /** `%session-window-changed $session @window` — active window changed. */
    data class SessionWindowChanged(val sessionId: String, val windowId: String) : TmuxNotification

    /** `%unlinked-window-add @window` — window created outside this session. */
    data class UnlinkedWindowAdd(val windowId: String) : TmuxNotification

    /** `%unlinked-window-close @window` — window outside this session closed. */
    data class UnlinkedWindowClose(val windowId: String) : TmuxNotification

    /** `%unlinked-window-renamed @window name` */
    data class UnlinkedWindowRenamed(val windowId: String, val name: String) : TmuxNotification

    /** `%pane-mode-changed %pane` — pane entered or left a mode (e.g. copy mode). */
    data class PaneModeChanged(val paneId: String) : TmuxNotification

    /** `%pause %pane` — tmux ≥3.2 paused a flooding pane's output stream. */
    data class Pause(val paneId: String) : TmuxNotification

    /** `%continue %pane` — a paused pane's output stream resumed. */
    data class Continue(val paneId: String) : TmuxNotification

    /** `%client-detached client` — another client detached (tmux ≥3.2). */
    data class ClientDetached(val client: String) : TmuxNotification

    /** `%client-session-changed client $session name` — another client switched. */
    data class ClientSessionChanged(
        val client: String,
        val sessionId: String,
        val name: String,
    ) : TmuxNotification

    /** `%exit [reason]` — control mode is ending; the channel will close. */
    data class Exit(val reason: String?) : TmuxNotification

    /** `%message text` — a message tmux would show in its status line. */
    data class Message(val text: String) : TmuxNotification

    /** `%config-error text` — an error from a sourced configuration file. */
    data class ConfigError(val text: String) : TmuxNotification

    /** Any `%`-line this client does not understand (forward compatibility). */
    data class Unknown(val line: String) : TmuxNotification
}

/**
 * The reply to one control-mode command: the body lines between
 * `%begin number` and the matching `%end` ([ok] = true) or `%error`
 * ([ok] = false).
 */
data class TmuxReply(
    val number: Int,
    val ok: Boolean,
    val lines: List<String>,
) {
    val text: String
        get() = lines.joinToString("\n")
}

/** Thrown to callers awaiting replies when the control channel closes. */
class TmuxChannelClosedException(message: String) : IOException(message)
