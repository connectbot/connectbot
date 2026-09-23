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

package org.connectbot.service.automation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel

/** Bounded remote-output capture; append never waits for the automation consumer. */
class AutomationOutput(private val capacity: Int = 65_536) {
    private enum class EscapeState {
        TEXT,
        ESCAPE,
        CONTROL_SEQUENCE,
        CONTROL_STRING,
        CONTROL_STRING_ESCAPE,
        INTERMEDIATE,
    }

    private val text = StringBuilder()
    private var escape = EscapeState.TEXT
    private var pendingCr = false
    val changed = Channel<Unit>(Channel.CONFLATED)
    val overflow = CompletableDeferred<Unit>()

    @Synchronized
    fun append(chunk: CharSequence) {
        if (overflow.isCompleted) return
        for (c in chunk) {
            when (escape) {
                EscapeState.ESCAPE -> escape = when (c) {
                    '[' -> EscapeState.CONTROL_SEQUENCE
                    ']', 'P', '^', '_' -> EscapeState.CONTROL_STRING
                    in ' '..'/' -> EscapeState.INTERMEDIATE
                    else -> EscapeState.TEXT
                }

                EscapeState.CONTROL_SEQUENCE -> if (c in '@'..'~') escape = EscapeState.TEXT

                EscapeState.CONTROL_STRING -> when (c) {
                    '\u0007', '\u009c' -> escape = EscapeState.TEXT
                    '\u001b' -> escape = EscapeState.CONTROL_STRING_ESCAPE
                }

                EscapeState.CONTROL_STRING_ESCAPE -> escape = if (c == '\\') EscapeState.TEXT else EscapeState.CONTROL_STRING

                EscapeState.INTERMEDIATE -> if (c in '0'..'~') escape = EscapeState.TEXT

                EscapeState.TEXT -> when (c) {
                    '\u001b' -> escape = EscapeState.ESCAPE

                    '\u009b' -> escape = EscapeState.CONTROL_SEQUENCE

                    '\u009d', '\u0090', '\u009e', '\u009f' -> escape = EscapeState.CONTROL_STRING

                    '\r' -> {
                        text.append('\n')
                        pendingCr = true
                    }

                    '\n' -> {
                        if (!pendingCr) text.append(c)
                        pendingCr = false
                    }

                    else -> {
                        pendingCr = false
                        if (c == '\t' || c >= ' ') text.append(c)
                    }
                }
            }
            if (text.length > capacity) {
                overflow.complete(Unit)
                text.clear()
                break
            }
        }
        changed.trySend(Unit)
    }

    @Synchronized
    fun snapshot(): String = text.toString()

    @Synchronized
    fun consume(end: Int) {
        text.delete(0, end.coerceIn(0, text.length))
    }

    @Synchronized
    fun clear() {
        text.clear()
    }
}
