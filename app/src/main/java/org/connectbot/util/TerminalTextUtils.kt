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

package org.connectbot.util

/**
 * A single visual line of a terminal session: its text and whether it soft-wraps
 * into the following line.
 */
data class TerminalSessionLine(val text: String, val softWrapped: Boolean)

object TerminalTextUtils {
    /**
     * Normalizes line breaks in text that is sent to the terminal as if the
     * user had typed it, such as clipboard pastes and the floating text input.
     *
     * Pressing Enter in a terminal sends a carriage return, so CRLF and LF
     * both become CR, matching what xterm and other terminal emulators send
     * when pasting. Programs running in raw mode (menus, editors, REPLs)
     * ignore bare line feeds, which made multi-line pastes run together.
     *
     * @param text The raw text to send to the terminal.
     * @return The text with CRLF and LF line breaks replaced by CR.
     */
    fun normalizeLineBreaks(text: String): String = text.replace("\r\n", "\r").replace('\n', '\r')

    /**
     * Builds a plain-text transcript of a terminal session from its lines,
     * in order from the oldest scrollback line to the bottom of the screen.
     *
     * Trailing whitespace on each visual line is dropped, soft-wrapped lines
     * are joined with their continuation instead of a line break (matching
     * how the terminal renders and how selection copy behaves), and blank
     * lines at the end (the unused screen area below the output) are removed.
     *
     * @param lines The session's lines, oldest first.
     * @return The session transcript as a single string.
     */
    fun buildSessionText(lines: List<TerminalSessionLine>): String {
        val builder = StringBuilder()
        for (line in lines) {
            builder.append(line.text.trimEnd())
            if (!line.softWrapped) {
                builder.append('\n')
            }
        }
        return builder.toString().trimEnd('\n')
    }

    /**
     * Builds plain text from the visible rows of a terminal snapshot.
     * Each snapshot row remains a separate line because the public snapshot
     * text API intentionally does not expose soft-wrap metadata.
     */
    fun buildSnapshotText(lines: List<String>): String = buildSessionText(
        lines.map { TerminalSessionLine(text = it, softWrapped = false) },
    )

    /** Selects the truthful copy source for the emulator's active screen. */
    fun buildTerminalCopyText(
        altScreenActive: Boolean,
        snapshotLines: () -> List<String>,
        sessionLines: () -> List<TerminalSessionLine>,
    ): String = if (altScreenActive) {
        buildSnapshotText(snapshotLines())
    } else {
        buildSessionText(sessionLines())
    }
}
