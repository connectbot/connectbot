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

package org.connectbot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class JsonImportReaderTest {
    @Test
    fun readPreservesUtf8Content() {
        val json = """{"name":"café"}"""

        assertEquals(json, JsonImportReader.read(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))))
    }

    @Test
    fun readStopsWhenSizeLimitIsExceeded() {
        val input = object : InputStream() {
            var bytesRead = 0

            override fun read(): Int {
                bytesRead++
                return 'x'.code
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                buffer.fill('x'.code.toByte(), offset, offset + length)
                bytesRead += length
                return length
            }
        }

        assertThrows(JsonImportTooLargeException::class.java) {
            JsonImportReader.read(input)
        }
        assertTrue(input.bytesRead <= JsonImportReader.MAX_BYTES + 8192)
    }
}
