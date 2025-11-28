/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

internal class ModifierManagerImpl: ModifierManager {
    companion object {
        // Modifier bit flags (compatible with ConnectBot's TerminalKeyListener)
        const val CTRL_ON = 0x01      // Transient Ctrl
        const val CTRL_LOCK = 0x02    // Locked Ctrl
        const val ALT_ON = 0x04       // Transient Alt
        const val ALT_LOCK = 0x08     // Locked Alt
        const val SHIFT_ON = 0x10     // Transient Shift
        const val SHIFT_LOCK = 0x20   // Locked Shift

        // Convenience masks
        private const val CTRL_MASK = CTRL_ON or CTRL_LOCK
        private const val ALT_MASK = ALT_ON or ALT_LOCK
        private const val SHIFT_MASK = SHIFT_ON or SHIFT_LOCK
        private const val TRANSIENT_MASK = CTRL_ON or ALT_ON or SHIFT_ON
    }

    /**
     * Current modifier state as a bitmask.
     * Use the modifier constants to check individual modifier states.
     */
    private var state: Int = 0

    /**
     * Configuration: which modifiers can be sticky.
     * Set via setStickyModifiers().
     */
    private var stickyModifiers: Int = 0

    private val _modifierState = MutableStateFlow(getState())
    val modifierState: StateFlow<ModifierState> = _modifierState.asStateFlow()

    /**
     * Toggle modifier state (for UI buttons).
     *
     * Implements a 3-state cycle:
     * 1. OFF → TRANSIENT (or LOCKED if forceSticky=true)
     * 2. TRANSIENT → LOCKED
     * 3. LOCKED → OFF
     *
     * @param code Modifier code (CTRL_ON, ALT_ON, or SHIFT_ON)
     * @param forceSticky If true, first press enables sticky mode directly (skips transient)
     */
    fun metaPress(code: Int, forceSticky: Boolean = false) {
        when {
            // Currently locked → turn off completely
            (state and (code shl 1)) != 0 -> {
                state = state and (code shl 1).inv()
            }
            // Currently transient → make locked
            (state and code) != 0 -> {
                state = state and code.inv()
                state = state or (code shl 1)
            }
            // Off → make transient (or locked if forceSticky/stickyModifiers allows)
            forceSticky || (stickyModifiers and code) != 0 -> {
                state = state or code
            }
            // Sticky disabled for this modifier - do nothing
            else -> return
        }
        _modifierState.value = getState()
    }

    /**
     * Clear transient modifiers after a key press.
     *
     * This should be called by KeyboardHandler after each key is dispatched
     * to the terminal. Transient modifiers are one-shot and clear automatically,
     * while locked modifiers persist.
     */
    override fun clearTransients() {
        val oldState = state
        state = state and TRANSIENT_MASK.inv()
        if (state != oldState) {
            _modifierState.value = getState()
        }
    }

    /**
     * Check if Ctrl modifier is active (transient or locked).
     */
    override fun isCtrlActive(): Boolean = (state and CTRL_MASK) != 0

    /**
     * Check if Alt modifier is active (transient or locked).
     */
    override fun isAltActive(): Boolean = (state and ALT_MASK) != 0

    /**
     * Check if Shift modifier is active (transient or locked).
     */
    override fun isShiftActive(): Boolean = (state and SHIFT_MASK) != 0

    /**
     * Check if Ctrl modifier is in transient (one-shot) state.
     */
    fun isCtrlTransient(): Boolean = (state and CTRL_ON) != 0

    /**
     * Check if Ctrl modifier is in locked (sticky) state.
     */
    fun isCtrlLocked(): Boolean = (state and CTRL_LOCK) != 0

    /**
     * Check if Alt modifier is in transient (one-shot) state.
     */
    fun isAltTransient(): Boolean = (state and ALT_ON) != 0

    /**
     * Check if Alt modifier is in locked (sticky) state.
     */
    fun isAltLocked(): Boolean = (state and ALT_LOCK) != 0

    /**
     * Check if Shift modifier is in transient (one-shot) state.
     */
    fun isShiftTransient(): Boolean = (state and SHIFT_ON) != 0

    /**
     * Check if Shift modifier is in locked (sticky) state.
     */
    fun isShiftLocked(): Boolean = (state and SHIFT_LOCK) != 0

    /**
     * Get detailed modifier state for UI display.
     *
     * Returns a ModifierState object with the current level (OFF, TRANSIENT, LOCKED)
     * for each modifier. Useful for highlighting UI buttons.
     */
    fun getState(): ModifierState {
        return ModifierState(
            ctrlState = when {
                (state and CTRL_LOCK) != 0 -> ModifierLevel.LOCKED
                (state and CTRL_ON) != 0 -> ModifierLevel.TRANSIENT
                else -> ModifierLevel.OFF
            },
            altState = when {
                (state and ALT_LOCK) != 0 -> ModifierLevel.LOCKED
                (state and ALT_ON) != 0 -> ModifierLevel.TRANSIENT
                else -> ModifierLevel.OFF
            },
            shiftState = when {
                (state and SHIFT_LOCK) != 0 -> ModifierLevel.LOCKED
                (state and SHIFT_ON) != 0 -> ModifierLevel.TRANSIENT
                else -> ModifierLevel.OFF
            }
        )
    }

    /**
     * Configure which modifiers can be sticky.
     *
     * By default, all modifiers are disabled. Call this method to enable
     * sticky behavior for specific modifiers based on user preferences.
     *
     * @param ctrl True to allow Ctrl to be sticky
     * @param alt True to allow Alt to be sticky
     * @param shift True to allow Shift to be sticky
     */
    fun setStickyModifiers(ctrl: Boolean = true, alt: Boolean = true, shift: Boolean = true) {
        stickyModifiers = 0
        if (ctrl) stickyModifiers = stickyModifiers or CTRL_ON
        if (alt) stickyModifiers = stickyModifiers or ALT_ON
        if (shift) stickyModifiers = stickyModifiers or SHIFT_ON
    }

    /**
     * Reset all modifiers to OFF state.
     *
     * This clears both transient and locked modifiers.
     */
    fun reset() {
        if (state != 0) {
            state = 0
            _modifierState.value = getState()
        }
    }
}

/**
 * Represents the detailed state of all modifiers.
 *
 * @property ctrlState Current state of Ctrl modifier
 * @property altState Current state of Alt modifier
 * @property shiftState Current state of Shift modifier
 */
internal data class ModifierState(
    val ctrlState: ModifierLevel,
    val altState: ModifierLevel,
    val shiftState: ModifierLevel
)

/**
 * Level of a modifier key.
 */
internal enum class ModifierLevel {
    OFF,        // Modifier not active
    TRANSIENT,  // One-shot modifier (clears after next key)
    LOCKED      // Sticky modifier (stays until toggled off)
}
