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

/**
 * Built-in keys-bar layouts. Like built-in color schemes, these are virtual
 * (negative IDs, never stored in the database) so they can evolve in code.
 */
object DefaultKeyboardLayouts {
    /** Virtual ID of the built-in two-row default (JuiceSSH-style) layout. */
    const val DEFAULT_ID = -1L

    /** Virtual ID of the built-in single-row classic ConnectBot layout. */
    const val CLASSIC_ID = -2L

    /**
     * Two-row default modeled on JuiceSSH, putting `/`, `|` and `-` within
     * one tap (upstream issues 1088/1744).
     */
    val default = KeyboardLayoutSpec(
        rows = listOf(
            listOf(
                KeySpec.Special(SpecialKey.ESC),
                KeySpec.Text("/"),
                KeySpec.Text("|"),
                KeySpec.Text("-"),
                KeySpec.Special(SpecialKey.HOME),
                KeySpec.Special(SpecialKey.UP),
                KeySpec.Special(SpecialKey.END),
                KeySpec.Special(SpecialKey.PGUP),
                KeySpec.FnGrid(),
            ),
            listOf(
                KeySpec.Special(SpecialKey.TAB),
                KeySpec.Modifier(ModifierKey.CTRL),
                KeySpec.Modifier(ModifierKey.ALT),
                KeySpec.Special(SpecialKey.LEFT),
                KeySpec.Special(SpecialKey.DOWN),
                KeySpec.Special(SpecialKey.RIGHT),
                KeySpec.Special(SpecialKey.PGDN),
            ),
        ),
    )

    /** The pre-customization ConnectBot bar: a single scrollable row. */
    val classic = KeyboardLayoutSpec(
        rows = listOf(
            listOf(
                KeySpec.Modifier(ModifierKey.CTRL),
                KeySpec.Special(SpecialKey.ESC),
                KeySpec.Special(SpecialKey.TAB),
                KeySpec.Special(SpecialKey.UP),
                KeySpec.Special(SpecialKey.DOWN),
                KeySpec.Special(SpecialKey.LEFT),
                KeySpec.Special(SpecialKey.RIGHT),
                KeySpec.Special(SpecialKey.HOME),
                KeySpec.Special(SpecialKey.END),
                KeySpec.Special(SpecialKey.PGUP),
                KeySpec.Special(SpecialKey.PGDN),
                KeySpec.Special(SpecialKey.F1),
                KeySpec.Special(SpecialKey.F2),
                KeySpec.Special(SpecialKey.F3),
                KeySpec.Special(SpecialKey.F4),
                KeySpec.Special(SpecialKey.F5),
                KeySpec.Special(SpecialKey.F6),
                KeySpec.Special(SpecialKey.F7),
                KeySpec.Special(SpecialKey.F8),
                KeySpec.Special(SpecialKey.F9),
                KeySpec.Special(SpecialKey.F10),
                KeySpec.Special(SpecialKey.F11),
                KeySpec.Special(SpecialKey.F12),
            ),
        ),
    )

    /** Resolve a built-in layout by its virtual ID, or null for custom IDs. */
    fun byId(id: Long?): KeyboardLayoutSpec? = when (id) {
        null, DEFAULT_ID -> default
        CLASSIC_ID -> classic
        else -> null
    }
}
