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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
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

	private CharsetDecoder decoder;
	private CharsetDecoder replacer;

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
		Charset charset = Charset.forName(encoding);

		CharsetDecoder newCd = charset.newDecoder();
		newCd.onUnmappableCharacter(CodingErrorAction.REPLACE);
		newCd.onMalformedInput(CodingErrorAction.REPORT);

		CharsetDecoder newReplacer = charset.newDecoder();
		newReplacer.onUnmappableCharacter(CodingErrorAction.REPLACE);
		newReplacer.onMalformedInput(CodingErrorAction.REPLACE);

		decoder = newCd;
		replacer = newReplacer;
	}

	public void run() {
		byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
		charBuffer = CharBuffer.allocate(BUFFER_SIZE);

		byteArray = byteBuffer.array();
		charArray = charBuffer.array();

		int bytesRead = 0;
		int offset = 0;

		int newConditions = 0;

		while((newConditions & ChannelCondition.CLOSED) == 0) {
			try {
				newConditions = session.waitForCondition(CONDITIONS, 0);
				if ((newConditions & ChannelCondition.STDOUT_DATA) != 0) {
					while (stdout.available() > 0) {
						bytesRead = offset + stdout.read(byteArray, offset, BUFFER_SIZE - offset);

						byteBuffer.position(0);
						byteBuffer.limit(bytesRead);

						CoderResult coderResult = decoder.decode(byteBuffer, charBuffer, true);

						while (byteBuffer.position() < bytesRead) {
							if (coderResult.isMalformed())
								skipMalformedBytes(bytesRead, coderResult);

							coderResult = decoder.decode(byteBuffer, charBuffer, true);
						}

						if (coderResult.isMalformed())
							offset = discardMalformedBytes(bytesRead, coderResult);
						else {
							// No errors at all.
							buffer.putString(charArray, 0, charBuffer.position());
							offset = 0;
						}

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

	/**
	 * @param stream
\	 * @throws IOException
	 */
	private void logAndDiscard(InputStream stream) throws IOException {
		while (stream.available() > 0) {
			int n = stream.read(byteArray);
			byteBuffer.position(0);
			byteBuffer.limit(n);
			replacer.decode(byteBuffer, charBuffer, false);
			// TODO I don't know.. do we want this? We were ignoring it before
			Log.d(TAG, String.format("Read data from stream: %s", new String(charArray, 0, charBuffer.position())));
			charBuffer.clear();
		}
	}

	/**
	 * @param n
	 * @param cr
	 * @return
	 */
	private int discardMalformedBytes(int n, CoderResult cr) {
		int offset;
		/* If we still have malformed input, save the bytes for the next
		 * read and try to parse it again.
		 */
		offset = n - byteBuffer.position() + cr.length();
		System.arraycopy(byteArray, byteBuffer.position() - cr.length(), byteArray, 0, offset);
		Log.d(TAG, String.format("Copying out %d chars at %d: 0x%02x",
				offset, byteBuffer.position() - cr.length(),
				byteArray[byteBuffer.position() - cr.length()]
		));
		return offset;
	}

	/**
	 * @param numReadBytes
	 * @param errorResult
	 */
	private void skipMalformedBytes(int numReadBytes, CoderResult errorResult) {
		int curpos = byteBuffer.position() - errorResult.length();

		if (curpos > 0) {
			/* There is good data before the malformed section, so
			 * pass this on immediately.
			 */
			buffer.putString(charArray, 0, charBuffer.position());
		}

		byteBuffer.position(curpos);
		byteBuffer.limit(curpos + errorResult.length());

		charBuffer.clear();
		replacer.decode(byteBuffer, charBuffer, true);

		buffer.putString(charArray, 0, charBuffer.position());

		curpos += errorResult.length();

		byteBuffer.position(curpos);
		byteBuffer.limit(numReadBytes);

		charBuffer.clear();
	}
}
