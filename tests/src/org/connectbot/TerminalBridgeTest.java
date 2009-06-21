/*
	ConnectBot: simple, powerful, open-source SSH client for Android
	Copyright (C) 2007-2008 Kenny Root, Jeffrey Sharkey

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.connectbot;

import java.lang.reflect.Field;

import org.connectbot.mock.NullTransport;
import org.connectbot.service.TerminalBridge;
import org.connectbot.transport.AbsTransport;
import org.connectbot.util.PreferenceConstants;

import android.test.AndroidTestCase;
import android.view.KeyEvent;

/**
 * @author Kenny Root
 *
 */
public class TerminalBridgeTest extends AndroidTestCase {
	public void testShiftLock() throws SecurityException, NoSuchFieldException,
			IllegalArgumentException, IllegalAccessException {
		TerminalBridge bridge = new TerminalBridge();
		AbsTransport nullTransport = new NullTransport();

		// Make sure onKey will work when we call it
		Field disconnected = TerminalBridge.class
				.getDeclaredField("disconnected");
		Field keymode = TerminalBridge.class.getDeclaredField("keymode");
		Field transport = TerminalBridge.class.getDeclaredField("transport");

		disconnected.setAccessible(true);
		keymode.setAccessible(true);
		transport.setAccessible(true);

		disconnected.setBoolean(bridge, false);
		keymode.set(bridge, PreferenceConstants.KEYMODE_RIGHT);
		transport.set(bridge, nullTransport);

		// Begin tests
		assertTrue("Meta state is " + bridge.getMetaState()
				+ " when it should be 0", bridge.getMetaState() == 0);

		KeyEvent shiftDown = new KeyEvent(KeyEvent.ACTION_DOWN,
				KeyEvent.KEYCODE_SHIFT_LEFT);
		bridge.onKey(null, shiftDown.getKeyCode(), shiftDown);

		assertTrue("Shift test: after shift press, meta state is "
				+ bridge.getMetaState() + " when it should be "
				+ TerminalBridge.META_SHIFT_ON,
				bridge.getMetaState() == TerminalBridge.META_SHIFT_ON);

		KeyEvent shiftUp = KeyEvent.changeAction(shiftDown, KeyEvent.ACTION_UP);
		bridge.onKey(null, shiftUp.getKeyCode(), shiftUp);

		assertTrue("Shift test: after shift release, meta state is "
				+ bridge.getMetaState() + " when it should be "
				+ TerminalBridge.META_SHIFT_ON,
				bridge.getMetaState() == TerminalBridge.META_SHIFT_ON);

		KeyEvent letterAdown = new KeyEvent(KeyEvent.ACTION_DOWN,
				KeyEvent.KEYCODE_A);
		KeyEvent letterAup = KeyEvent.changeAction(letterAdown,
				KeyEvent.ACTION_UP);

		bridge.onKey(null, letterAdown.getKeyCode(), letterAdown);
		bridge.onKey(null, letterAup.getKeyCode(), letterAup);

		assertTrue("Shift test: after letter press and release, meta state is "
				+ bridge.getMetaState() + " when it should be 0", bridge
				.getMetaState() == 0);

		bridge.onKey(null, shiftDown.getKeyCode(), shiftDown);
		bridge.onKey(null, shiftUp.getKeyCode(), shiftUp);
		bridge.onKey(null, shiftDown.getKeyCode(), shiftDown);
		bridge.onKey(null, shiftUp.getKeyCode(), shiftUp);

		assertTrue("Shift lock test: after two shift presses, meta state is "
				+ bridge.getMetaState() + " when it should be "
				+ TerminalBridge.META_SHIFT_LOCK,
				bridge.getMetaState() == TerminalBridge.META_SHIFT_LOCK);

		bridge.onKey(null, letterAdown.getKeyCode(), letterAdown);

		assertTrue(
				"Shift lock test: after letter press, meta state is "
				+ bridge.getMetaState() + " when it should be "
				+ TerminalBridge.META_SHIFT_LOCK,
				bridge.getMetaState() == TerminalBridge.META_SHIFT_LOCK);

		bridge.onKey(null, letterAup.getKeyCode(), letterAup);

		assertTrue(
				"Shift lock test: after letter press and release, meta state is "
				+ bridge.getMetaState() + " when it should be "
				+ TerminalBridge.META_SHIFT_LOCK,
				bridge.getMetaState() == TerminalBridge.META_SHIFT_LOCK);
	}
}
