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

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTextUtilsTest {
    // Real terminal lines are padded to the full column width with spaces.
    private fun line(text: String, softWrapped: Boolean = false, cols: Int = 20) = TerminalSessionLine(text.padEnd(cols), softWrapped)

    @Test
    fun normalizeLineBreaks_convertsLineFeedsToCarriageReturns() {
        assertEquals(
            "echo one\recho two\recho three",
            TerminalTextUtils.normalizeLineBreaks("echo one\necho two\necho three"),
        )
    }

    @Test
    fun normalizeLineBreaks_convertsCrlfToSingleCarriageReturn() {
        assertEquals(
            "echo one\recho two\r",
            TerminalTextUtils.normalizeLineBreaks("echo one\r\necho two\r\n"),
        )
    }

    @Test
    fun normalizeLineBreaks_keepsBareCarriageReturns() {
        assertEquals(
            "echo one\recho two",
            TerminalTextUtils.normalizeLineBreaks("echo one\recho two"),
        )
    }

    @Test
    fun normalizeLineBreaks_handlesMixedLineEndings() {
        assertEquals(
            "one\rtwo\rthree\rfour",
            TerminalTextUtils.normalizeLineBreaks("one\r\ntwo\nthree\rfour"),
        )
    }

    @Test
    fun normalizeLineBreaks_leavesTextWithoutLineBreaksUntouched() {
        assertEquals("ls -la", TerminalTextUtils.normalizeLineBreaks("ls -la"))
        assertEquals("", TerminalTextUtils.normalizeLineBreaks(""))
    }

    @Test
    fun buildSessionText_joinsLinesWithNewlinesAndTrimsCellPadding() {
        assertEquals(
            "$ echo hello\nhello\n$",
            TerminalTextUtils.buildSessionText(
                listOf(line("$ echo hello"), line("hello"), line("$")),
            ),
        )
    }

    @Test
    fun buildSessionText_joinsSoftWrappedLinesWithoutLineBreak() {
        assertEquals(
            "a-very-long-command-line\ndone",
            TerminalTextUtils.buildSessionText(
                listOf(
                    line("a-very-long-", softWrapped = true, cols = 12),
                    line("command-line", cols = 12),
                    line("done", cols = 12),
                ),
            ),
        )
    }

    @Test
    fun buildSessionText_dropsTrailingBlankScreenLines() {
        assertEquals(
            "$ uptime\n12:00 up 3 days",
            TerminalTextUtils.buildSessionText(
                listOf(line("$ uptime"), line("12:00 up 3 days"), line(""), line("")),
            ),
        )
    }

    @Test
    fun buildSessionText_preservesBlankLinesBetweenContent() {
        assertEquals(
            "first\n\nsecond",
            TerminalTextUtils.buildSessionText(
                listOf(line("first"), line(""), line("second")),
            ),
        )
    }

    @Test
    fun buildSessionText_returnsEmptyStringForBlankSession() {
        assertEquals("", TerminalTextUtils.buildSessionText(emptyList()))
        assertEquals("", TerminalTextUtils.buildSessionText(listOf(line(""), line(""))))
    }
}
