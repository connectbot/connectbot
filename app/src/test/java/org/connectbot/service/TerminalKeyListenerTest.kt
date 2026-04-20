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

    // NONE: sticky is OFF for all modifiers. metaPress only works if forceSticky=true.

    @Test
    fun `NONE ctrl first press does nothing if not forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = false)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    @Test
    fun `NONE ctrl first press goes to TRANSIENT if forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        assertEquals(ModifierLevel.TRANSIENT, listener.getModifierState().ctrlState)
    }

    @Test
    fun `NONE ctrl second press goes to LOCKED if forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }

    @Test
    fun `NONE ctrl third press goes to OFF if forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    @Test
    fun `NONE ctrl clearTransients removes TRANSIENT but not LOCKED`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        listener.clearTransients()
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)

        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        listener.clearTransients()
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }

    // ALT: alt is sticky, others are not.

    @Test
    fun `ALT alt first press goes to TRANSIENT even if not forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALT)
        listener.metaPress(TerminalKeyListener.ALT_ON, forceSticky = false)
        assertEquals(ModifierLevel.TRANSIENT, listener.getModifierState().altState)
    }

    @Test
    fun `ALT ctrl first press does nothing if not forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALT)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = false)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    // ALL: all modifiers are sticky.

    @Test
    fun `ALL ctrl first press goes to TRANSIENT even if not forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALL)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = false)
        assertEquals(ModifierLevel.TRANSIENT, listener.getModifierState().ctrlState)
    }

    // sendPressedKey/sendTab/sendEscape clear TRANSIENT but preserve LOCKED

    @Test
    fun `sendPressedKey clears TRANSIENT ctrl`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALL)
        listener.metaPress(TerminalKeyListener.CTRL_ON)
        listener.sendPressedKey(0)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    @Test
    fun `sendPressedKey preserves LOCKED ctrl`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALL)
        listener.metaPress(TerminalKeyListener.CTRL_ON)
        listener.metaPress(TerminalKeyListener.CTRL_ON)
        listener.sendPressedKey(0)
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }
}
