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

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Configuration
import android.text.ClipboardManager
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.View.OnKeyListener
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.connectbot.terminal.ModifierManager
import org.connectbot.terminal.VTermKey
import org.connectbot.util.PreferenceConstants
import timber.log.Timber
import java.io.IOException

@Suppress("DEPRECATION")
class TerminalKeyListener(
    private val manager: TerminalManager?,
    private val bridge: TerminalBridge,
    private var encoding: String?
) : OnKeyListener, OnSharedPreferenceChangeListener, ModifierManager {

    private var keymode: String? = null
    private val deviceHasHardKeyboard: Boolean
    private var shiftedNumbersAreFKeysOnHardKeyboard: Boolean = false
    private var controlNumbersAreFKeysOnSoftKeyboard: Boolean = false
    private var volumeKeysChangeFontSize: Boolean = false
    private var stickyMetas: Int = 0

    private var ourMetaState: Int = 0

    private var ourDeadKey: Int = 0

    private var clipboard: ClipboardManager? = null

    private var selectingForCopy: Boolean = false

    private val prefs: SharedPreferences? = manager?.let { PreferenceManager.getDefaultSharedPreferences(it) }

    private val _modifierState = MutableStateFlow(getModifierState())
    val modifierState: StateFlow<ModifierState> = _modifierState.asStateFlow()

    init {
        prefs?.registerOnSharedPreferenceChangeListener(this)

        deviceHasHardKeyboard = (manager?.res?.configuration?.keyboard
                == Configuration.KEYBOARD_QWERTY)

        updatePrefs()
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        try {
            if (bridge.isDisconnected || bridge.transport == null)
                return false

            val interpretAsHardKeyboard = deviceHasHardKeyboard &&
                    manager?.hardKeyboardHidden == false
            val rightModifiersAreSlashAndTab = interpretAsHardKeyboard &&
                    PreferenceConstants.KEYMODE_RIGHT == keymode
            val leftModifiersAreSlashAndTab = interpretAsHardKeyboard &&
                    PreferenceConstants.KEYMODE_LEFT == keymode
            val shiftedNumbersAreFKeys = shiftedNumbersAreFKeysOnHardKeyboard &&
                    interpretAsHardKeyboard
            val controlNumbersAreFKeys = controlNumbersAreFKeysOnSoftKeyboard &&
                    !interpretAsHardKeyboard

            if (event.action == KeyEvent.ACTION_UP) {
                if (rightModifiersAreSlashAndTab) {
                    if (keyCode == KeyEvent.KEYCODE_ALT_RIGHT &&
                            (ourMetaState and OUR_SLASH) != 0) {
                        ourMetaState = ourMetaState and OUR_TRANSIENT.inv()
                        _modifierState.value = getModifierState()
                        bridge.writeToTransport('/'.code)
                        return true
                    } else if (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT &&
                            (ourMetaState and OUR_TAB) != 0) {
                        ourMetaState = ourMetaState and OUR_TRANSIENT.inv()
                        _modifierState.value = getModifierState()
                        bridge.writeToTransport(0x09)
                        return true
                    }
                } else if (leftModifiersAreSlashAndTab) {
                    if (keyCode == KeyEvent.KEYCODE_ALT_LEFT &&
                            (ourMetaState and OUR_SLASH) != 0) {
                        ourMetaState = ourMetaState and OUR_TRANSIENT.inv()
                        _modifierState.value = getModifierState()
                        bridge.writeToTransport('/'.code)
                        return true
                    } else if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT &&
                            (ourMetaState and OUR_TAB) != 0) {
                        ourMetaState = ourMetaState and OUR_TRANSIENT.inv()
                        _modifierState.value = getModifierState()
                        bridge.writeToTransport(0x09)
                        return true
                    }
                }

                return false
            }

            if (volumeKeysChangeFontSize) {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        bridge.increaseFontSize()
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        bridge.decreaseFontSize()
                        return true
                    }
                }
            }

            // TODO(Terminal): Do we need to reset scroll position?
