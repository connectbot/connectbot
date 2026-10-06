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
import org.connectbot.telnetlib.TelnetInput
import org.connectbot.telnetlib.TelnetOutcome
import org.connectbot.transport.TelnetInputReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class TelnetInputTest {
    @Test
    fun orderedEventsDoNotLosePartialCharactersAndEofFlushesTail() = runTest {
        val events = mutableListOf<String>()
        val decoder = RelayDecoder("UTF-8", { events.add("text:$it") }, {})
        val input = ArrayDeque<TelnetInput?>()
        input.add(TelnetInput.Data(byteArrayOf('A'.code.toByte(), 0xe2.toByte())))
        input.add(TelnetInput.EchoChanged(false))
        input.add(TelnetInput.GoAhead)
        input.add(TelnetInput.EndOfRecord)
        input.add(TelnetInput.Data(byteArrayOf(0x82.toByte(), 0xac.toByte(), 0xc3.toByte())))
        input.add(TelnetInput.Diagnostic("boundary"))
        input.add(null)
        val reader = TelnetInputReader(decoder.charset, { TelnetOutcome.Success(input.removeFirst()) }, { events.add("echo:$it") }, { events.add(it) })
        val buffer = ByteArray(4096)
        while (true) {
            val count = reader.read(buffer, 0, buffer.size)
            if (count == -1) break
            decoder.accept(buffer, length = count)
        }
        decoder.finish()
        assertEquals(listOf("text:A", "echo:false", "text:€", "boundary", "text:�"), events)
    }

    @Test
    fun decoderHandlesLargeLegacyEncodedChunks() {
        val output = StringBuilder()
        val decoder = RelayDecoder("ISO-8859-1", output::append, {})
        decoder.accept(ByteArray(10000) { 0xe9.toByte() })
        decoder.finish()
        assertEquals("é".repeat(10000), output.toString())
    }

    @Test
    fun decoderEmitsConfiguredCharsetAsUtf8() {
        var bytes = byteArrayOf()
        RelayDecoder("CP437", {}, { bytes = it }).accept(byteArrayOf(0x82.toByte()))
        assertArrayEquals("é".toByteArray(), bytes)
    }

    @Test(expected = IOException::class)
    fun unexpectedCharsetAgreementCannotSwitchDecoder() = runTest {
        TelnetInputReader(
            Charsets.UTF_8,
            { TelnetOutcome.Success(TelnetInput.CharsetAgreed("ISO-8859-1")) },
            {},
            {},
        ).read(ByteArray(16), 0, 16)
    }

    @Test
    fun readerDrainsLargeDataBeforeFollowingEchoEvent() = runTest {
        val events = mutableListOf<String>()
        val input = ArrayDeque<TelnetInput?>()
        input.add(TelnetInput.Data("abcdef".toByteArray()))
        input.add(TelnetInput.EchoChanged(false))
        input.add(null)
        val reader = TelnetInputReader(Charsets.UTF_8, { TelnetOutcome.Success(input.removeFirst()) }, { events.add("echo:$it") }, {})
        val buffer = ByteArray(4)
        assertEquals(0, reader.read(buffer, 1, 0))
        repeat(3) {
            val count = reader.read(buffer, 1, 2)
            events.add(buffer.copyOfRange(1, 1 + count).toString(Charsets.UTF_8))
        }
        assertEquals(listOf("ab", "cd", "ef"), events)
        assertEquals(-1, reader.read(buffer, 1, 2))
        assertEquals(listOf("ab", "cd", "ef", "echo:false"), events)
    }

    @Test
    fun forcedEchoKeepsEraseStateAcrossNegotiationChanges() {
        val rendered = StringBuilder()
        val input = TelnetInputDiscipline(TelnetEchoMode.ON, "UTF-8") { rendered.append(it.toString(Charsets.UTF_8)) }
        input.echo("x".toByteArray())
        input.negotiatedEcho(false)
        input.echo(byteArrayOf(8))
        assertEquals("x\b \b", rendered.toString())
    }
}
