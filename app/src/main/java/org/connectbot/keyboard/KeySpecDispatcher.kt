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

import org.connectbot.service.TerminalKeyListener
import org.connectbot.terminal.VTermKey

/** Map a catalog key to its termlib key code. */
fun SpecialKey.toVTermKey(): Int = when (this) {
    SpecialKey.ESC -> VTermKey.ESCAPE
    SpecialKey.TAB -> VTermKey.TAB
    SpecialKey.ENTER -> VTermKey.ENTER
    SpecialKey.UP -> VTermKey.UP
    SpecialKey.DOWN -> VTermKey.DOWN
    SpecialKey.LEFT -> VTermKey.LEFT
    SpecialKey.RIGHT -> VTermKey.RIGHT
    SpecialKey.HOME -> VTermKey.HOME
    SpecialKey.END -> VTermKey.END
    SpecialKey.PGUP -> VTermKey.PAGEUP
    SpecialKey.PGDN -> VTermKey.PAGEDOWN
    SpecialKey.INS -> VTermKey.INS
    SpecialKey.DEL -> VTermKey.DEL
    SpecialKey.F1 -> VTermKey.FUNCTION_1
    SpecialKey.F2 -> VTermKey.FUNCTION_2
    SpecialKey.F3 -> VTermKey.FUNCTION_3
    SpecialKey.F4 -> VTermKey.FUNCTION_4
    SpecialKey.F5 -> VTermKey.FUNCTION_5
    SpecialKey.F6 -> VTermKey.FUNCTION_6
    SpecialKey.F7 -> VTermKey.FUNCTION_7
    SpecialKey.F8 -> VTermKey.FUNCTION_8
    SpecialKey.F9 -> VTermKey.FUNCTION_9
    SpecialKey.F10 -> VTermKey.FUNCTION_10
    SpecialKey.F11 -> VTermKey.FUNCTION_11
    SpecialKey.F12 -> VTermKey.FUNCTION_12
}

/**
 * Send one keys-bar key to the terminal.
 *
 * @param keyHandler modifier-aware key path for special/modifier/combo keys;
 *   must belong to the terminal that currently has focus (host shell or
 *   tmux pane).
 * @param injectText text path for [KeySpec.Text] keys; callers pass the
 *   pane-aware paste/inject function so text lands in the right channel.
 */
fun dispatchKeySpec(
    spec: KeySpec,
    keyHandler: TerminalKeyListener,
    onTmuxAction: ((TmuxAction) -> Unit)? = null,
    injectText: (String) -> Unit,
) {
    when (spec) {
        is KeySpec.Special -> when (spec.key) {
            SpecialKey.ESC -> keyHandler.sendEscape()
            SpecialKey.TAB -> keyHandler.sendTab()
            else -> keyHandler.sendPressedKey(spec.key.toVTermKey())
        }

        is KeySpec.Modifier -> keyHandler.metaPress(
            when (spec.mod) {
                ModifierKey.CTRL -> TerminalKeyListener.CTRL_ON
                ModifierKey.ALT -> TerminalKeyListener.ALT_ON
                ModifierKey.SHIFT -> TerminalKeyListener.SHIFT_ON
            },
            true,
        )

        is KeySpec.Text -> {
            val text = TextEscapes.parse(spec.text) + if (spec.sendEnter) "\r" else ""
            if (text.isNotEmpty()) {
                injectText(text)
            }
        }

        is KeySpec.Combo -> {
            val special = spec.special
            val ch = spec.ch
            when {
                special != null ->
                    keyHandler.sendCombo(spec.ctrl, spec.alt, spec.shift, special.toVTermKey())

                ch != null ->
                    keyHandler.sendComboChar(spec.ctrl, spec.alt, spec.shift, ch)
            }
        }

        // Opens a popup; the keys inside it dispatch as Special keys.
        is KeySpec.FnGrid -> Unit

        is KeySpec.Tmux -> onTmuxAction?.invoke(spec.action)
    }
}
