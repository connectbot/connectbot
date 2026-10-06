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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TelnetInputTest {
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
