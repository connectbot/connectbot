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

import org.connectbot.terminal.TerminalEmulator

const val SNIPPET_MAX_LINES = 8
const val SNIPPET_MAX_CHARS = 600

/**
 * Extracts the tail of the most recent command's output for completion-event
 * consumers.
 *
 * Prefers the emulator's OSC 133-based [TerminalEmulator.getLastCommandOutput]
 * (exactly the finished command's output); falls back to the tail of the whole
 * session transcript with the trailing prompt line dropped when no semantic
 * markers are available.
 */
fun commandOutputSnippet(
    emulator: TerminalEmulator?,
    maxLines: Int = SNIPPET_MAX_LINES,
    maxChars: Int = SNIPPET_MAX_CHARS,
): String? {
    if (emulator == null) return null
    val output = emulator.getLastCommandOutput()
        ?: sessionTailFallback(emulator, maxLines)
        ?: return null
    return output.trimEnd().takeIf { it.isNotEmpty() }?.let { tailOf(it, maxLines, maxChars) }
}

private fun sessionTailFallback(emulator: TerminalEmulator, maxLines: Int): String? {
    val lines = TerminalSessionReader.readSessionLines(emulator)
    if (lines.isEmpty()) return null
    val text = TerminalTextUtils.buildSessionText(lines.takeLast(maxLines + 1))
    if (text.isEmpty()) return null
    // The last line is the shell prompt that just repainted after the command.
    return text.substringBeforeLast('\n', missingDelimiterValue = "")
}

internal fun tailOf(text: String, maxLines: Int, maxChars: Int): String {
    val lines = text.lines()
    var result = lines.takeLast(maxLines).joinToString("\n")
    var truncated = lines.size > maxLines
    if (result.length > maxChars) {
        var truncatedText = result.takeLast(maxChars)
        if (truncatedText.firstOrNull()?.isLowSurrogate() == true) {
            truncatedText = truncatedText.drop(1)
        }
        result = truncatedText
        truncated = true
    }
    return if (truncated) "…" + result.trimStart() else result
}

/**
 * Formats a duration for the completion notification, e.g. "37s", "2m 14s",
 * "1h 03m".
 */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "%dh %02dm".format(hours, minutes)
        minutes > 0 -> "%dm %02ds".format(minutes, seconds)
        else -> "${seconds}s"
    }
}
