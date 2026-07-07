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
 * Encodes local input as tmux commands. Control mode has no pane stdin, so
 * keystrokes go through `send-keys -H` (hex bytes, exactly what the local
 * emulator produced — no key-name mapping layer) and pastes go through a
 * tmux buffer + `paste-buffer -p`, which gives remote apps a real bracketed
 * paste when they enabled it.
 */
object TmuxInputEncoder {
    private const val MAX_KEY_BYTES_PER_COMMAND = 256
    private const val PASTE_CHUNK_CHARS = 1024
    private const val PASTE_BUFFER_NAME = "connectbot-paste"

    /**
     * Chunks raw keyboard bytes into one or more
     * `send-keys -t %pane -H aa bb …` commands.
     */
    fun toSendKeysCommands(
        paneId: String,
        bytes: ByteArray,
        maxBytesPerCommand: Int = MAX_KEY_BYTES_PER_COMMAND,
    ): List<String> {
        if (bytes.isEmpty()) return emptyList()
        return (bytes.indices step maxBytesPerCommand).map { start ->
            val end = minOf(start + maxBytesPerCommand, bytes.size)
            buildString(16 + (end - start) * 3) {
                append("send-keys -t ")
                append(paneId)
                append(" -H")
                for (i in start until end) {
                    append(' ')
                    val v = bytes[i].toInt() and 0xff
                    if (v < 0x10) append('0')
                    append(v.toString(16))
                }
            }
        }
    }

    /**
     * Encodes a paste as buffer-load commands plus a final
     * `paste-buffer -p -d`. The text is chunked into `set-buffer` /
     * `set-buffer -a` appends so no single command line grows unbounded.
     */
    fun toPasteCommands(
        paneId: String,
        text: String,
        chunkChars: Int = PASTE_CHUNK_CHARS,
    ): List<String> {
        if (text.isEmpty()) return emptyList()
        val commands = mutableListOf<String>()
        var index = 0
        var first = true
        while (index < text.length) {
            val end = minOf(index + chunkChars, text.length)
            val chunk = quoteDouble(text.substring(index, end))
            val appendFlag = if (first) "" else "a"
            commands.add("set-buffer -${appendFlag}b $PASTE_BUFFER_NAME -- \"$chunk\"")
            first = false
            index = end
        }
        commands.add("paste-buffer -p -d -b $PASTE_BUFFER_NAME -t $paneId")
        return commands
    }

    /**
     * Escapes text for a tmux double-quoted argument: backslash, double
     * quote, and `$` get backslash-escaped; control bytes (including
     * newlines, which would otherwise terminate the control-mode command
     * line) become `\ooo` octal escapes, which tmux's parser decodes.
     */
    internal fun quoteDouble(text: String): String = buildString(text.length + 16) {
        for (c in text) {
            when {
                c == '\\' || c == '"' || c == '$' || c == '`' -> {
                    append('\\')
                    append(c)
                }

                c.code < 0x20 || c.code == 0x7f -> {
                    append('\\')
                    append(String.format("%03o", c.code))
                }

                else -> append(c)
            }
        }
    }
}
