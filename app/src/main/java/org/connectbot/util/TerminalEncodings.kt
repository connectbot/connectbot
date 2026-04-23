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

import org.apache.harmony.niochar.charset.additional.IBM437
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.util.TreeSet

/**
 * Supported terminal encodings for host/profile configuration and terminal I/O.
 */
object TerminalEncodings {
    const val DEFAULT = "UTF-8"
    const val CP437 = "CP437"

    private val customEncodings = setOf(CP437)

    private val preferredEncodings = listOf(
        "UTF-8",
        "EUC-JP",
        "Shift_JIS",
        "windows-31j",
        "ISO-2022-JP",
        "GB18030",
        "Big5",
        "EUC-KR",
        "ISO-8859-1",
        "ISO-8859-15",
        "US-ASCII",
        "windows-1252",
        CP437,
    )

    val commonEncodings: List<String> = preferredEncodings.filter(::isSupported)

    val allEncodings: List<String> = run {
        TreeSet(String.CASE_INSENSITIVE_ORDER).apply {
            addAll(commonEncodings)
            addAll(customEncodings)
            addAll(Charset.availableCharsets().keys)
        }.toList()
    }

    fun isSupported(encoding: String): Boolean {
        return encoding.equals(CP437, ignoreCase = true) ||
            try {
                Charset.isSupported(encoding)
            } catch (_: IllegalCharsetNameException) {
                false
            }
    }

    fun charsetFor(encoding: String): Charset {
        return if (encoding.equals(CP437, ignoreCase = true)) {
            IBM437("IBM437", arrayOf(CP437))
        } else {
            Charset.forName(encoding)
        }
    }

    /**
     * libvterm emits printable keyboard input as UTF-8. Remote hosts using a legacy
     * encoding need those bytes transcoded before they are written to the transport.
     */
    fun encodeTerminalInput(data: ByteArray, encoding: String): ByteArray {
        if (data.isEmpty() || encoding.equals(DEFAULT, ignoreCase = true)) {
            return data
        }

        return data.toString(Charsets.UTF_8).toByteArray(charsetFor(encoding))
    }
}
