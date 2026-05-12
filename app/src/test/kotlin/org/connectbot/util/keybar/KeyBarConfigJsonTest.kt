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
package org.connectbot.util.keybar

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyBarConfigJsonTest {

    @Test
    fun `round-trips a list of builtins`() {
        val original = listOf(
            KeyEntry.Builtin(BuiltinKeyId.CTRL, visible = true),
            KeyEntry.Builtin(BuiltinKeyId.ESC, visible = true),
            KeyEntry.Builtin(BuiltinKeyId.HOME, visible = false),
        )
        val json = KeyBarConfigJson.encode(original)
        val decoded = KeyBarConfigJson.decode(json)
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `round-trips macros`() {
        val original = listOf(
            KeyEntry.Macro(label = "sudo", text = "sudo "),
            KeyEntry.Macro(label = "tmux-prefix", text = "\\x02"),
        )
        val json = KeyBarConfigJson.encode(original)
        assertThat(KeyBarConfigJson.decode(json)).isEqualTo(original)
    }

    @Test
    fun `default visible flag is true when absent in JSON`() {
        // Old/short form for builtins with no "v" field should default to visible.
        val json = """[{"t":"b","id":"CTRL"}]"""
        assertThat(KeyBarConfigJson.decode(json))
            .containsExactly(KeyEntry.Builtin(BuiltinKeyId.CTRL, visible = true))
    }

    @Test
    fun `unknown builtin id is skipped on load`() {
        val json = """[{"t":"b","id":"CTRL"},{"t":"b","id":"BOGUS"},{"t":"b","id":"ESC"}]"""
        assertThat(KeyBarConfigJson.decode(json))
            .containsExactly(
                KeyEntry.Builtin(BuiltinKeyId.CTRL, visible = true),
                KeyEntry.Builtin(BuiltinKeyId.ESC, visible = true),
            )
    }

    @Test
    fun `unknown entry type is skipped on load`() {
        val json = """[{"t":"b","id":"CTRL"},{"t":"future","payload":"x"},{"t":"m","l":"L","x":"X"}]"""
        assertThat(KeyBarConfigJson.decode(json))
            .containsExactly(
                KeyEntry.Builtin(BuiltinKeyId.CTRL, visible = true),
                KeyEntry.Macro(label = "L", text = "X"),
            )
    }

    @Test
    fun `malformed JSON returns empty list rather than crashing`() {
        // Defensive: a corrupted pref blob shouldn't brick the app.
        assertThat(KeyBarConfigJson.decode("not json")).isEmpty()
        assertThat(KeyBarConfigJson.decode("")).isEmpty()
    }

    @Test
    fun `encoded JSON uses short keys`() {
        val json = KeyBarConfigJson.encode(
            listOf(
                KeyEntry.Builtin(BuiltinKeyId.CTRL, visible = true),
                KeyEntry.Macro("L", "X"),
            ),
        )
        // We rely on org.json which does not guarantee field order; assert presence.
        assertThat(json).contains("\"t\":\"b\"")
        assertThat(json).contains("\"id\":\"CTRL\"")
        assertThat(json).contains("\"t\":\"m\"")
        assertThat(json).contains("\"l\":\"L\"")
        assertThat(json).contains("\"x\":\"X\"")
    }

    @Test
    fun `omits visible flag when true to keep payload small`() {
        val json = KeyBarConfigJson.encode(
            listOf(KeyEntry.Builtin(BuiltinKeyId.CTRL, visible = true)),
        )
        assertThat(json).doesNotContain("\"v\":true")
    }

    @Test
    fun `emits visible flag when false`() {
        val json = KeyBarConfigJson.encode(
            listOf(KeyEntry.Builtin(BuiltinKeyId.CTRL, visible = false)),
        )
        assertThat(json).contains("\"v\":false")
    }
}
