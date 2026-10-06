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

import org.connectbot.data.entity.TelnetEchoMode

/** Character-at-a-time display echo; never feeds user escape sequences back into the emulator. */
internal class TelnetInputDiscipline(
    private val mode: TelnetEchoMode,
    private val encoding: String,
    private val display: (ByteArray) -> Unit,
) {
    private enum class Escape { NONE, ESC, CSI, STRING, STRING_ESC }
    private var escape = Escape.NONE
    private var localEcho = true
    private var displayed = 0
    private var previousCr = false
    private var decoder = makeDecoder()

    @Synchronized
    fun negotiatedEcho(localEcho: Boolean) {
        if (mode == TelnetEchoMode.AUTO && this.localEcho != localEcho) {
            decoder = makeDecoder()
            escape = Escape.NONE
            displayed = 0
            previousCr = false
        }
        this.localEcho = localEcho
    }

    @Synchronized
    fun echo(bytes: ByteArray) {
        if (echoEnabled()) decoder.accept(bytes)
    }

    @Synchronized
    fun carriageReturn() {
        if (echoEnabled()) render("\r")
    }

    private fun echoEnabled(): Boolean = when (mode) {
        TelnetEchoMode.AUTO -> localEcho
        TelnetEchoMode.ON -> true
        TelnetEchoMode.OFF -> false
    }

    private fun makeDecoder() = RelayDecoder(encoding, onText = ::render, onBytes = {})

    private fun render(text: String) {
        val output = StringBuilder()
        text.codePoints().forEach { cp ->
            if (cp == 10 && previousCr) {
                previousCr = false
                return@forEach
            }
            previousCr = cp == 13
            when (escape) {
                Escape.ESC -> escape = when (cp) {
                    '['.code -> Escape.CSI
                    ']'.code, 'P'.code, '^'.code, '_'.code -> Escape.STRING
                    else -> Escape.NONE
                }

                Escape.CSI -> if (cp in 0x40..0x7e) escape = Escape.NONE

                Escape.STRING -> when (cp) {
                    7 -> escape = Escape.NONE
                    27 -> escape = Escape.STRING_ESC
                }

                Escape.STRING_ESC -> escape = if (cp == 92) Escape.NONE else Escape.STRING

                Escape.NONE -> when (cp) {
                    27 -> escape = Escape.ESC

                    13 -> {
                        output.append("\r\n")
                        displayed = 0
                    }

                    10 -> {
                        output.append("\r\n")
                        displayed = 0
                    }

                    9 -> {
                        output.append('\t')
                        displayed++
                    }

                    8, 127 -> if (displayed > 0) {
                        output.append("\b \b")
                        displayed--
                    }

                    in 0..31, in 128..159 -> Unit

                    else -> {
                        output.appendCodePoint(cp)
                        displayed++
                    }
                }
            }
        }
        if (output.isNotEmpty()) display(output.toString().toByteArray(Charsets.UTF_8))
    }
}
