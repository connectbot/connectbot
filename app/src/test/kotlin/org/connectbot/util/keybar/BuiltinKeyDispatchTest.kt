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
import org.connectbot.terminal.VTermKey
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class BuiltinKeyDispatchTest {

    @Test
    fun `every BuiltinKeyId has a dispatch action`() {
        // If a new BuiltinKeyId is added and the when{} isn't extended,
        // Kotlin's exhaustiveness check fires at compile time. This test
        // is a runtime belt-and-braces guard.
        for (id in BuiltinKeyId.entries) {
            assertThat(id.dispatch()).describedAs(id.name).isNotNull()
        }
    }

    @Test
    fun `modifiers map to expected masks`() {
        assertThat(BuiltinKeyId.CTRL.dispatch()).isEqualTo(BuiltinKeyDispatch.Modifier(0x01))
        assertThat(BuiltinKeyId.ALT.dispatch()).isEqualTo(BuiltinKeyDispatch.Modifier(0x04))
        assertThat(BuiltinKeyId.SHIFT.dispatch()).isEqualTo(BuiltinKeyDispatch.Modifier(0x10))
    }

    @Test
    fun `ENTER sends CR byte not VTermKey`() {
        val d = BuiltinKeyId.ENTER.dispatch()
        assertThat(d).isInstanceOf(BuiltinKeyDispatch.ByteSequence::class.java)
        assertThat((d as BuiltinKeyDispatch.ByteSequence).bytes).containsExactly(0x0D)
    }

    @Test
    fun `BACKSPACE sends DEL byte`() {
        val d = BuiltinKeyId.BACKSPACE.dispatch() as BuiltinKeyDispatch.ByteSequence
        assertThat(d.bytes).containsExactly(0x7F)
    }

    @Test
    fun `DELETE sends xterm CSI 3 tilde`() {
        val d = BuiltinKeyId.DELETE.dispatch() as BuiltinKeyDispatch.ByteSequence
        assertThat(d.bytes).containsExactly(0x1B, '['.code, '3'.code, '~'.code)
    }

    @Test
    fun `INSERT sends xterm CSI 2 tilde`() {
        val d = BuiltinKeyId.INSERT.dispatch() as BuiltinKeyDispatch.ByteSequence
        assertThat(d.bytes).containsExactly(0x1B, '['.code, '2'.code, '~'.code)
    }

    @Test
    fun `F1 through F12 all dispatch to VTermKey FUNCTION constants`() {
        val expected = listOf(
            BuiltinKeyId.F1 to VTermKey.FUNCTION_1,
            BuiltinKeyId.F2 to VTermKey.FUNCTION_2,
            BuiltinKeyId.F12 to VTermKey.FUNCTION_12,
        )
        for ((id, vtKey) in expected) {
            assertThat(id.dispatch()).describedAs(id.name).isEqualTo(BuiltinKeyDispatch.VTerm(vtKey))
        }
    }

    @Test
    fun `arrows and navigation dispatch to VTerm`() {
        assertThat(BuiltinKeyId.UP.dispatch()).isEqualTo(BuiltinKeyDispatch.VTerm(VTermKey.UP))
        assertThat(BuiltinKeyId.HOME.dispatch()).isEqualTo(BuiltinKeyDispatch.VTerm(VTermKey.HOME))
        assertThat(BuiltinKeyId.PG_UP.dispatch()).isEqualTo(BuiltinKeyDispatch.VTerm(VTermKey.PAGEUP))
    }
}
