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

import java.io.ByteArrayOutputStream

/** One parsed line of control-mode output. */
sealed interface TmuxParseEvent {
    data class Notification(val value: TmuxNotification) : TmuxParseEvent

    data class Reply(val value: TmuxReply) : TmuxParseEvent

    /** The line was framing/noise (reply body collected, DCS guard, blank). */
    data object Consumed : TmuxParseEvent
}

/**
 * Stateful line parser for tmux control-mode output.
 *
 * Feed it lines decoded as ISO-8859-1 (byte-preserving: tmux escapes bytes
 * <0x20, 0x7f, and backslash as `\ooo` but passes UTF-8 ≥0x80 through raw).
 * Reply bodies between `%begin`/`%end` (or `%error`) are collected and
 * emitted as a single [TmuxParseEvent.Reply]; every other `%`-line becomes a
 * [TmuxParseEvent.Notification].
 *
 * Tolerates the `-CC` interactive ornaments (a `P1000p` DCS prologue
 * and `\\` terminator) and trailing `\r`, so the same parser works
 * whether or not the channel allocated a PTY.
 */
class TmuxControlParser {
    private var replyNumber = -1
    private var replyLines: MutableList<String>? = null

    fun feed(rawLine: String): TmuxParseEvent {
        var line = rawLine.removeSuffix("\r")
        if (line.startsWith(DCS_GUARD)) {
            line = line.substring(DCS_GUARD.length)
        }
        if (line == DCS_TERMINATOR) return TmuxParseEvent.Consumed

        val body = replyLines
        if (body != null) {
            if (line.startsWith("%end ") || line.startsWith("%error ")) {
                val ok = line.startsWith("%end ")
                val reply = TmuxReply(replyNumber, ok, body)
                replyLines = null
                replyNumber = -1
                return TmuxParseEvent.Reply(reply)
            }
            body.add(line)
            return TmuxParseEvent.Consumed
        }

        if (line.startsWith("%begin ")) {
            replyNumber = line.split(' ').getOrNull(2)?.toIntOrNull() ?: -1
            replyLines = mutableListOf()
            return TmuxParseEvent.Consumed
        }

        if (line.isEmpty()) return TmuxParseEvent.Consumed
        return TmuxParseEvent.Notification(parseNotification(line))
    }

    private fun parseNotification(line: String): TmuxNotification {
        val space = line.indexOf(' ')
        val word = if (space == -1) line else line.substring(0, space)
        val rest = if (space == -1) "" else line.substring(space + 1)

        return when (word) {
            "%output" -> parseOutput(rest) ?: TmuxNotification.Unknown(line)
            "%extended-output" -> parseExtendedOutput(rest) ?: TmuxNotification.Unknown(line)
            "%window-add" -> TmuxNotification.WindowAdd(rest)
            "%window-close" -> TmuxNotification.WindowClose(rest)
            "%window-renamed" -> splitIdAndText(rest)?.let { (id, name) ->
                TmuxNotification.WindowRenamed(id, name)
            } ?: TmuxNotification.Unknown(line)
            "%window-pane-changed" -> splitIdAndText(rest)?.let { (windowId, paneId) ->
                TmuxNotification.WindowPaneChanged(windowId, paneId)
            } ?: TmuxNotification.Unknown(line)
            "%layout-change" -> parseLayoutChange(rest) ?: TmuxNotification.Unknown(line)
            "%session-changed" -> splitIdAndText(rest)?.let { (id, name) ->
                TmuxNotification.SessionChanged(id, name)
            } ?: TmuxNotification.Unknown(line)
            "%session-renamed" -> parseSessionRenamed(rest)
            "%sessions-changed" -> TmuxNotification.SessionsChanged
            "%session-window-changed" -> splitIdAndText(rest)?.let { (sessionId, windowId) ->
                TmuxNotification.SessionWindowChanged(sessionId, windowId)
            } ?: TmuxNotification.Unknown(line)
            "%unlinked-window-add" -> TmuxNotification.UnlinkedWindowAdd(rest)
            "%unlinked-window-close" -> TmuxNotification.UnlinkedWindowClose(rest)
            "%unlinked-window-renamed" -> splitIdAndText(rest)?.let { (id, name) ->
                TmuxNotification.UnlinkedWindowRenamed(id, name)
            } ?: TmuxNotification.Unknown(line)
            "%pane-mode-changed" -> TmuxNotification.PaneModeChanged(rest)
            "%pause" -> TmuxNotification.Pause(rest)
            "%continue" -> TmuxNotification.Continue(rest)
            "%client-detached" -> TmuxNotification.ClientDetached(rest)
            "%client-session-changed" -> parseClientSessionChanged(rest) ?: TmuxNotification.Unknown(line)
            "%exit" -> TmuxNotification.Exit(rest.ifEmpty { null })
            "%message" -> TmuxNotification.Message(rest)
            "%config-error" -> TmuxNotification.ConfigError(rest)
            else -> TmuxNotification.Unknown(line)
        }
    }

