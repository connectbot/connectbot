/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
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

package org.connectbot;

import android.test.AndroidTestCase;

/**
 * @author Kenny Root
 *
 */
public class TerminalBridgeTest extends AndroidTestCase {
	public void testShiftLock() throws SecurityException, NoSuchFieldException,
			IllegalArgumentException, IllegalAccessException {
//		TerminalBridge bridge = new TerminalBridge();
//		AbsTransport nullTransport = new NullTransport();
//
//		// Make sure onKey will work when we call it
//		Field disconnected = TerminalBridge.class
//				.getDeclaredField("disconnected");
//		Field keymode = TerminalBridge.class.getDeclaredField("keymode");
//		Field transport = TerminalBridge.class.getDeclaredField("transport");
//
//		disconnected.setAccessible(true);
//		keymode.setAccessible(true);
//		transport.setAccessible(true);
//
//		disconnected.setBoolean(bridge, false);
//		keymode.set(bridge, PreferenceConstants.KEYMODE_RIGHT);
//		transport.set(bridge, nullTransport);
//
//		// Begin tests
//		assertTrue("Meta state is " + bridge.getMetaState()
//				+ " when it should be 0", bridge.getMetaState() == 0);
//
//		KeyEvent shiftDown = new KeyEvent(KeyEvent.ACTION_DOWN,
//				KeyEvent.KEYCODE_SHIFT_LEFT);
//		bridge.onKey(null, shiftDown.getKeyCode(), shiftDown);
//
//		assertTrue("Shift test: after shift press, meta state is "
//				+ bridge.getMetaState() + " when it should be "
//				+ TerminalBridge.META_SHIFT_ON,
//				bridge.getMetaState() == TerminalBridge.META_SHIFT_ON);
//
//		KeyEvent shiftUp = KeyEvent.changeAction(shiftDown, KeyEvent.ACTION_UP);
//		bridge.onKey(null, shiftUp.getKeyCode(), shiftUp);
//
//		assertTrue("Shift test: after shift release, meta state is "
//				+ bridge.getMetaState() + " when it should be "
//				+ TerminalBridge.META_SHIFT_ON,
//				bridge.getMetaState() == TerminalBridge.META_SHIFT_ON);
//
//		KeyEvent letterAdown = new KeyEvent(KeyEvent.ACTION_DOWN,
//				KeyEvent.KEYCODE_A);
//		KeyEvent letterAup = KeyEvent.changeAction(letterAdown,
//				KeyEvent.ACTION_UP);
//
//		bridge.onKey(null, letterAdown.getKeyCode(), letterAdown);
//		bridge.onKey(null, letterAup.getKeyCode(), letterAup);
//
//		assertTrue("Shift test: after letter press and release, meta state is "
//				+ bridge.getMetaState() + " when it should be 0", bridge
//				.getMetaState() == 0);
//
//		bridge.onKey(null, shiftDown.getKeyCode(), shiftDown);
//		bridge.onKey(null, shiftUp.getKeyCode(), shiftUp);
//		bridge.onKey(null, shiftDown.getKeyCode(), shiftDown);
//		bridge.onKey(null, shiftUp.getKeyCode(), shiftUp);
//
//		assertTrue("Shift lock test: after two shift presses, meta state is "
//				+ bridge.getMetaState() + " when it should be "
//				+ TerminalBridge.META_SHIFT_LOCK,
//				bridge.getMetaState() == TerminalBridge.META_SHIFT_LOCK);
//
//		bridge.onKey(null, letterAdown.getKeyCode(), letterAdown);
//
//		assertTrue(
//				"Shift lock test: after letter press, meta state is "
//				+ bridge.getMetaState() + " when it should be "
//				+ TerminalBridge.META_SHIFT_LOCK,
//				bridge.getMetaState() == TerminalBridge.META_SHIFT_LOCK);
//
//		bridge.onKey(null, letterAup.getKeyCode(), letterAup);
//
//		assertTrue(
//				"Shift lock test: after letter press and release, meta state is "
//				+ bridge.getMetaState() + " when it should be "
//				+ TerminalBridge.META_SHIFT_LOCK,
//				bridge.getMetaState() == TerminalBridge.META_SHIFT_LOCK);
	}
}
