/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeKeySequenceParserTest {

    @Test
    fun `empty input returns empty string`() {
        assertEquals("", SwipeKeySequenceParser.parse(""))
    }

    @Test
    fun `blank input returns empty string`() {
        assertEquals("", SwipeKeySequenceParser.parse("   "))
    }

    @Test
    fun `Ctrl+B N produces control-B followed by N`() {
        // Ctrl+B = 0x02
        assertEquals("N", SwipeKeySequenceParser.parse("Ctrl+B N"))
    }

    @Test
    fun `Ctrl+B P produces control-B followed by P`() {
        // Ctrl+B = 0x02
        assertEquals("P", SwipeKeySequenceParser.parse("Ctrl+B P"))
    }

    @Test
    fun `Ctrl+A N produces control-A followed by N`() {
        // Ctrl+A = 0x01
        assertEquals("N", SwipeKeySequenceParser.parse("Ctrl+A N"))
    }

    @Test
    fun `Ctrl+A P produces control-A followed by P`() {
        // Ctrl+A = 0x01
        assertEquals("P", SwipeKeySequenceParser.parse("Ctrl+A P"))
    }

    @Test
    fun `Ctrl+A Space produces control-A followed by space`() {
        assertEquals(" ", SwipeKeySequenceParser.parse("Ctrl+A Space"))
    }

    @Test
    fun `Esc produces escape character`() {
        assertEquals("", SwipeKeySequenceParser.parse("Esc"))
    }

    @Test
    fun `case insensitivity for keywords`() {
        assertEquals("", SwipeKeySequenceParser.parse("esc"))
        assertEquals("", SwipeKeySequenceParser.parse("ESC"))
        assertEquals("N", SwipeKeySequenceParser.parse("ctrl+B N"))
        assertEquals(" ", SwipeKeySequenceParser.parse("space"))
        assertEquals("\t", SwipeKeySequenceParser.parse("tab"))
        assertEquals("\r", SwipeKeySequenceParser.parse("enter"))
    }

    @Test
    fun `single character is passed through literally`() {
        assertEquals("a", SwipeKeySequenceParser.parse("a"))
        assertEquals("Z", SwipeKeySequenceParser.parse("Z"))
    }

    @Test
    fun `Tab produces tab character`() {
        assertEquals("\t", SwipeKeySequenceParser.parse("Tab"))
    }

    @Test
    fun `Enter produces carriage return`() {
        assertEquals("\r", SwipeKeySequenceParser.parse("Enter"))
    }

    @Test
    fun `complex sequence with Esc and characters`() {
        // Esc followed by literal characters
        assertEquals("b", SwipeKeySequenceParser.parse("Esc b"))
    }
}
