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

import org.connectbot.terminal.VTermKey

/**
 * Maps a [BuiltinKeyId] to the action that should fire when the
 * user taps the corresponding button on the bar.
 *
 * Three kinds of actions:
 *   - [Modifier]:     toggle sticky modifier state in TerminalKeyListener.
 *   - [VTerm]:        dispatch a VTerm key (modifier state composes via
 *                     `sendPressedKey` -> `modifiersForTerminal`).
 *   - [ByteSequence]: enqueue raw bytes (used as fallback when termlib
 *                     doesn't have a constant for the key; modifier
 *                     state is intentionally *not* composed here, since
 *                     these sequences already encode the desired
 *                     semantics).
 *
 * VTermKey constants known to exist in termlib:0.0.39: DOWN, END,
 * ENTER, ESCAPE, FUNCTION_0..12, HOME, LEFT, PAGEDOWN, PAGEUP,
 * RIGHT, TAB, UP. BACKSPACE/DELETE/INSERT are not present and fall
 * through to xterm CSI byte sequences below.
 */
sealed class BuiltinKeyDispatch {
    data class Modifier(val ourMask: Int) : BuiltinKeyDispatch()
    data class VTerm(val key: Int) : BuiltinKeyDispatch()
    data class ByteSequence(val bytes: ByteArray) : BuiltinKeyDispatch() {
        override fun equals(other: Any?): Boolean =
            other is ByteSequence && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

// Mirror the private constants from TerminalKeyListener.kt so this
// helper stays decoupled (no companion-object import path required).
private const val CTRL_ON = 0x01
private const val ALT_ON = 0x04
private const val SHIFT_ON = 0x10

private val ESC_LBR_3_TILDE = byteArrayOf(0x1B, '['.code.toByte(), '3'.code.toByte(), '~'.code.toByte())
private val ESC_LBR_2_TILDE = byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '~'.code.toByte())

fun BuiltinKeyId.dispatch(): BuiltinKeyDispatch = when (this) {
    BuiltinKeyId.CTRL  -> BuiltinKeyDispatch.Modifier(CTRL_ON)
    BuiltinKeyId.ALT   -> BuiltinKeyDispatch.Modifier(ALT_ON)
    BuiltinKeyId.SHIFT -> BuiltinKeyDispatch.Modifier(SHIFT_ON)

    BuiltinKeyId.ESC   -> BuiltinKeyDispatch.VTerm(VTermKey.ESCAPE)
    BuiltinKeyId.TAB   -> BuiltinKeyDispatch.VTerm(VTermKey.TAB)

    // Enter: a literal CR matches what the IME path already sends
    // and avoids surprising terminal apps that watch for 0x0D.
    BuiltinKeyId.ENTER -> BuiltinKeyDispatch.ByteSequence(byteArrayOf(0x0D))

    BuiltinKeyId.UP    -> BuiltinKeyDispatch.VTerm(VTermKey.UP)
    BuiltinKeyId.DOWN  -> BuiltinKeyDispatch.VTerm(VTermKey.DOWN)
    BuiltinKeyId.LEFT  -> BuiltinKeyDispatch.VTerm(VTermKey.LEFT)
    BuiltinKeyId.RIGHT -> BuiltinKeyDispatch.VTerm(VTermKey.RIGHT)
    BuiltinKeyId.HOME  -> BuiltinKeyDispatch.VTerm(VTermKey.HOME)
    BuiltinKeyId.END   -> BuiltinKeyDispatch.VTerm(VTermKey.END)
    BuiltinKeyId.PG_UP -> BuiltinKeyDispatch.VTerm(VTermKey.PAGEUP)
    BuiltinKeyId.PG_DN -> BuiltinKeyDispatch.VTerm(VTermKey.PAGEDOWN)

    // termlib 0.0.39 doesn't expose constants for these — fall back
    // to standard xterm sequences which every terminal app understands.
    BuiltinKeyId.BACKSPACE -> BuiltinKeyDispatch.ByteSequence(byteArrayOf(0x7F))
    BuiltinKeyId.DELETE    -> BuiltinKeyDispatch.ByteSequence(ESC_LBR_3_TILDE)
    BuiltinKeyId.INSERT    -> BuiltinKeyDispatch.ByteSequence(ESC_LBR_2_TILDE)

    BuiltinKeyId.F1  -> BuiltinKeyDispatch.VTerm(VTermKey.FUNCTION_1)
    BuiltinKeyId.F2  -> BuiltinKeyDispatch.VTerm(VTermKey.FUNCTION_2)
    BuiltinKeyId.F3  -> BuiltinKeyDispatch.VTerm(VTermKey.FUNCTION_3)
    BuiltinKeyId.F4  -> BuiltinKeyDispatch.VTerm(VTermKey.FUNCTION_4)
    BuiltinKeyId.F5  -> BuiltinKeyDispatch.VTerm(VTermKey.FUNCTION_5)
    BuiltinKeyId.F6  -> BuiltinKeyDispatch.VTerm(VTermKey.FUNCTION_6)
    BuiltinKeyId.F7  -> BuiltinKeyDispatch.VTerm(VTermKey.FUNCTION_7)
    BuiltinKeyId.F8  -> BuiltinKeyDispatch.VTerm(VTermKey.FUNCTION_8)
    BuiltinKeyId.F9  -> BuiltinKeyDispatch.VTerm(VTermKey.FUNCTION_9)
    BuiltinKeyId.F10 -> BuiltinKeyDispatch.VTerm(VTermKey.FUNCTION_10)
    BuiltinKeyId.F11 -> BuiltinKeyDispatch.VTerm(VTermKey.FUNCTION_11)
    BuiltinKeyId.F12 -> BuiltinKeyDispatch.VTerm(VTermKey.FUNCTION_12)
}
