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

package org.connectbot.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class TerminalEncodingsTest {
    @Test
    fun commonEncodings_includeJapaneseEncodings() {
        assertTrue(TerminalEncodings.commonEncodings.contains("EUC-JP"))
        assertTrue(TerminalEncodings.commonEncodings.contains("Shift_JIS"))
        assertTrue(TerminalEncodings.commonEncodings.contains("ISO-2022-JP"))
    }

    @Test
    fun allEncodings_containsEucJpAndCp437() {
        assertTrue(TerminalEncodings.allEncodings.contains("EUC-JP"))
        assertTrue(TerminalEncodings.allEncodings.contains("CP437"))
    }

    @Test
    fun encodeTerminalInput_leavesUtf8Unchanged() {
        val input = "日本語".toByteArray(Charsets.UTF_8)

        assertArrayEquals(input, TerminalEncodings.encodeTerminalInput(input, "UTF-8"))
    }

    @Test
    fun encodeTerminalInput_transcodesUtf8ToEucJp() {
        val text = "日本語"
        val input = text.toByteArray(Charsets.UTF_8)
        val expected = text.toByteArray(Charset.forName("EUC-JP"))

        assertArrayEquals(expected, TerminalEncodings.encodeTerminalInput(input, "EUC-JP"))
    }

    @Test
    fun charsetFor_supportsCp437Alias() {
        assertEquals("IBM437", TerminalEncodings.charsetFor("CP437").name())
    }
}
