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

package org.connectbot.keyboard

/**
 * Backslash escape sequences for custom text keys: `\n`, `\t`, `\r`,
 * `\e` (escape), `\xHH` (hex byte) and `\\`. Unknown escapes and a
 * trailing backslash pass through literally.
 */
object TextEscapes {
    fun parse(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c != '\\' || i == input.length - 1) {
                sb.append(c)
                i++
                continue
            }
            when (input[i + 1]) {
                'n' -> {
                    sb.append('\n')
                    i += 2
                }
                't' -> {
                    sb.append('\t')
                    i += 2
                }
                'r' -> {
                    sb.append('\r')
                    i += 2
                }
                'e' -> {
                    sb.append('\u001B')
                    i += 2
                }
                '\\' -> {
                    sb.append('\\')
                    i += 2
                }
                'x' -> {
                    val hex = input.substring(i + 2, minOf(i + 4, input.length))
                    val value = if (hex.length == 2) hex.toIntOrNull(16) else null
                    if (value != null) {
                        sb.append(value.toChar())
                        i += 4
                    } else {
                        sb.append(c)
                        i++
                    }
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }
}
