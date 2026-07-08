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

package org.connectbot.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class TextEscapesTest {
    @Test
    fun parsesNamedEscapes() {
        assertEquals("a\nb", TextEscapes.parse("a\\nb"))
        assertEquals("a\tb", TextEscapes.parse("a\\tb"))
        assertEquals("a\rb", TextEscapes.parse("a\\rb"))
        assertEquals("\u001B", TextEscapes.parse("\\e"))
        assertEquals("\\", TextEscapes.parse("\\\\"))
    }

    @Test
    fun parsesHexEscapes() {
        assertEquals("\u001B", TextEscapes.parse("\\x1b"))
        assertEquals("A", TextEscapes.parse("\\x41"))
        assertEquals("a\u001Bb", TextEscapes.parse("a\\x1bb"))
    }

    @Test
    fun leavesUnknownEscapesLiteral() {
        assertEquals("\\q", TextEscapes.parse("\\q"))
        // Incomplete hex escape is passed through untouched.
        assertEquals("\\xZZ", TextEscapes.parse("\\xZZ"))
        assertEquals("\\x1", TextEscapes.parse("\\x1"))
    }

    @Test
    fun leavesTrailingBackslashLiteral() {
        assertEquals("abc\\", TextEscapes.parse("abc\\"))
    }

    @Test
    fun passesPlainTextThrough() {
        assertEquals("ls -la /etc", TextEscapes.parse("ls -la /etc"))
    }
}
