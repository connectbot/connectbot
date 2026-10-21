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

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MacroFileFormatTest {
    @Test fun exportIsJsonAndImportAllocatesFreshIdentity() {
        val original = KeyboardMacro(name = "Login", document = MacroFormat.encode(listOf(MacroAction.Text("λ\r\n"), MacroAction.Key(key = "enter"), MacroAction.Bytes("1b"))))
        val exported = MacroFileFormat.encode(original)
        assertEquals("Login", JSONObject(exported).getString("name"))
        val restored = MacroFileFormat.decode(exported)
        assertNotEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(MacroFormat.decode(original.document), MacroFormat.decode(restored.document))
    }

    @Test fun importAcceptsJson5CommentsAndTrailingCommas() {
        val macro = MacroFileFormat.decode(
            """
            { // A shareable macro
                version: 1, name: 'Build',
                actions: [{ type: 'text', text: 'make', }, { type: 'key', key: 'enter', modifiers: [], },],
            }
            """.trimIndent(),
        )
        assertEquals("Build", macro.name)
        assertEquals(listOf(MacroAction.Text("make"), MacroAction.Key(key = "enter")), MacroFormat.decode(macro.document))
    }

    @Test fun existingMacroNamesRoundTripWithoutANewLengthRestriction() {
        val macro = KeyboardMacro(name = "x".repeat(2048), document = MacroFormat.encode(listOf(MacroAction.Text("run"))))
        assertEquals(macro.name, MacroFileFormat.decode(MacroFileFormat.encode(macro)).name)
    }

    @Test fun invalidFileDoesNotBecomeAMacro() {
        listOf("{version:2,name:'x',actions:[]}", "{version:1,name:'',actions:[]}", "{version:1,name:'x',actions:[{type:'invalid'}]}").forEach {
            assertTrue(runCatching { MacroFileFormat.decode(it) }.isFailure)
        }
    }
}