    private fun parseOutput(rest: String): TmuxNotification.Output? {
        val (paneId, value) = splitIdAndText(rest) ?: return null
        if (!paneId.startsWith('%')) return null
        return TmuxNotification.Output(paneId, unescapeControlBytes(value))
    }

    /** `%pane age ... : value` — future-proofed by anchoring on the first " : ". */
    private fun parseExtendedOutput(rest: String): TmuxNotification.Output? {
        val paneEnd = rest.indexOf(' ')
        if (paneEnd == -1) return null
        val paneId = rest.substring(0, paneEnd)
        if (!paneId.startsWith('%')) return null
        val separator = rest.indexOf(" : ", startIndex = paneEnd)
        if (separator == -1) return null
        return TmuxNotification.Output(paneId, unescapeControlBytes(rest.substring(separator + 3)))
    }

    private fun parseLayoutChange(rest: String): TmuxNotification.LayoutChange? {
        val parts = rest.split(' ')
        if (parts.size < 2) return null
        return TmuxNotification.LayoutChange(
            windowId = parts[0],
            layout = parts[1],
            visibleLayout = parts.getOrNull(2),
            flags = parts.getOrNull(3),
        )
    }

    private fun parseSessionRenamed(rest: String): TmuxNotification {
        // tmux ≥3.0 sends "$id name"; older versions send just "name".
        val (first, remainder) = splitIdAndText(rest) ?: return TmuxNotification.SessionRenamed(null, rest)
        return if (first.startsWith('$')) {
            TmuxNotification.SessionRenamed(first, remainder)
        } else {
            TmuxNotification.SessionRenamed(null, rest)
        }
    }

    private fun parseClientSessionChanged(rest: String): TmuxNotification.ClientSessionChanged? {
        val (client, remainder) = splitIdAndText(rest) ?: return null
        val (sessionId, name) = splitIdAndText(remainder) ?: return null
        return TmuxNotification.ClientSessionChanged(client, sessionId, name)
    }

    private fun splitIdAndText(rest: String): Pair<String, String>? {
        val space = rest.indexOf(' ')
        if (space == -1) return null
        return rest.substring(0, space) to rest.substring(space + 1)
    }

    companion object {
        private const val DCS_GUARD = "\u001bP1000p"
        private const val DCS_TERMINATOR = "\u001b\\"

        /**
         * Decodes control-mode value escaping: tmux encodes backslash and
         * bytes <0x20 / 0x7f as `\ooo` (exactly three octal digits, e.g.
         * `\134`, `\015`) and passes all other bytes — including raw UTF-8
         * sequences — through unchanged. The input must have been decoded
         * as ISO-8859-1 so every char maps 1:1 to the original byte.
         */
        fun unescapeControlBytes(value: String): ByteArray {
            val out = ByteArrayOutputStream(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 3 < value.length &&
                    value[i + 1] in '0'..'7' && value[i + 2] in '0'..'7' && value[i + 3] in '0'..'7'
                ) {
                    val byte = (value[i + 1] - '0') * 64 + (value[i + 2] - '0') * 8 + (value[i + 3] - '0')
                    out.write(byte)
                    i += 4
                } else {
                    out.write(c.code)
                    i++
                }
            }
            return out.toByteArray()
        }
    }
}
