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

package org.connectbot.service;

import gnu.java.nio.charset.Cp437;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import android.util.Log;

import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Session;

import de.mud.terminal.vt320;

/**
 * @author Kenny Root
 */
public class Relay implements Runnable {
	private static final String TAG = "ConnectBot.Relay";

	private static final int BUFFER_SIZE = 4096;

	private static final int CONDITIONS =
		ChannelCondition.STDOUT_DATA
		| ChannelCondition.STDERR_DATA
		| ChannelCondition.CLOSED
		| ChannelCondition.EOF;

	private TerminalBridge bridge;

	private Charset currentCharset;
	private CharsetDecoder decoder;

	private Session session;

	private InputStream stdout;
	private InputStream stderr;

	private vt320 buffer;

	private ByteBuffer byteBuffer;
	private CharBuffer charBuffer;

	private byte[] byteArray;
	private char[] charArray;

	public Relay(TerminalBridge bridge, Session session, InputStream stdout, InputStream stderr, vt320 buffer, String encoding) {
		setCharset(encoding);
		this.bridge = bridge;
		this.session = session;
		this.stdout = stdout;
		this.stderr = stderr;
		this.buffer = buffer;
	}

	public void setCharset(String encoding) {
		Log.d("ConnectBot.Relay", "changing charset to " + encoding);
		Charset charset;
		if (encoding.equals(Cp437.NAME))
			charset = new Cp437();
		else
			charset = Charset.forName(encoding);

		if (charset == currentCharset)
			return;

		CharsetDecoder newCd = charset.newDecoder();
		newCd.onUnmappableCharacter(CodingErrorAction.REPLACE);
		newCd.onMalformedInput(CodingErrorAction.REPLACE);

		currentCharset = charset;
		decoder = newCd;
	}

	public void run() {
		byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
		charBuffer = CharBuffer.allocate(BUFFER_SIZE);

		byteArray = byteBuffer.array();
		charArray = charBuffer.array();

		int bytesRead = 0;

		int newConditions = 0;

		while((newConditions & ChannelCondition.CLOSED) == 0) {
			try {
				newConditions = session.waitForCondition(CONDITIONS, 0);
				if ((newConditions & ChannelCondition.STDOUT_DATA) != 0) {
					while (stdout.available() > 0) {
						bytesRead = stdout.read(byteArray, 0, BUFFER_SIZE);

						byteBuffer.position(0);
						byteBuffer.limit(bytesRead);
						decoder.decode(byteBuffer, charBuffer, false);
						buffer.putString(charArray, 0, charBuffer.position());
						charBuffer.clear();
					}

					bridge.redraw();
				}

				if ((newConditions & ChannelCondition.STDERR_DATA) != 0)
					logAndDiscard(stderr);

				if ((newConditions & ChannelCondition.EOF) != 0) {
					// The other side closed our channel, so let's disconnect.
					// TODO review whether any tunnel is in use currently.
					bridge.dispatchDisconnect(false);
					break;
				}
			} catch (IOException e) {
				Log.e(TAG, "Problem while handling incoming data in relay thread", e);
				break;
			}
		}

	}

	private void logAndDiscard(InputStream stream) throws IOException {
		int bytesAvail;
		while ((bytesAvail = stream.available()) > 0) {
			stream.skip(bytesAvail);
			Log.d(TAG, String.format("Discarded %d bytes from stderr", bytesAvail));
		}
	}
}
