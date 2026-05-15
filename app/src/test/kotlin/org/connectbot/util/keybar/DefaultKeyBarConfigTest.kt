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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DefaultKeyBarConfigTest {

    @Test
    fun `includes every builtin exactly once`() {
        val default = defaultKeyBarConfig()
        val ids = default.filterIsInstance<KeyEntry.Builtin>().map { it.id }
        assertThat(ids).containsExactlyInAnyOrderElementsOf(BuiltinKeyId.entries)
    }

    @Test
    fun `visible-by-default matches the spec`() {
        val visible = defaultKeyBarConfig()
            .filterIsInstance<KeyEntry.Builtin>()
            .filter { it.visible }
            .map { it.id }
        assertThat(visible).containsExactly(
            BuiltinKeyId.CTRL, BuiltinKeyId.ESC, BuiltinKeyId.TAB,
            BuiltinKeyId.UP, BuiltinKeyId.DOWN, BuiltinKeyId.LEFT, BuiltinKeyId.RIGHT,
            BuiltinKeyId.ENTER,
            BuiltinKeyId.HOME, BuiltinKeyId.END, BuiltinKeyId.PG_UP, BuiltinKeyId.PG_DN,
            BuiltinKeyId.F1, BuiltinKeyId.F2, BuiltinKeyId.F3, BuiltinKeyId.F4,
            BuiltinKeyId.F5, BuiltinKeyId.F6, BuiltinKeyId.F7, BuiltinKeyId.F8,
            BuiltinKeyId.F9, BuiltinKeyId.F10, BuiltinKeyId.F11, BuiltinKeyId.F12,
        )
    }

    @Test
    fun `hidden-by-default appear at the tail in spec order`() {
        val hidden = defaultKeyBarConfig()
            .filterIsInstance<KeyEntry.Builtin>()
            .filter { !it.visible }
            .map { it.id }
        assertThat(hidden).containsExactly(
            BuiltinKeyId.ALT,
            BuiltinKeyId.SHIFT,
            BuiltinKeyId.BACKSPACE,
            BuiltinKeyId.DELETE,
            BuiltinKeyId.INSERT,
        )
    }

    @Test
    fun `default contains no macros`() {
        assertThat(defaultKeyBarConfig().filterIsInstance<KeyEntry.Macro>()).isEmpty()
    }
}
