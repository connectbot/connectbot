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

import org.apache.harmony.niochar.charset.additional.IBM437
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Streaming decoder shared by byte transports and ordered Telnet input. */
internal class RelayDecoder(
    encoding: String,
    private val onText: (String) -> Unit,
    private val onBytes: (ByteArray) -> Unit,
) {
    var charset: Charset = terminalCharset(encoding)
        private set
    private var decoder = newDecoder(charset)
    private val source = ByteBuffer.allocate(4096)
    private val characters = CharBuffer.allocate(4096)

    fun setCharset(encoding: String) {
        val next = terminalCharset(encoding)
        if (next != charset) {
            charset = next
            decoder = newDecoder(next)
        }
    }

    fun accept(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        var position = offset
        val end = offset + length
        while (position < end) {
            val count = minOf(source.remaining(), end - position)
            if (count == 0) throw IOException("Decoder made no progress")
            source.put(bytes, position, count)
            position += count
            decode(false)
        }
    }

    fun finish() {
        decode(true)
        while (true) {
            val result = decoder.flush(characters)
            emit()
            if (result.isUnderflow) break
            if (result.isError) result.throwException()
        }
    }

    private fun decode(endOfInput: Boolean) {
        source.flip()
        while (true) {
            val result = decoder.decode(source, characters, endOfInput)
            emit()
            if (result.isUnderflow) break
            if (result.isError) result.throwException()
        }
        source.compact()
    }

    private fun emit() {
        characters.flip()
        if (characters.hasRemaining()) {
            val text = characters.toString()
            onText(text)
            onBytes(text.toByteArray(Charsets.UTF_8))
        }
        characters.clear()
    }

    private fun newDecoder(charset: Charset) = charset.newDecoder().apply {
        onMalformedInput(CodingErrorAction.REPLACE)
        onUnmappableCharacter(CodingErrorAction.REPLACE)
    }
}

internal fun terminalCharset(encoding: String): Charset = if (encoding == "CP437") {
    IBM437("IBM437", arrayOf("IBM437", "CP437"))
} else {
    Charset.forName(encoding)
}