//            bridge.resetScrollPosition()

            if (keyCode == KeyEvent.KEYCODE_UNKNOWN &&
                    event.action == KeyEvent.ACTION_MULTIPLE) {
                val input = encoding?.let { event.characters.toByteArray(charset(it)) }
                input?.let { bridge.writeToTransport(it) }
                return true
            }

            if (event.repeatCount == 0) {
                if (rightModifiersAreSlashAndTab) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_ALT_RIGHT -> {
                            ourMetaState = ourMetaState or OUR_SLASH
                            return true
                        }
                        KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                            ourMetaState = ourMetaState or OUR_TAB
                            return true
                        }
                        KeyEvent.KEYCODE_SHIFT_LEFT -> {
                            metaPress(OUR_SHIFT_ON)
                            return true
                        }
                        KeyEvent.KEYCODE_ALT_LEFT -> {
                            metaPress(OUR_ALT_ON)
                            return true
                        }
                    }
                } else if (leftModifiersAreSlashAndTab) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_ALT_LEFT -> {
                            ourMetaState = ourMetaState or OUR_SLASH
                            return true
                        }
                        KeyEvent.KEYCODE_SHIFT_LEFT -> {
                            ourMetaState = ourMetaState or OUR_TAB
                            return true
                        }
                        KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                            metaPress(OUR_SHIFT_ON)
                            return true
                        }
                        KeyEvent.KEYCODE_ALT_RIGHT -> {
                            metaPress(OUR_ALT_ON)
                            return true
                        }
                    }
                } else {
                    when (keyCode) {
                        KeyEvent.KEYCODE_ALT_LEFT,
                        KeyEvent.KEYCODE_ALT_RIGHT -> {
                            metaPress(OUR_ALT_ON)
                            return true
                        }
                        KeyEvent.KEYCODE_SHIFT_LEFT,
                        KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                            metaPress(OUR_SHIFT_ON)
                            return true
                        }
                    }
                }
                if (keyCode == KEYCODE_CTRL_LEFT || keyCode == KEYCODE_CTRL_RIGHT) {
                    metaPress(OUR_CTRL_ON)
                    return true
                }
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                if (selectingForCopy) {
                    // TODO(Terminal): implement selection area change
//                    if (selectionArea.isSelectingOrigin)
//                        selectionArea.finishSelectingOrigin()
//                    else {
//                        clipboard?.let {
//                            // TODO: Implement copyFrom using TerminalEmulator snapshot
//                            val copiedText = "" // selectionArea.copyFrom(emulator)
//                            it.text = copiedText
//                            selectingForCopy = false
//                            selectionArea.reset()
//                        }
//                    }
                } else {
                    if ((ourMetaState and OUR_CTRL_ON) != 0) {
                        sendEscape()
                        ourMetaState = ourMetaState and OUR_CTRL_ON.inv()
                    } else
                        metaPress(OUR_CTRL_ON, true)
                }
                return true
            }

            var derivedMetaState = event.metaState
            if ((ourMetaState and OUR_SHIFT_MASK) != 0)
                derivedMetaState = derivedMetaState or KeyEvent.META_SHIFT_ON
            if ((ourMetaState and OUR_ALT_MASK) != 0)
                derivedMetaState = derivedMetaState or KeyEvent.META_ALT_ON
            if ((ourMetaState and OUR_CTRL_MASK) != 0)
                derivedMetaState = derivedMetaState or HC_META_CTRL_ON

            if ((ourMetaState and OUR_TRANSIENT) != 0) {
                val oldState = ourMetaState
                ourMetaState = ourMetaState and OUR_TRANSIENT.inv()
                if (oldState != ourMetaState) {
                    _modifierState.value = getModifierState()
                }
            }

            if (shiftedNumbersAreFKeys && (derivedMetaState and KeyEvent.META_SHIFT_ON) != 0) {
                if (sendFunctionKey(keyCode))
                    return true
            }
            if (controlNumbersAreFKeys && (derivedMetaState and HC_META_CTRL_ON) != 0) {
                if (sendFunctionKey(keyCode))
                    return true
            }

            if (keyCode == KeyEvent.KEYCODE_C &&
                    (derivedMetaState and HC_META_CTRL_ON) != 0 &&
                    (derivedMetaState and KeyEvent.META_SHIFT_ON) != 0) {
                // TODO(Terminal): copy current selection
//                bridge.copyCurrentSelection()
                return true
            }

            if (keyCode == KeyEvent.KEYCODE_V &&
                    (derivedMetaState and HC_META_CTRL_ON) != 0 &&
                    (derivedMetaState and KeyEvent.META_SHIFT_ON) != 0 &&
                    clipboard?.hasText() == true) {
                bridge.injectString(clipboard?.text.toString())
                return true
            }

            if ((keyCode == KeyEvent.KEYCODE_EQUALS &&
                    (derivedMetaState and HC_META_CTRL_ON) != 0 &&
                    (derivedMetaState and KeyEvent.META_SHIFT_ON) != 0) ||
                    (keyCode == KeyEvent.KEYCODE_PLUS &&
                    (derivedMetaState and HC_META_CTRL_ON) != 0)) {
                bridge.increaseFontSize()
                return true
            }

            if (keyCode == KeyEvent.KEYCODE_MINUS && (derivedMetaState and HC_META_CTRL_ON) != 0) {
                bridge.decreaseFontSize()
                return true
            }

            var uchar = event.getUnicodeChar(derivedMetaState and HC_META_CTRL_MASK.inv())
            val ucharWithoutAlt = event.getUnicodeChar(
                    derivedMetaState and (HC_META_ALT_MASK or HC_META_CTRL_MASK).inv())
            if (uchar == 0) {
                uchar = ucharWithoutAlt
            } else if (uchar != ucharWithoutAlt) {
                derivedMetaState = derivedMetaState and HC_META_ALT_MASK.inv()
            }

            derivedMetaState = derivedMetaState and KeyEvent.META_SHIFT_ON.inv()

            if ((uchar and KeyCharacterMap.COMBINING_ACCENT) != 0) {
                ourDeadKey = uchar and KeyCharacterMap.COMBINING_ACCENT_MASK
                return true
            }

            if (ourDeadKey != 0) {
                uchar = KeyCharacterMap.getDeadChar(ourDeadKey, keyCode)
                ourDeadKey = 0
            }

            if (uchar >= 0x20) {
                var finalChar = uchar
                if ((derivedMetaState and HC_META_CTRL_ON) != 0)
                    finalChar = keyAsControl(finalChar)
                if ((derivedMetaState and KeyEvent.META_ALT_ON) != 0)
                    sendEscape()
                if (finalChar < 0x80)
                    bridge.writeToTransport(finalChar)
                else
                    encoding?.let {
                        bridge.writeToTransport(String(Character.toChars(finalChar))
                            .toByteArray(charset(it)))
                    }
                return true
            }

            when (keyCode) {
                KEYCODE_ESCAPE -> {
                    sendEscape()
                    return true
                }
                KeyEvent.KEYCODE_TAB -> {
                    bridge.writeToTransport(0x09)
                    return true
                }
                KeyEvent.KEYCODE_CAMERA -> {
                    val camera = manager?.prefs?.getString(
                        PreferenceConstants.CAMERA,
                        PreferenceConstants.CAMERA_CTRLA_SPACE)
                    when (camera) {
                        PreferenceConstants.CAMERA_CTRLA_SPACE -> {
                            bridge.writeToTransport(0x01)
                            bridge.writeToTransport(' '.code)
                        }
                        PreferenceConstants.CAMERA_CTRLA -> {
                            bridge.writeToTransport(0x01)
                        }
                        PreferenceConstants.CAMERA_ESC -> {
                            bridge.terminalEmulator.dispatchKey(0, VTermKey.ESCAPE)
                        }
                        PreferenceConstants.CAMERA_ESC_A -> {
                            bridge.terminalEmulator.dispatchKey(0, VTermKey.ESCAPE)
                            bridge.writeToTransport('a'.code)
                        }
                        "text_input" -> {
                            // Request floating text input dialog
                            bridge.requestOpenTextInput()
                            return true
                        }
                    }
                }
                KeyEvent.KEYCODE_DEL -> {
                    bridge.terminalEmulator.dispatchKey(modifiersForTerminal, VTermKey.BACKSPACE)
                    return true
                }
                KeyEvent.KEYCODE_ENTER -> {
                    bridge.terminalEmulator.dispatchKey(0, VTermKey.ENTER)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (selectingForCopy) {
                        // TODO(Terminal): implement selection area change
//                        selectionArea.decrementColumn()
                    } else {
                        bridge.terminalEmulator.dispatchKey(modifiersForTerminal, VTermKey.LEFT)
                        bridge.tryKeyVibrate()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (selectingForCopy) {
                        // TODO(Terminal): implement selection area change
//                        selectionArea.decrementRow()
                    } else {
                        bridge.terminalEmulator.dispatchKey(modifiersForTerminal, VTermKey.UP)
                        bridge.tryKeyVibrate()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (selectingForCopy) {
                        // TODO(Terminal): implement selection area change
//                        selectionArea.incrementRow()
                    } else {
                        bridge.terminalEmulator.dispatchKey(modifiersForTerminal, VTermKey.DOWN)
                        bridge.tryKeyVibrate()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (selectingForCopy) {
                        // TODO(Terminal): implement selection area change
//                        selectionArea.incrementColumn()
                    } else {
                        bridge.terminalEmulator.dispatchKey(modifiersForTerminal, VTermKey.RIGHT)
                        bridge.tryKeyVibrate()
                    }
                    return true
                }
                KEYCODE_INSERT -> {
                    bridge.terminalEmulator.dispatchKey(modifiersForTerminal, VTermKey.INS)
                    return true
                }
                KEYCODE_FORWARD_DEL -> {
                    bridge.terminalEmulator.dispatchKey(modifiersForTerminal, VTermKey.DEL)
                    return true
                }
                KEYCODE_MOVE_HOME -> {
                    bridge.terminalEmulator.dispatchKey(modifiersForTerminal, VTermKey.HOME)
                    return true
                }
                KEYCODE_MOVE_END -> {
                    bridge.terminalEmulator.dispatchKey(modifiersForTerminal, VTermKey.END)
                    return true
                }
                KEYCODE_PAGE_UP -> {
                    bridge.terminalEmulator.dispatchKey(modifiersForTerminal, VTermKey.PAGEUP)
                    return true
                }
                KEYCODE_PAGE_DOWN -> {
                    bridge.terminalEmulator.dispatchKey(modifiersForTerminal, VTermKey.PAGEDOWN)
                    return true
                }
            }

        } catch (e: IOException) {
            Timber.e(e, "Problem while trying to handle an onKey() event")
            bridge.dispatchDisconnect(false)
        } catch (_: NullPointerException) {
            Timber.d("Input before connection established ignored.")
            return true
        }

        return false
    }

    fun keyAsControl(key: Int): Int {
        var result = key
        when {
            key in 0x61..0x7A -> result -= 0x60
            key in 0x40..0x5F -> result -= 0x40
            key == 0x20 -> result = 0x00
            key == 0x3F -> result = 0x7F
        }
        return result
    }

    fun sendEscape() {
        bridge.terminalEmulator.dispatchKey(0, VTermKey.ESCAPE)
    }

    fun sendTab() {
        bridge.terminalEmulator.dispatchKey(0, VTermKey.TAB)
    }

    fun sendPressedKey(key: Int) {
        bridge.terminalEmulator.dispatchKey(modifiersForTerminal, key)
    }

    private fun sendFunctionKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_1 -> {
                bridge.terminalEmulator.dispatchKey(0, VTermKey.FUNCTION_1)
                return true
            }
            KeyEvent.KEYCODE_2 -> {
                bridge.terminalEmulator.dispatchKey(0, VTermKey.FUNCTION_2)
                return true
            }
            KeyEvent.KEYCODE_3 -> {
                bridge.terminalEmulator.dispatchKey(0, VTermKey.FUNCTION_3)
                return true
            }
            KeyEvent.KEYCODE_4 -> {
                bridge.terminalEmulator.dispatchKey(0, VTermKey.FUNCTION_4)
                return true
            }
            KeyEvent.KEYCODE_5 -> {
                bridge.terminalEmulator.dispatchKey(0, VTermKey.FUNCTION_5)
                return true
            }
            KeyEvent.KEYCODE_6 -> {
                bridge.terminalEmulator.dispatchKey(0, VTermKey.FUNCTION_6)
                return true
            }
            KeyEvent.KEYCODE_7 -> {
                bridge.terminalEmulator.dispatchKey(0, VTermKey.FUNCTION_7)
                return true
            }
            KeyEvent.KEYCODE_8 -> {
                bridge.terminalEmulator.dispatchKey(0, VTermKey.FUNCTION_8)
                return true
            }
            KeyEvent.KEYCODE_9 -> {
                bridge.terminalEmulator.dispatchKey(0, VTermKey.FUNCTION_9)
                return true
            }
            KeyEvent.KEYCODE_0 -> {
                bridge.terminalEmulator.dispatchKey(0, VTermKey.FUNCTION_10)
                return true
            }
            else -> return false
        }
    }

    @JvmOverloads
    fun metaPress(code: Int, forceSticky: Boolean = false) {
        if ((ourMetaState and (code shl 1)) != 0) {
            ourMetaState = ourMetaState and (code shl 1).inv()
        } else if ((ourMetaState and code) != 0) {
            ourMetaState = ourMetaState and code.inv()
            ourMetaState = ourMetaState or (code shl 1)
        } else if (forceSticky || (stickyMetas and code) != 0) {
            ourMetaState = ourMetaState or code
        } else {
            return
        }
        _modifierState.value = getModifierState()
    }

    /**
     * Build VTerm modifier mask from our meta state.
     * Bit 0: Shift
     * Bit 1: Alt
     * Bit 2: Ctrl
     */
    private val modifiersForTerminal: Int
        get() {
            var mask = 0
            if ((ourMetaState and OUR_SHIFT_MASK) != 0) mask = mask or 1
            if ((ourMetaState and OUR_ALT_MASK) != 0) mask = mask or 2
            if ((ourMetaState and OUR_CTRL_MASK) != 0) mask = mask or 4
            return mask
        }

    fun setClipboardManager(clipboard: ClipboardManager) {
        this.clipboard = clipboard
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (PreferenceConstants.KEYMODE == key ||
                PreferenceConstants.SHIFT_FKEYS == key ||
                PreferenceConstants.CTRL_FKEYS == key ||
                PreferenceConstants.VOLUME_FONT == key ||
                PreferenceConstants.STICKY_MODIFIERS == key) {
            updatePrefs()
        }
    }

    private fun updatePrefs() {
        keymode = prefs?.getString(PreferenceConstants.KEYMODE, PreferenceConstants.KEYMODE_NONE)
        shiftedNumbersAreFKeysOnHardKeyboard =
            prefs?.getBoolean(PreferenceConstants.SHIFT_FKEYS, false) == true
        controlNumbersAreFKeysOnSoftKeyboard =
            prefs?.getBoolean(PreferenceConstants.CTRL_FKEYS, false) == true
        volumeKeysChangeFontSize = prefs?.getBoolean(PreferenceConstants.VOLUME_FONT, true) == true
        val stickyModifiers = prefs?.getString(PreferenceConstants.STICKY_MODIFIERS,
                PreferenceConstants.NO)
        stickyMetas = when (stickyModifiers) {
            PreferenceConstants.ALT -> OUR_ALT_ON
            PreferenceConstants.YES -> OUR_SHIFT_ON or OUR_CTRL_ON or OUR_ALT_ON
            else -> 0
        }
    }

    fun setCharset(encoding: String) {
        this.encoding = encoding
    }

    override fun isCtrlActive(): Boolean = (ourMetaState and OUR_CTRL_MASK) != 0

    override fun isAltActive(): Boolean = (ourMetaState and OUR_ALT_MASK) != 0

    override fun isShiftActive(): Boolean = (ourMetaState and OUR_SHIFT_MASK) != 0

    override fun clearTransients() {
        val oldState = ourMetaState
        ourMetaState = ourMetaState and OUR_TRANSIENT.inv()
        if (oldState != ourMetaState) {
            _modifierState.value = getModifierState()
        }
    }

    fun getModifierState(): ModifierState {
        return ModifierState(
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
            }
        )
    }

    companion object {
        private const val TAG = "CB.OnKeyListener"

        const val OUR_CTRL_ON = 0x01
        const val OUR_CTRL_LOCK = 0x02
        const val OUR_ALT_ON = 0x04
        const val OUR_ALT_LOCK = 0x08
        const val OUR_SHIFT_ON = 0x10
        const val OUR_SHIFT_LOCK = 0x20
        private const val OUR_SLASH = 0x40
        private const val OUR_TAB = 0x80

        private const val OUR_TRANSIENT = OUR_CTRL_ON or OUR_ALT_ON or
                OUR_SHIFT_ON or OUR_SLASH or OUR_TAB

        private const val OUR_CTRL_MASK = OUR_CTRL_ON or OUR_CTRL_LOCK
        private const val OUR_ALT_MASK = OUR_ALT_ON or OUR_ALT_LOCK
        private const val OUR_SHIFT_MASK = OUR_SHIFT_ON or OUR_SHIFT_LOCK

        private const val KEYCODE_ESCAPE = 111
        private const val KEYCODE_CTRL_LEFT = 113
        private const val KEYCODE_CTRL_RIGHT = 114
        private const val KEYCODE_INSERT = 124
        private const val KEYCODE_FORWARD_DEL = 112
        private const val KEYCODE_MOVE_HOME = 122
        private const val KEYCODE_MOVE_END = 123
        private const val KEYCODE_PAGE_DOWN = 93
        private const val KEYCODE_PAGE_UP = 92
        private const val HC_META_CTRL_ON = 0x1000
        private const val HC_META_CTRL_LEFT_ON = 0x2000
        private const val HC_META_CTRL_RIGHT_ON = 0x4000
        private const val HC_META_CTRL_MASK = HC_META_CTRL_ON or HC_META_CTRL_RIGHT_ON or
                HC_META_CTRL_LEFT_ON
        private const val HC_META_ALT_MASK = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON or
                KeyEvent.META_ALT_RIGHT_ON
    }
}

data class ModifierState(
    val ctrlState: ModifierLevel,
    val altState: ModifierLevel,
    val shiftState: ModifierLevel
)

enum class ModifierLevel {
    OFF,
    TRANSIENT,
    LOCKED
}
