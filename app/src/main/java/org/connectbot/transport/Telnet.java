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

package org.connectbot.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.connectbot.R;
import org.connectbot.bean.HostBean;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.util.HostDatabase;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import de.mud.telnet.TelnetProtocolHandler;

/**
 * Telnet transport implementation.<br/>
 * Original idea from the JTA telnet package (de.mud.telnet)
 *
 * @author Kenny Root
 *
 */
public class Telnet extends AbsTransport {
	private static final String TAG = "ConnectBot.Telnet";
	private static final String PROTOCOL = "telnet";

	private static final int DEFAULT_PORT = 23;

	private TelnetProtocolHandler handler;
	private Socket socket;

	private InputStream is;
	private OutputStream os;
	private int width;
	private int height;

	private boolean connected = false;

	static final Pattern hostmask;
	static {
		hostmask = Pattern.compile("^([0-9a-z.-]+)(:(\\d+))?$", Pattern.CASE_INSENSITIVE);
	}

	public Telnet() {
		handler = new TelnetProtocolHandler() {
			/** get the current terminal type */
			@Override
			public String getTerminalType() {
				return getEmulation();
			}

			/** get the current window size */
			@Override
			public int[] getWindowSize() {
				return new int[] { width, height };
			}

			/** notify about local echo */
			@Override
			public void setLocalEcho(boolean echo) {
				/* EMPTY */
			}

			/** write data to our back end */
			@Override
			public void write(byte[] b) throws IOException {
				if (os != null)
					os.write(b);
			}

			/** sent on IAC EOR (prompt terminator for remote access systems). */
			@Override
			public void notifyEndOfRecord() {
			}

			@Override
			protected String getCharsetName() {
				Charset charset = bridge.getCharset();
				if (charset != null)
					return charset.name();
				else
					return "";
			}
		};
	}

	/**
	 * @param host
	 * @param bridge
	 * @param manager
	 */
	public Telnet(HostBean host, TerminalBridge bridge, TerminalManager manager) {
		super(host, bridge, manager);
	}

	public static String getProtocolName() {
		return PROTOCOL;
	}

	@Override
	public void connect() {
		try {
			socket = new Socket(host.getHostname(), host.getPort());

			connected = true;

			is = socket.getInputStream();
			os = socket.getOutputStream();

			bridge.onConnected();
		} catch (UnknownHostException e) {
			Log.d(TAG, "IO Exception connecting to host", e);
		} catch (IOException e) {
			Log.d(TAG, "IO Exception connecting to host", e);
		}
	}

	@Override
	public void close() {
		connected = false;
		if (socket != null)
			try {
				socket.close();
				socket = null;
			} catch (IOException e) {
				Log.d(TAG, "Error closing telnet socket.", e);
			}
	}

	@Override
	public void flush() throws IOException {
		os.flush();
	}

	@Override
	public int getDefaultPort() {
		return DEFAULT_PORT;
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public boolean isSessionOpen() {
		return connected;
	}

	@Override
	public int read(byte[] buffer, int start, int len) throws IOException {
		/* process all already read bytes */
		int n = 0;

		do {
			n = handler.negotiate(buffer, start);
			if (n > 0)
				return n;
		} while (n == 0);

		while (n <= 0) {
			do {
				n = handler.negotiate(buffer, start);
				if (n > 0)
					return n;
			} while (n == 0);
			n = is.read(buffer, start, len);
			if (n < 0) {
				bridge.dispatchDisconnect(false);
				throw new IOException("Remote end closed connection.");
			}

			handler.inputfeed(buffer, start, n);
			n = handler.negotiate(buffer, start);
		}
		return n;
	}

	@Override
	public void write(byte[] buffer) throws IOException {
		try {
			if (os != null)
				os.write(buffer);
		} catch (SocketException e) {
			bridge.dispatchDisconnect(false);
		}
	}

	@Override
	public void write(int c) throws IOException {
		try {
			if (os != null)
				os.write(c);
		} catch (SocketException e) {
			bridge.dispatchDisconnect(false);
		}
	}

	@Override
	public void setDimensions(int columns, int rows, int width, int height) {
		try {
			handler.setWindowSize(columns, rows);
		} catch (IOException e) {
			Log.e(TAG, "Couldn't resize remote terminal", e);
		}
	}

	@Override
	public String getDefaultNickname(String username, String hostname, int port) {
		if (port == DEFAULT_PORT) {
			return String.format("%s", hostname);
		} else {
			return String.format("%s:%d", hostname, port);
		}
	}

	public static Uri getUri(String input) {
		Matcher matcher = hostmask.matcher(input);

		if (!matcher.matches())
			return null;

		StringBuilder sb = new StringBuilder();

		sb.append(PROTOCOL)
			.append("://")
			.append(matcher.group(1));

		String portString = matcher.group(3);
		int port = DEFAULT_PORT;
		if (portString != null) {
			try {
				port = Integer.parseInt(portString);
				if (port < 1 || port > 65535) {
					port = DEFAULT_PORT;
				}
			} catch (NumberFormatException nfe) {
				// Keep the default port
			}
		}

		if (port != DEFAULT_PORT) {
			sb.append(':');
			sb.append(port);
		}

		sb.append("/#")
			.append(Uri.encode(input));

		Uri uri = Uri.parse(sb.toString());

		return uri;
	}

	@Override
	public HostBean createHost(Uri uri) {
		HostBean host = new HostBean();

		host.setProtocol(PROTOCOL);

		host.setHostname(uri.getHost());

		int port = uri.getPort();
		if (port < 0)
			port = DEFAULT_PORT;
		host.setPort(port);

		String nickname = uri.getFragment();
		if (nickname == null || nickname.length() == 0) {
			host.setNickname(getDefaultNickname(host.getUsername(),
					host.getHostname(), host.getPort()));
		} else {
			host.setNickname(uri.getFragment());
		}

		return host;
	}

	@Override
	public void getSelectionArgs(Uri uri, Map<String, String> selection) {
		selection.put(HostDatabase.FIELD_HOST_PROTOCOL, PROTOCOL);
		selection.put(HostDatabase.FIELD_HOST_NICKNAME, uri.getFragment());
		selection.put(HostDatabase.FIELD_HOST_HOSTNAME, uri.getHost());

		int port = uri.getPort();
		if (port < 0)
			port = DEFAULT_PORT;
		selection.put(HostDatabase.FIELD_HOST_PORT, Integer.toString(port));
	}

	public static String getFormatHint(Context context) {
		return String.format("%s:%s",
				context.getString(R.string.format_hostname),
				context.getString(R.string.format_port));
	}

	/* (non-Javadoc)
	 * @see org.connectbot.transport.AbsTransport#usesNetwork()
	 */
	@Override
	public boolean usesNetwork() {
		return true;
	}
}
