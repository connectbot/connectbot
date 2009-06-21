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
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import org.connectbot.transport.AbsTransport;

import android.util.Log;
import de.mud.terminal.vt320;

/**
 * @author Kenny Root
 */
public class Relay implements Runnable {
	private static final String TAG = "ConnectBot.Relay";

	private static final int BUFFER_SIZE = 4096;

	private TerminalBridge bridge;

	private Charset currentCharset;
	private CharsetDecoder decoder;

	private AbsTransport transport;

	private vt320 buffer;

	private ByteBuffer byteBuffer;
	private CharBuffer charBuffer;

	private byte[] byteArray;
	private char[] charArray;

	public Relay(TerminalBridge bridge, AbsTransport transport, vt320 buffer, String encoding) {
		setCharset(encoding);
		this.bridge = bridge;
		this.transport = transport;
		this.buffer = buffer;
	}

	public void setCharset(String encoding) {
		Log.d("ConnectBot.Relay", "changing charset to " + encoding);
		Charset charset;
		if (encoding.equals(Cp437.NAME))
			charset = new Cp437();
		else
			charset = Charset.forName(encoding);

		if (charset == currentCharset || charset == null)
			return;

		CharsetDecoder newCd = charset.newDecoder();
		newCd.onUnmappableCharacter(CodingErrorAction.REPLACE);
		newCd.onMalformedInput(CodingErrorAction.REPLACE);

		currentCharset = charset;
		synchronized (this) {
			decoder = newCd;
		}
	}

	public void run() {
		byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
		charBuffer = CharBuffer.allocate(BUFFER_SIZE);

		byteArray = byteBuffer.array();
		charArray = charBuffer.array();

		CoderResult result;

		int bytesRead = 0;
		byteBuffer.limit(0);
		int bytesToRead;
		int offset;

		try {
			while (true) {
				bytesToRead = byteBuffer.capacity() - byteBuffer.limit();
				offset = byteBuffer.arrayOffset() + byteBuffer.limit();
				bytesRead = transport.read(byteArray, offset, bytesToRead);

				if (bytesRead > 0) {
					byteBuffer.limit(byteBuffer.limit() + bytesRead);

					synchronized (this) {
						result = decoder.decode(byteBuffer, charBuffer, false);
					}

					if (result.isUnderflow() &&
							byteBuffer.limit() == byteBuffer.capacity()) {
						byteBuffer.compact();
						byteBuffer.limit(byteBuffer.position());
						byteBuffer.position(0);
					}

					buffer.putString(charArray, 0, charBuffer.position());
					charBuffer.clear();
					bridge.redraw();
				}
			}
		} catch (IOException e) {
			Log.e(TAG, "Problem while handling incoming data in relay thread", e);
		}
	}
}
