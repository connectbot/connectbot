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

import org.connectbot.service.KeyDispatcher
import org.connectbot.service.StickyModifierSetting
import org.connectbot.service.TerminalKeyListener
import org.connectbot.terminal.VTermKey
import org.junit.Assert.assertEquals
import org.junit.Test

class KeySpecDispatcherTest {

    private class RecordingDispatcher : KeyDispatcher {
        val keys = mutableListOf<Pair<Int, Int>>()
        val chars = mutableListOf<Pair<Int, Char>>()

        override fun dispatchKey(modifiers: Int, key: Int) {
            keys.add(modifiers to key)
        }

        override fun dispatchCharacter(modifiers: Int, ch: Char) {
            chars.add(modifiers to ch)
        }
    }

    private fun fixture(): Triple<TerminalKeyListener, RecordingDispatcher, MutableList<String>> {
        val dispatcher = RecordingDispatcher()
        val listener = TerminalKeyListener(dispatcher, StickyModifierSetting.NONE)
        val injected = mutableListOf<String>()
        return Triple(listener, dispatcher, injected)
    }

    @Test
    fun specialEscapeAndTabUseDedicatedPaths() {
        val (listener, dispatcher, injected) = fixture()
        dispatchKeySpec(KeySpec.Special(SpecialKey.ESC), listener) { injected += it }
        dispatchKeySpec(KeySpec.Special(SpecialKey.TAB), listener) { injected += it }

        assertEquals(
            listOf(0 to VTermKey.ESCAPE, 0 to VTermKey.TAB),
            dispatcher.keys,
        )
    }

    @Test
    fun specialArrowUsesSendPressedKey() {
        val (listener, dispatcher, injected) = fixture()
        dispatchKeySpec(KeySpec.Special(SpecialKey.UP), listener) { injected += it }
        assertEquals(listOf(0 to VTermKey.UP), dispatcher.keys)
    }

    @Test
    fun textKeyInjectsParsedText() {
        val (listener, _, injected) = fixture()
        dispatchKeySpec(KeySpec.Text("cd /\\t"), listener) { injected += it }
        assertEquals(listOf("cd /\t"), injected)
    }

    @Test
    fun textKeyWithSendEnterAppendsCarriageReturn() {
        val (listener, _, injected) = fixture()
        dispatchKeySpec(KeySpec.Text("ls", sendEnter = true), listener) { injected += it }
        assertEquals(listOf("ls\r"), injected)
    }

    @Test
    fun comboCharSendsCtrlMaskedCharacter() {
        val (listener, dispatcher, injected) = fixture()
        dispatchKeySpec(KeySpec.Combo(ctrl = true, ch = 'c'), listener) { injected += it }
        // VTerm ctrl mask is 4.
        assertEquals(listOf(4 to 'c'), dispatcher.chars)
    }

    @Test
    fun comboSpecialSendsMaskedKey() {
        val (listener, dispatcher, injected) = fixture()
        dispatchKeySpec(
            KeySpec.Combo(alt = true, special = SpecialKey.LEFT),
            listener,
        ) { injected += it }
        // VTerm alt mask is 2.
        assertEquals(listOf(2 to VTermKey.LEFT), dispatcher.keys)
    }

    @Test
    fun modifierKeyTogglesTransientState() {
        val (listener, _, injected) = fixture()
        dispatchKeySpec(KeySpec.Modifier(ModifierKey.CTRL), listener) { injected += it }
        assertEquals(
            org.connectbot.service.ModifierLevel.TRANSIENT,
            listener.getModifierState().ctrlState,
        )
    }

    @Test
    fun fnGridKeyDoesNothing() {
        val (listener, dispatcher, injected) = fixture()
        dispatchKeySpec(KeySpec.FnGrid(), listener) { injected += it }
        assertEquals(0, dispatcher.keys.size)
        assertEquals(0, dispatcher.chars.size)
        assertEquals(0, injected.size)
    }

    @Test
    fun tmuxKeyInvokesSemanticAction() {
        val (listener, dispatcher, injected) = fixture()
        val actions = mutableListOf<TmuxAction>()

        dispatchKeySpec(
            spec = KeySpec.Tmux(TmuxAction.SPLIT_H),
            keyHandler = listener,
            onTmuxAction = { actions += it },
            injectText = { injected += it },
        )

        assertEquals(listOf(TmuxAction.SPLIT_H), actions)
        assertEquals(0, dispatcher.keys.size)
        assertEquals(0, injected.size)
    }
}
