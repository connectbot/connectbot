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
 * Non-modifier special keys available on the terminal keys bar.
 *
 * @param repeatable Whether holding the key auto-repeats (arrow keys).
 */
enum class SpecialKey(val repeatable: Boolean = false) {
    ESC,
    TAB,
    ENTER,
    UP(repeatable = true),
    DOWN(repeatable = true),
    LEFT(repeatable = true),
    RIGHT(repeatable = true),
    HOME,
    END,
    PGUP,
    PGDN,
    INS,
    DEL,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,
}

/** Sticky modifier keys (3-state: off / transient / locked). */
enum class ModifierKey {
    CTRL,
    ALT,
    SHIFT,
}

/**
 * One key on the terminal keys bar.
 *
 * [label] and [icon] are optional user overrides for how the key is drawn;
 * when null the key renders with its default label. [icon] is an identifier
 * resolved through the curated icon catalog and wins over [label] if both
 * are set.
 */
sealed interface KeySpec {
    val label: String?
    val icon: String?

    /** A predefined key (Esc, arrows, F-keys, ...). */
    data class Special(
        val key: SpecialKey,
        override val label: String? = null,
        override val icon: String? = null,
    ) : KeySpec

    /** A sticky modifier key (Ctrl/Alt/Shift). */
    data class Modifier(
        val mod: ModifierKey,
        override val label: String? = null,
        override val icon: String? = null,
    ) : KeySpec

    /**
     * A key that sends [text] literally. Supports backslash escape sequences
     * (see [TextEscapes]); [sendEnter] appends a carriage return, turning the
     * key into a one-tap command runner.
     */
    data class Text(
        val text: String,
        val sendEnter: Boolean = false,
        override val label: String? = null,
        override val icon: String? = null,
    ) : KeySpec

    /**
     * A one-button modifier combination, e.g. Ctrl+C. Exactly one of [ch]
     * or [special] should be set as the base key.
     */
    data class Combo(
        val ctrl: Boolean = false,
        val alt: Boolean = false,
        val shift: Boolean = false,
        val ch: Char? = null,
        val special: SpecialKey? = null,
        override val label: String? = null,
        override val icon: String? = null,
    ) : KeySpec

    /** Opens the F1-F12 grid popup. */
    data class FnGrid(
        override val label: String? = null,
        override val icon: String? = null,
    ) : KeySpec

    /** Runs a semantic tmux action through control mode, never prefix keystrokes. */
    data class Tmux(
        val action: TmuxAction,
        override val label: String? = null,
        override val icon: String? = null,
    ) : KeySpec
}

/**
 * An ordered keys-bar layout: one or two rows, each an ordered list of keys.
 */
data class KeyboardLayoutSpec(val rows: List<List<KeySpec>>) {
    init {
        require(rows.isNotEmpty()) { "A keyboard layout needs at least one row" }
    }

    companion object {
        /** The keys bar renders at most this many rows. */
        const val MAX_ROWS = 2
    }
}
