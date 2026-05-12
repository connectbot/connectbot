/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root, Jeffrey Sharkey
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
package org.connectbot.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.VTermKey
import org.connectbot.util.keybar.BuiltinKeyDispatch
import org.connectbot.util.keybar.BuiltinKeyId
import org.connectbot.util.keybar.dispatch

// Internal modifier bitmasks
private const val OUR_CTRL_ON = 0x01
private const val OUR_CTRL_LOCK = 0x02
private const val OUR_ALT_ON = 0x04
private const val OUR_ALT_LOCK = 0x08
private const val OUR_SHIFT_ON = 0x10
private const val OUR_SHIFT_LOCK = 0x20

private const val OUR_CTRL_MASK = OUR_CTRL_ON or OUR_CTRL_LOCK
private const val OUR_ALT_MASK = OUR_ALT_ON or OUR_ALT_LOCK
private const val OUR_SHIFT_MASK = OUR_SHIFT_ON or OUR_SHIFT_LOCK

// Terminal modifier bitmasks (per VTerm spec)
private const val VTERM_MOD_SHIFT = 1
private const val VTERM_MOD_ALT = 2
private const val VTERM_MOD_CTRL = 4

fun interface KeyDispatcher {
    fun dispatchKey(modifiers: Int, key: Int)
}

class TerminalEmulatorKeyDispatcher(private val emulator: TerminalEmulator) : KeyDispatcher {
    override fun dispatchKey(modifiers: Int, key: Int) = emulator.dispatchKey(modifiers, key)
}

enum class StickyModifierSetting(internal val mask: Int) {
    NONE(0),
    ALT(OUR_ALT_ON),
    ALL(OUR_CTRL_ON or OUR_ALT_ON or OUR_SHIFT_ON),
}

/**
 * Handles key state tracking for terminal modifiers (Ctrl, Alt, Shift).
 *
 * This class distinguishes between two types of interaction:
 * 1. **Hardware Keyboards:** Modifiers typically only enter the "sticky" cycle
 *    (TRANSIENT -> LOCKED) if the user has enabled the "Sticky Modifiers" preference.
 * 2. **Software (On-screen) Keyboards:** Because on-screen buttons cannot be "held"
 *    while pressing another key, they use the [metaPress] method with `forceSticky = true`
 *    to ensure they at least enter the TRANSIENT state, allowing for one-handed
 *    modifier use.
 */
