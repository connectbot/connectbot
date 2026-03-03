/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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
 * Parses a human-readable key sequence string (e.g., "Ctrl+B N") into raw bytes
 * suitable for [org.connectbot.service.TerminalBridge.injectString].
 *
 * Supported tokens (space-separated):
 * - `Ctrl+X` → control character (X & 0x1F)
 * - `Esc` → escape character (\u001B)
 * - `Space` → literal space
 * - `Tab` → tab character (\t)
 * - `Enter` → carriage return (\r)
 * - Single character → literal character
 */
object SwipeKeySequenceParser {
    private val WHITESPACE = "\\s+".toRegex()

    fun parse(sequence: String): String {
        if (sequence.isBlank()) return ""

        return sequence.trim().split(WHITESPACE).joinToString("") { token ->
            when {
                token.equals("Esc", ignoreCase = true) -> "\u001B"

                token.equals("Space", ignoreCase = true) -> " "

                token.equals("Tab", ignoreCase = true) -> "\t"

                token.equals("Enter", ignoreCase = true) -> "\r"

                token.startsWith("Ctrl+", ignoreCase = true) && token.length == 6 -> {
                    val ch = token[5].uppercaseChar()
                    (ch.code and 0x1F).toChar().toString()
                }

                token.length == 1 -> token

                else -> token
            }
        }
    }
}
