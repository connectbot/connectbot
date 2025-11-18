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
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.View.OnKeyListener
import androidx.preference.PreferenceManager
import de.mud.terminal.VDUBuffer
import de.mud.terminal.vt320
import org.connectbot.bean.SelectionArea
import org.connectbot.util.PreferenceConstants
import java.io.IOException

@Suppress("DEPRECATION")
class TerminalKeyListener(
    private val manager: TerminalManager?,
    private val bridge: TerminalBridge,
    private val buffer: VDUBuffer,
    private var encoding: String?
) : OnKeyListener, OnSharedPreferenceChangeListener {

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
    private val selectionArea: SelectionArea = SelectionArea()

    private val prefs: SharedPreferences? = manager?.let { PreferenceManager.getDefaultSharedPreferences(it) }

    init {
        prefs?.registerOnSharedPreferenceChangeListener(this)

        deviceHasHardKeyboard = (manager?.res?.configuration?.keyboard
                == Configuration.KEYBOARD_QWERTY)

        updatePrefs()
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        val transport = bridge.transport

        try {
            if (bridge.isDisconnected || transport == null)
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
                        transport.write('/'.code)
                        return true
                    } else if (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT &&
                            (ourMetaState and OUR_TAB) != 0) {
                        ourMetaState = ourMetaState and OUR_TRANSIENT.inv()
                        transport.write(0x09)
                        return true
                    }
                } else if (leftModifiersAreSlashAndTab) {
                    if (keyCode == KeyEvent.KEYCODE_ALT_LEFT &&
                            (ourMetaState and OUR_SLASH) != 0) {
                        ourMetaState = ourMetaState and OUR_TRANSIENT.inv()
                        transport.write('/'.code)
                        return true
                    } else if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT &&
                            (ourMetaState and OUR_TAB) != 0) {
                        ourMetaState = ourMetaState and OUR_TRANSIENT.inv()
                        transport.write(0x09)
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

            bridge.resetScrollPosition()

            if (keyCode == KeyEvent.KEYCODE_UNKNOWN &&
                    event.action == KeyEvent.ACTION_MULTIPLE) {
                val input = encoding?.let { event.characters.toByteArray(charset(it)) }
                input?.let { transport.write(it) }
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
                    if (selectionArea.isSelectingOrigin)
                        selectionArea.finishSelectingOrigin()
                    else {
                        clipboard?.let {
                            val copiedText = selectionArea.copyFrom(buffer)
                            it.text = copiedText
                            // XXX STOPSHIP
//                            manager.notifyUser(manager.getString(
//                                    R.string.console_copy_done,
//                                    copiedText.length))
                            selectingForCopy = false
                            selectionArea.reset()
                        }
                    }
                } else {
                    if ((ourMetaState and OUR_CTRL_ON) != 0) {
                        sendEscape()
                        ourMetaState = ourMetaState and OUR_CTRL_ON.inv()
                    } else
                        metaPress(OUR_CTRL_ON, true)
                }
                bridge.redraw()
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
                ourMetaState = ourMetaState and OUR_TRANSIENT.inv()
                bridge.redraw()
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
                bridge.copyCurrentSelection()
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
                    transport.write(finalChar)
                else
                    encoding?.let {
                        transport.write(String(Character.toChars(finalChar))
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
                    transport.write(0x09)
                    return true
                }
                KeyEvent.KEYCODE_CAMERA -> {
                    val camera = manager?.prefs?.getString(
                        PreferenceConstants.CAMERA,
                        PreferenceConstants.CAMERA_CTRLA_SPACE)
                    when (camera) {
                        PreferenceConstants.CAMERA_CTRLA_SPACE -> {
                            transport.write(0x01)
                            transport.write(' '.code)
                        }
                        PreferenceConstants.CAMERA_CTRLA -> {
                            transport.write(0x01)
                        }
                        PreferenceConstants.CAMERA_ESC -> {
                            (buffer as vt320).keyTyped(vt320.KEY_ESCAPE, ' ', 0)
                        }
                        PreferenceConstants.CAMERA_ESC_A -> {
                            (buffer as vt320).keyTyped(vt320.KEY_ESCAPE, ' ', 0)
                            transport.write('a'.code)
                        }
                    }
                }
                KeyEvent.KEYCODE_DEL -> {
                    (buffer as vt320).keyPressed(vt320.KEY_BACK_SPACE, ' ',
                            stateForBuffer)
                    return true
                }
                KeyEvent.KEYCODE_ENTER -> {
                    (buffer as vt320).keyTyped(vt320.KEY_ENTER, ' ', 0)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (selectingForCopy) {
                        selectionArea.decrementColumn()
                        bridge.redraw()
                    } else {
                        (buffer as vt320).keyPressed(vt320.KEY_LEFT, ' ',
                                stateForBuffer)
                        bridge.tryKeyVibrate()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (selectingForCopy) {
                        selectionArea.decrementRow()
                        bridge.redraw()
                    } else {
                        (buffer as vt320).keyPressed(vt320.KEY_UP, ' ',
                                stateForBuffer)
                        bridge.tryKeyVibrate()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (selectingForCopy) {
                        selectionArea.incrementRow()
                        bridge.redraw()
                    } else {
                        (buffer as vt320).keyPressed(vt320.KEY_DOWN, ' ',
                                stateForBuffer)
                        bridge.tryKeyVibrate()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (selectingForCopy) {
                        selectionArea.incrementColumn()
                        bridge.redraw()
                    } else {
                        (buffer as vt320).keyPressed(vt320.KEY_RIGHT, ' ',
                                stateForBuffer)
                        bridge.tryKeyVibrate()
                    }
                    return true
                }
                KEYCODE_INSERT -> {
                    (buffer as vt320).keyPressed(vt320.KEY_INSERT, ' ',
                            stateForBuffer)
                    return true
                }
                KEYCODE_FORWARD_DEL -> {
                    (buffer as vt320).keyPressed(vt320.KEY_DELETE, ' ',
                            stateForBuffer)
                    return true
                }
                KEYCODE_MOVE_HOME -> {
                    (buffer as vt320).keyPressed(vt320.KEY_HOME, ' ',
                            stateForBuffer)
                    return true
                }
                KEYCODE_MOVE_END -> {
                    (buffer as vt320).keyPressed(vt320.KEY_END, ' ',
                            stateForBuffer)
                    return true
                }
                KEYCODE_PAGE_UP -> {
                    (buffer as vt320).keyPressed(vt320.KEY_PAGE_UP, ' ',
                            stateForBuffer)
                    return true
                }
                KEYCODE_PAGE_DOWN -> {
                    (buffer as vt320).keyPressed(vt320.KEY_PAGE_DOWN, ' ',
                            stateForBuffer)
                    return true
                }
            }

        } catch (e: IOException) {
            Log.e(TAG, "Problem while trying to handle an onKey() event", e)
            try {
                transport?.flush()
            } catch (_: IOException) {
                Log.d(TAG, "Our transport was closed, dispatching disconnect event")
                bridge.dispatchDisconnect(false)
            }
        } catch (_: NullPointerException) {
            Log.d(TAG, "Input before connection established ignored.")
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
        (buffer as vt320).keyTyped(vt320.KEY_ESCAPE, ' ', 0)
    }

    fun sendTab() {
        val transport = bridge.transport
        try {
            transport?.write(0x09)
        } catch (e: IOException) {
            Log.e(TAG, "Problem while trying to send TAB press.", e)
            try {
                transport?.flush()
            } catch (_: IOException) {
                Log.d(TAG, "Our transport was closed, dispatching disconnect event")
                bridge.dispatchDisconnect(false)
            }
        }
    }

    fun sendPressedKey(key: Int) {
        (buffer as vt320).keyPressed(key, ' ', stateForBuffer)
    }

    private fun sendFunctionKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_1 -> {
                (buffer as vt320).keyPressed(vt320.KEY_F1, ' ', 0)
                return true
            }
            KeyEvent.KEYCODE_2 -> {
                (buffer as vt320).keyPressed(vt320.KEY_F2, ' ', 0)
                return true
            }
            KeyEvent.KEYCODE_3 -> {
                (buffer as vt320).keyPressed(vt320.KEY_F3, ' ', 0)
                return true
            }
            KeyEvent.KEYCODE_4 -> {
                (buffer as vt320).keyPressed(vt320.KEY_F4, ' ', 0)
                return true
            }
            KeyEvent.KEYCODE_5 -> {
                (buffer as vt320).keyPressed(vt320.KEY_F5, ' ', 0)
                return true
            }
            KeyEvent.KEYCODE_6 -> {
                (buffer as vt320).keyPressed(vt320.KEY_F6, ' ', 0)
                return true
            }
            KeyEvent.KEYCODE_7 -> {
                (buffer as vt320).keyPressed(vt320.KEY_F7, ' ', 0)
                return true
            }
            KeyEvent.KEYCODE_8 -> {
                (buffer as vt320).keyPressed(vt320.KEY_F8, ' ', 0)
                return true
            }
            KeyEvent.KEYCODE_9 -> {
                (buffer as vt320).keyPressed(vt320.KEY_F9, ' ', 0)
                return true
            }
            KeyEvent.KEYCODE_0 -> {
                (buffer as vt320).keyPressed(vt320.KEY_F10, ' ', 0)
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
        bridge.redraw()
    }

    private val stateForBuffer: Int
        get() {
            var bufferState = 0

            if ((ourMetaState and OUR_CTRL_MASK) != 0)
                bufferState = bufferState or vt320.KEY_CONTROL
            if ((ourMetaState and OUR_SHIFT_MASK) != 0)
                bufferState = bufferState or vt320.KEY_SHIFT
            if ((ourMetaState and OUR_ALT_MASK) != 0)
                bufferState = bufferState or vt320.KEY_ALT

            return bufferState
        }

    val metaState: Int
        get() = ourMetaState

    val deadKey: Int
        get() = ourDeadKey

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
