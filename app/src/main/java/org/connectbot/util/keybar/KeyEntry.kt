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
