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

package org.connectbot.data.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MacroFormatTest {
    @Test fun stableDocumentHasNoImplementationNames() {
        val fixture = """{"version":1,"actions":[{"type":"key","character":"b","modifiers":["ctrl"]},{"type":"text","text":"l"},{"type":"bytes","hex":"00ff1b"}]}"""
        val actions = MacroFormat.decode(fixture)
        assertEquals(listOf(MacroAction.Key(character = "b", modifiers = listOf("ctrl")), MacroAction.Text("l"), MacroAction.Bytes("00ff1b")), actions)
        assertEquals(actions, MacroFormat.decode(MacroFormat.encode(actions)))
    }

    @Test fun bothEditorsRoundTripUnicodeAndLiteralEscapes() {
        val actions = listOf(
            MacroAction.Text("日本語 😀\n\\x1b\t\""),
            MacroAction.Key(character = "+", modifiers = listOf("ctrl", "alt")),
            MacroAction.Key(character = "😀"),
            MacroAction.Key(key = "arrow_up", modifiers = listOf("shift")),
            MacroAction.Bytes("000aff1b"),
        )
        assertEquals(actions, MacroFormat.parse(MacroFormat.format(actions)))
        assertEquals(actions, MacroFormat.decode(MacroFormat.encode(actions)))
    }

    @Test fun enterIsDifferentFromNewlineAndRawByte() {
        assertEquals(listOf(MacroAction.Key(key = "enter"), MacroAction.Text("\n"), MacroAction.Bytes("0a")), MacroFormat.parse("key enter\ntext \"\\n\"\nbytes 0a"))
    }

    @Test fun unknownVersionsActionsAndFieldsCannotBeExecuted() {
        listOf(
            """{"version":2,"actions":[{"type":"text","text":"x"}]}""",
            """{"version":1,"actions":[{"type":"delay","milliseconds":1}]}""",
            """{"version":1,"actions":[{"type":"text","text":"x","repeat":2}]}""",
            """{"version":1,"actions":[{"type":"key","key":"up","modifiers":[]}]}""",
        ).forEach { assertTrue(runCatching { MacroFormat.decode(it) }.isFailure) }
    }

    @Test fun errorsIdentifyTheLineAndRejectMalformedInput() {
        val error = runCatching { MacroFormat.parse("key enter\n  bytes 1g") }.exceptionOrNull()
        assertTrue(error!!.message!!.startsWith("Line 2, column 3:"))
        listOf("", "text hello", "text \"hi\" trailing", "key ctrl+ctrl+b", "key \"ab\"", "bytes 123", "key unknown").forEach {
            assertTrue("Accepted $it", runCatching { MacroFormat.parse(it) }.isFailure)
        }
    }
}
