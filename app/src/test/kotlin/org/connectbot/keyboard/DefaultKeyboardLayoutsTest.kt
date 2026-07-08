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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultKeyboardLayoutsTest {
    @Test
    fun defaultLayoutIsTwoJuiceSshRows() {
        val rows = DefaultKeyboardLayouts.default.rows
        assertEquals(2, rows.size)
        assertEquals(KeySpec.Special(SpecialKey.ESC), rows[0].first())
        // The requested symbol keys appear early in row 1 (issues 1088/1744).
        assertTrue(rows[0].contains(KeySpec.Text("/")))
        assertTrue(rows[0].contains(KeySpec.Text("|")))
        assertTrue(rows[0].contains(KeySpec.Text("-")))
        assertTrue(rows[0].any { it is KeySpec.FnGrid })
        assertEquals(KeySpec.Special(SpecialKey.TAB), rows[1].first())
        assertTrue(rows[1].contains(KeySpec.Modifier(ModifierKey.CTRL)))
        assertTrue(rows[1].contains(KeySpec.Modifier(ModifierKey.ALT)))
    }

    @Test
    fun classicLayoutIsSingleRow() {
        assertEquals(1, DefaultKeyboardLayouts.classic.rows.size)
    }

    @Test
    fun byIdResolvesBuiltIns() {
        assertSame(DefaultKeyboardLayouts.default, DefaultKeyboardLayouts.byId(null))
        assertSame(DefaultKeyboardLayouts.default, DefaultKeyboardLayouts.byId(DefaultKeyboardLayouts.DEFAULT_ID))
        assertSame(DefaultKeyboardLayouts.classic, DefaultKeyboardLayouts.byId(DefaultKeyboardLayouts.CLASSIC_ID))
        assertEquals(null, DefaultKeyboardLayouts.byId(5L))
    }
}
