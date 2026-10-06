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

package org.connectbot.service

/** Translate emulator UTF-8 callbacks, retaining partial characters and typed Enter boundaries. */
internal class TelnetKeyboardInput(encoding: String) {
    private val output = mutableListOf<String>()
    private val charset = terminalCharset(encoding)
    private val decoder = RelayDecoder("UTF-8", { output.add(it) }, {})

    suspend fun write(
        bytes: ByteArray,
        write: suspend (ByteArray) -> Unit,
        carriageReturn: suspend () -> Unit,
    ) {
        output.clear()
        decoder.accept(bytes)
        for (chunk in output) {
            var start = 0
            var index = 0
            while (index < chunk.length) {
                if (chunk[index] == '\r') {
                    if (index > start) write(chunk.substring(start, index).toByteArray(charset))
                    carriageReturn()
                    if (index + 1 < chunk.length && chunk[index + 1] == '\n') index++
                    start = index + 1
                }
                index++
            }
            if (start < chunk.length) write(chunk.substring(start).toByteArray(charset))
        }
    }
}
