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
package org.connectbot.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalKeyListenerTest {

    private val noopDispatcher = KeyDispatcher { _, _ -> }

    // NONE: OFF -> TRANSIENT -> LOCKED -> OFF

    @Test
    fun `NONE ctrl first press goes to TRANSIENT`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        assertEquals(ModifierLevel.TRANSIENT, listener.getModifierState().ctrlState)
    }

    @Test
    fun `NONE ctrl second press goes to LOCKED`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }

    @Test
    fun `NONE ctrl third press goes to OFF`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    @Test
    fun `NONE ctrl clearTransients removes TRANSIENT but not LOCKED`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.clearTransients()
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)

        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.clearTransients()
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }

    // ALT: alt goes OFF -> LOCKED -> OFF; ctrl/shift still use 3-state

    @Test
    fun `ALT alt first press goes to LOCKED`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALT)
        listener.metaPress(TerminalKeyListener.OUR_ALT_ON)
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().altState)
    }

    @Test
    fun `ALT alt second press goes to OFF`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALT)
        listener.metaPress(TerminalKeyListener.OUR_ALT_ON)
        listener.metaPress(TerminalKeyListener.OUR_ALT_ON)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().altState)
    }

    @Test
    fun `ALT ctrl still uses 3-state`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALT)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        assertEquals(ModifierLevel.TRANSIENT, listener.getModifierState().ctrlState)
    }

    // ALL: ctrl/shift/alt all go OFF -> LOCKED -> OFF

    @Test
    fun `ALL ctrl first press goes to LOCKED`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALL)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }

    @Test
    fun `ALL ctrl second press goes to OFF`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALL)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    @Test
    fun `ALL clearTransients does not clear LOCKED state`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALL)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.clearTransients()
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }

    // sendPressedKey/sendTab/sendEscape clear TRANSIENT but preserve LOCKED

    @Test
    fun `sendPressedKey clears TRANSIENT ctrl`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.sendPressedKey(0)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    @Test
    fun `sendPressedKey preserves LOCKED ctrl`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.sendPressedKey(0)
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }

    @Test
    fun `sendTab clears TRANSIENT ctrl`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.sendTab()
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    @Test
    fun `sendTab preserves LOCKED ctrl`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.sendTab()
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }

    @Test
    fun `sendEscape clears TRANSIENT ctrl`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.sendEscape()
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    @Test
    fun `sendEscape preserves LOCKED ctrl`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON)
        listener.sendEscape()
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }

    // forceSticky jumps directly to LOCKED regardless of StickyModifierSetting

    @Test
    fun `forceSticky jumps to LOCKED from OFF when setting is NONE`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.OUR_CTRL_ON, forceSticky = true)
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }
}
