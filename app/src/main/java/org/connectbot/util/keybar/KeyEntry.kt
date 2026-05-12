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

/**
 * One entry in the user's on-screen key bar configuration.
 */
sealed class KeyEntry {
    /** A built-in key (modifier, control key, navigation, or function). */
    data class Builtin(
        val id: BuiltinKeyId,
        val visible: Boolean,
    ) : KeyEntry()

    /** A user-defined button that sends a byte stream (with C-style escapes). */
    data class Macro(
        val label: String,
        val text: String,
    ) : KeyEntry()
}

enum class BuiltinKeyId {
    // Sticky modifiers — render as toggle buttons with TRANSIENT/LOCKED/OFF state
    CTRL, ALT, SHIFT,
    // One-shot keys
    ESC, TAB, ENTER, BACKSPACE, DELETE, INSERT,
    UP, DOWN, LEFT, RIGHT,
    HOME, END, PG_UP, PG_DN,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
}
