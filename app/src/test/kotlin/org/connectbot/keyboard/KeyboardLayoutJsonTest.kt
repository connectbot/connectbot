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
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardLayoutJsonTest {

    @Test
    fun roundTripsEveryKeyType() {
        val spec = KeyboardLayoutSpec(
            rows = listOf(
                listOf(
                    KeySpec.Special(SpecialKey.ESC),
                    KeySpec.Modifier(ModifierKey.CTRL),
                    KeySpec.Text("cd /", sendEnter = true),
                    KeySpec.Combo(ctrl = true, ch = 'c'),
                    KeySpec.Combo(alt = true, special = SpecialKey.LEFT),
                    KeySpec.FnGrid(),
                ),
                listOf(
                    KeySpec.Special(SpecialKey.UP, label = "Up", icon = "arrow_up"),
                    KeySpec.Text("|"),
                ),
            ),
        )

        val decoded = KeyboardLayoutJson.decode(KeyboardLayoutJson.encode(spec))
        assertEquals(spec, decoded)
    }

    @Test
    fun preservesLabelAndIconOverrides() {
        val spec = KeyboardLayoutSpec(
            rows = listOf(listOf(KeySpec.FnGrid(label = "Fn", icon = "function"))),
        )
        val decoded = KeyboardLayoutJson.decode(KeyboardLayoutJson.encode(spec))
        val key = decoded!!.rows[0][0] as KeySpec.FnGrid
        assertEquals("Fn", key.label)
        assertEquals("function", key.icon)
    }

    @Test
    fun explicitNullLabelAndIconDecodeAsAbsent() {
        val json = """
            {"version":1,"rows":[[
              {"type":"text","text":"x","label":null,"icon":null}
            ]]}
        """.trimIndent()

        val key = KeyboardLayoutJson.decode(json)!!.rows[0][0] as KeySpec.Text
        assertNull(key.label)
        assertNull(key.icon)
    }

    @Test
    fun skipsUnknownKeyTypes() {
        val json = """
            {"version":1,"rows":[[
              {"type":"special","key":"ESC"},
              {"type":"future_key","foo":"bar"},
              {"type":"text","text":"x"}
            ]]}
        """.trimIndent()
        val decoded = KeyboardLayoutJson.decode(json)
        assertEquals(
            listOf(KeySpec.Special(SpecialKey.ESC), KeySpec.Text("x")),
            decoded!!.rows[0],
        )
    }

    @Test
    fun skipsUnknownSpecialKeyNames() {
        val json = """{"version":1,"rows":[[{"type":"special","key":"NOPE"}]]}"""
        // The only key is unrecognized, so the row is empty and the layout has no keys.
        assertEquals(emptyList<KeySpec>(), KeyboardLayoutJson.decode(json)!!.rows[0])
    }

    @Test
    fun returnsNullForMalformedJson() {
        assertNull(KeyboardLayoutJson.decode("not json"))
        assertNull(KeyboardLayoutJson.decode("{}"))
        assertNull(KeyboardLayoutJson.decode("""{"version":1,"rows":[]}"""))
    }

    @Test
    fun encodesFormatVersion() {
        val json = KeyboardLayoutJson.encode(DefaultKeyboardLayouts.default)
        assertEquals(1, org.json.JSONObject(json).getInt("version"))
    }
}