class TerminalKeyListener(
    private val keyDispatcher: KeyDispatcher,
    stickyModifierSetting: StickyModifierSetting = StickyModifierSetting.NONE,
) : ModifierManager {

    private val stickyMetas: Int = stickyModifierSetting.mask

    private var ourMetaState: Int = 0

    private val _modifierState = MutableStateFlow(getModifierState())
    val modifierState: StateFlow<ModifierState> = _modifierState.asStateFlow()

    fun sendEscape() {
        keyDispatcher.dispatchKey(0, VTermKey.ESCAPE)
        clearTransients()
    }

    fun sendTab() {
        keyDispatcher.dispatchKey(0, VTermKey.TAB)
        clearTransients()
    }

    fun sendPressedKey(key: Int) {
        keyDispatcher.dispatchKey(modifiersForTerminal, key)
        clearTransients()
    }

    /**
     * Dispatches a built-in key bar action.
     *
     * - Modifiers go through [metaPress] with `forceSticky = true`
     *   so on-screen taps cycle the modifier into TRANSIENT state.
     * - VTerm keys go through [sendPressedKey] so the current
     *   modifier state composes (e.g. Tab built-in + sticky Ctrl
     *   sends Ctrl+Tab).
     * - Raw byte sequences are written through the provided sink
     *   (in production: `bridge::sendBytes`). Sticky modifier
     *   state is intentionally NOT applied to raw sequences —
     *   they already encode their intended semantics. Transients
     *   are cleared after the write so the user's pending Ctrl/Alt
     *   doesn't carry over to the next key event.
     *
     * @param id the built-in to fire.
     * @param byteSink callback invoked for [BuiltinKeyDispatch.ByteSequence]
     *   actions; receives the bytes to write to the SSH transport.
     */
    fun sendBuiltin(
        id: BuiltinKeyId,
        byteSink: (ByteArray) -> Unit,
    ) {
        when (val action = id.dispatch()) {
            is BuiltinKeyDispatch.Modifier ->
                metaPress(action.ourMask, forceSticky = true)

            is BuiltinKeyDispatch.VTerm ->
                sendPressedKey(action.key)

            is BuiltinKeyDispatch.ByteSequence -> {
                byteSink(action.bytes)
                clearTransients()
            }
        }
    }

    /**
     * Toggles the state of a modifier key.
     *
     * The state machine follows a 3-state toggle:
     * OFF → TRANSIENT → LOCKED → OFF
     *
     * @param code The modifier code (e.g., [OUR_CTRL_ON]).
     * @param forceSticky If true, forces the transition to at least TRANSIENT even if
     *   the modifier isn't in the [stickyMetas] list. This is primarily used by
     *   on-screen keyboards.
     */
    @JvmOverloads
    fun metaPress(code: Int, forceSticky: Boolean = false) {
        if ((ourMetaState and (code shl 1)) != 0) {
            // LOCKED → OFF
            ourMetaState = ourMetaState and (code shl 1).inv()
        } else if ((ourMetaState and code) != 0) {
            // TRANSIENT → LOCKED
            ourMetaState = ourMetaState and code.inv()
            ourMetaState = ourMetaState or (code shl 1)
        } else if (forceSticky || (stickyMetas and code) != 0) {
            // OFF → TRANSIENT
            ourMetaState = ourMetaState or code
        } else {
            return
        }
        _modifierState.value = getModifierState()
    }

    /**
     * Build VTerm modifier mask from our meta state.
     */
    private val modifiersForTerminal: Int
        get() {
            var mask = 0
            if ((ourMetaState and OUR_SHIFT_MASK) != 0) mask = mask or VTERM_MOD_SHIFT
            if ((ourMetaState and OUR_ALT_MASK) != 0) mask = mask or VTERM_MOD_ALT
            if ((ourMetaState and OUR_CTRL_MASK) != 0) mask = mask or VTERM_MOD_CTRL
            return mask
        }

    override fun isCtrlActive(): Boolean = (ourMetaState and OUR_CTRL_MASK) != 0

    override fun isAltActive(): Boolean = (ourMetaState and OUR_ALT_MASK) != 0

    override fun isShiftActive(): Boolean = (ourMetaState and OUR_SHIFT_MASK) != 0

    override fun clearTransients() {
        val oldState = ourMetaState
        ourMetaState = ourMetaState and (OUR_CTRL_ON or OUR_ALT_ON or OUR_SHIFT_ON).inv()
        if (oldState != ourMetaState) {
            _modifierState.value = getModifierState()
        }
    }

    fun getModifierState(): ModifierState = ModifierState(
        ctrlState = when {
            (ourMetaState and OUR_CTRL_LOCK) != 0 -> ModifierLevel.LOCKED
            (ourMetaState and OUR_CTRL_ON) != 0 -> ModifierLevel.TRANSIENT
            else -> ModifierLevel.OFF
        },
        altState = when {
            (ourMetaState and OUR_ALT_LOCK) != 0 -> ModifierLevel.LOCKED
            (ourMetaState and OUR_ALT_ON) != 0 -> ModifierLevel.TRANSIENT
            else -> ModifierLevel.OFF
        },
        shiftState = when {
            (ourMetaState and OUR_SHIFT_LOCK) != 0 -> ModifierLevel.LOCKED
            (ourMetaState and OUR_SHIFT_ON) != 0 -> ModifierLevel.TRANSIENT
            else -> ModifierLevel.OFF
        },
    )

    companion object {
        const val CTRL_ON = OUR_CTRL_ON
        const val ALT_ON = OUR_ALT_ON
        const val SHIFT_ON = OUR_SHIFT_ON
    }
}

data class ModifierState(
    val ctrlState: ModifierLevel,
    val altState: ModifierLevel,
    val shiftState: ModifierLevel,
)

enum class ModifierLevel {
    OFF,
    TRANSIENT,
    LOCKED,
}
