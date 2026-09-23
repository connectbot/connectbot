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

import org.connectbot.data.keyboard.MacroAction
import org.connectbot.data.keyboard.MacroFormat

/** The adapter must finish queuing each step's output before returning. */
interface MacroOutput {
    suspend fun checkConnected()
    suspend fun accepted()
    suspend fun text(text: String)
    suspend fun bytes(bytes: ByteArray)
    suspend fun key(key: MacroAction.Key)
}

/** Preflight the entire document before changing modifier state or emitting any input. */
suspend fun runKeyboardMacro(document: String, output: MacroOutput) {
    val actions = MacroFormat.decode(document)
    output.checkConnected()
    output.accepted()
    actions.forEach { action ->
        output.checkConnected()
        when (action) {
            is MacroAction.Text -> output.text(action.text)
            is MacroAction.Bytes -> output.bytes(action.hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            is MacroAction.Key -> output.key(action)
        }
    }
}
