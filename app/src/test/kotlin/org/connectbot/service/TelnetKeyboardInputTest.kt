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

import kotlinx.coroutines.test.runTest
import org.connectbot.data.entity.TelnetEchoMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TelnetKeyboardInputTest(private val encoding: String) {
    @Test
    fun keyboardTranscodesSplitUnicodeAndUsesTypedEnterInOrder() = runTest {
        val operations = mutableListOf<String>()
        val keyboard = TelnetKeyboardInput(encoding)
        val write: suspend (ByteArray) -> Unit = { operations.add(it.toString(terminalCharset(encoding))) }
        val enter: suspend () -> Unit = { operations.add("ENTER") }
        keyboard.write(byteArrayOf(0xc3.toByte()), write, enter)
        assertEquals(emptyList<String>(), operations)
        keyboard.write(byteArrayOf(0xa9.toByte(), 13, 10, 'B'.code.toByte(), 13), write, enter)
        assertEquals(listOf("é", "ENTER", "B", "ENTER"), operations)
    }

    @Test
    fun typedEnterEchoDoesNotCorruptMultibyteEncoding() = runTest {
        val rendered = StringBuilder()
        val input = TelnetInputDiscipline(TelnetEchoMode.ON, encoding) { rendered.append(it.toString(Charsets.UTF_8)) }
        val keyboard = TelnetKeyboardInput(encoding)
        keyboard.write("é\rB".toByteArray(), input::echo, input::carriageReturn)
        assertEquals("é\r\nB", rendered.toString())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "encoding={0}")
        fun data(): List<Array<String>> = listOf(
            arrayOf("UTF-8"),
            arrayOf("UTF-16LE"),
            arrayOf("UTF-16BE"),
            arrayOf("ISO-8859-1"),
            arrayOf("CP437"),
        )
    }
}
