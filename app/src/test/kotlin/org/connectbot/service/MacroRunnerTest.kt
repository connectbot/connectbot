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

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.connectbot.data.keyboard.MacroAction
import org.connectbot.data.keyboard.MacroFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MacroRunnerTest {
    private class Output : MacroOutput {
        val events = mutableListOf<String>()
        var connected = true
        override suspend fun checkConnected() {
            check(connected)
        }
        override suspend fun accepted() {
            events.add("accepted")
        }
        override suspend fun text(text: String) {
            events.add("text:$text")
        }
        override suspend fun bytes(bytes: ByteArray) {
            events.add("bytes:${bytes.joinToString()}")
        }
        override suspend fun key(key: MacroAction.Key) {
            delay(10)
            events.add("key:${key.key ?: key.character}:${key.modifiers}")
        }
    }

    @Test fun mixedStepsWaitForSemanticOutputAndPreserveOrder() = runTest {
        val output = Output()
        runKeyboardMacro(MacroFormat.encode(listOf(MacroAction.Key(character = "b", modifiers = listOf("ctrl")), MacroAction.Text("l"), MacroAction.Bytes("00ff"))), output)
        assertEquals(listOf("accepted", "key:b:[ctrl]", "text:l", "bytes:0, -1"), output.events)
    }

    @Test fun invalidTailCannotSendValidPrefix() = runTest {
        val output = Output()
        assertTrue(runCatching { runKeyboardMacro("""{"version":1,"actions":[{"type":"text","text":"danger"},{"type":"unknown"}]}""", output) }.isFailure)
        assertTrue(output.events.isEmpty())
    }

    @Test fun disconnectedSessionDoesNotConsumeModifiersOrSend() = runTest {
        val output = Output().apply { connected = false }
        assertTrue(runCatching { runKeyboardMacro(MacroFormat.encode(listOf(MacroAction.Text("x"))), output) }.isFailure)
        assertTrue(output.events.isEmpty())
    }

    @Test fun outputFailureStopsRemainingSteps() = runTest {
        var sent = 0
        val output = object : MacroOutput {
            override suspend fun checkConnected() = Unit
            override suspend fun accepted() = Unit
            override suspend fun text(text: String) {
                sent++
                error("disconnected")
            }
            override suspend fun bytes(bytes: ByteArray) {
                sent++
            }
            override suspend fun key(key: MacroAction.Key) {
                sent++
            }
        }
        assertTrue(runCatching { runKeyboardMacro(MacroFormat.encode(listOf(MacroAction.Text("a"), MacroAction.Text("b"))), output) }.isFailure)
        assertEquals(1, sent)
    }
}
