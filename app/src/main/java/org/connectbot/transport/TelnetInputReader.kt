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

package org.connectbot.transport

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.connectbot.telnetlib.TelnetInput
import org.connectbot.telnetlib.TelnetOutcome
import java.io.IOException
import java.nio.charset.Charset

/** Single-consumer byte reader. Return preceding Data before processing the next ordered event. */
internal class TelnetInputReader(
    private val charset: Charset,
    private val receive: suspend () -> TelnetOutcome<TelnetInput?>,
    private val onEcho: (Boolean) -> Unit,
    private val onDiagnostic: (String) -> Unit,
) {
    private var pending = byteArrayOf()
    private var position = 0

    suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length >= 0 && offset <= buffer.size && length <= buffer.size - offset)
        if (length == 0) return 0
        while (true) {
            currentCoroutineContext().ensureActive()
            if (position < pending.size) {
                val count = minOf(length, pending.size - position)
                pending.copyInto(buffer, offset, position, position + count)
                position += count
                if (position == pending.size) pending = byteArrayOf()
                return count
            }
            when (val result = receive()) {
                is TelnetOutcome.Failure -> throw TelnetOperationException(result.error)

                is TelnetOutcome.Success -> when (val input = result.value) {
                    null -> return -1

                    is TelnetInput.Data -> {
                        pending = input.bytes
                        position = 0
                    }

                    is TelnetInput.EchoChanged -> onEcho(input.localEcho)

                    is TelnetInput.CharsetAgreed -> if (!input.name.equals(charset.name(), ignoreCase = true)) {
                        throw IOException("Unexpected Telnet charset agreement: ${input.name}")
                    }

                    is TelnetInput.Diagnostic -> onDiagnostic(input.message)

                    TelnetInput.GoAhead, TelnetInput.EndOfRecord -> Unit
                }
            }
        }
    }
}
