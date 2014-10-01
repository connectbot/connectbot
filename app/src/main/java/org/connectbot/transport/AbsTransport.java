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
import java.util.List;
import java.util.Map;

import org.connectbot.bean.HostBean;
import org.connectbot.bean.PortForwardBean;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;

import android.content.Context;
import android.net.Uri;

/**
 * @author Kenny Root
 *
 */
public abstract class AbsTransport {
	HostBean host;
	TerminalBridge bridge;
	TerminalManager manager;

	String emulation;

	public AbsTransport() {}

	public AbsTransport(HostBean host, TerminalBridge bridge, TerminalManager manager) {
		this.host = host;
		this.bridge = bridge;
		this.manager = manager;
	}

	/**
	 * @return protocol part of the URI
	 */
	public static String getProtocolName() {
		return "unknown";
	}

	/**
	 * Encode the current transport into a URI that can be passed via intent calls.
	 * @return URI to host
	 */
	public static Uri getUri(String input) {
		return null;
	}

	/**
	 * Causes transport to connect to the target host. After connecting but before a
	 * session is started, must call back to {@link TerminalBridge#onConnected()}.
	 * After that call a session may be opened.
	 */
	public abstract void connect();

	/**
	 * Reads from the transport. Transport must support reading into a the byte array
	 * <code>buffer</code> at the start of <code>offset</code> and a maximum of
	 * <code>length</code> bytes. If the remote host disconnects, throw an
	 * {@link IOException}.
	 * @param buffer byte buffer to store read bytes into
	 * @param offset where to start writing in the buffer
	 * @param length maximum number of bytes to read
	 * @return number of bytes read
	 * @throws IOException when remote host disconnects
	 */
	public abstract int read(byte[] buffer, int offset, int length) throws IOException;

	/**
	 * Writes to the transport. If the host is not yet connected, simply return without
	 * doing anything. An {@link IOException} should be thrown if there is an error after
	 * connection.
	 * @param buffer bytes to write to transport
	 * @throws IOException when there is a problem writing after connection
	 */
	public abstract void write(byte[] buffer) throws IOException;

	/**
	 * Writes to the transport. See {@link #write(byte[])} for behavior details.
	 * @param c character to write to the transport
	 * @throws IOException when there is a problem writing after connection
	 */
	public abstract void write(int c) throws IOException;

	/**
	 * Flushes the write commands to the transport.
	 * @throws IOException when there is a problem writing after connection
	 */
	public abstract void flush() throws IOException;

	/**
	 * Closes the connection to the terminal. Note that the resulting failure to read
	 * should call {@link TerminalBridge#dispatchDisconnect(boolean)}.
	 */
	public abstract void close();

	/**
	 * Tells the transport what dimensions the display is currently
	 * @param columns columns of text
	 * @param rows rows of text
	 * @param width width in pixels
	 * @param height height in pixels
	 */
	public abstract void setDimensions(int columns, int rows, int width, int height);

	public void setOptions(Map<String,String> options) {
		// do nothing
	}

	public Map<String,String> getOptions() {
		return null;
	}

	public void setCompression(boolean compression) {
		// do nothing
	}

	public void setUseAuthAgent(String useAuthAgent) {
		// do nothing
	}

	public void setEmulation(String emulation) {
		this.emulation = emulation;
	}

	public String getEmulation() {
		return emulation;
	}

	public void setHost(HostBean host) {
		this.host = host;
	}

	public void setBridge(TerminalBridge bridge) {
		this.bridge = bridge;
	}

	public void setManager(TerminalManager manager) {
		this.manager = manager;
	}

	/**
	 * Whether or not this transport type can forward ports.
	 * @return true on ability to forward ports
	 */
	public boolean canForwardPorts() {
		return false;
	}

	/**
	 * Adds the {@link PortForwardBean} to the list.
	 * @param portForward the port forward bean to add
	 * @return true on successful addition
	 */
	public boolean addPortForward(PortForwardBean portForward) {
		return false;
	}

	/**
	 * Enables a port forward member. After calling this method, the port forward should
	 * be operational iff it could be enabled by the transport.
	 * @param portForward member of our current port forwards list to enable
	 * @return true on successful port forward setup
	 */
	public boolean enablePortForward(PortForwardBean portForward) {
		return false;
	}

	/**
	 * Disables a port forward member. After calling this method, the port forward should
	 * be non-functioning iff it could be disabled by the transport.
	 * @param portForward member of our current port forwards list to enable
	 * @return true on successful port forward tear-down
	 */
	public boolean disablePortForward(PortForwardBean portForward) {
		return false;
	}

	/**
	 * Removes the {@link PortForwardBean} from the available port forwards.
	 * @param portForward the port forward bean to remove
	 * @return true on successful removal
	 */
	public boolean removePortForward(PortForwardBean portForward) {
		return false;
	}

	/**
	 * Gets a list of the {@link PortForwardBean} currently used by this transport.
	 * @return the list of port forwards
	 */
	public List<PortForwardBean> getPortForwards() {
		return null;
	}

	public abstract boolean isConnected();
	public abstract boolean isSessionOpen();

	/**
	 * @return int default port for protocol
	 */
	public abstract int getDefaultPort();

	/**
	 * @param username
	 * @param hostname
	 * @param port
	 * @return
	 */
	public abstract String getDefaultNickname(String username, String hostname, int port);

	/**
	 * @param uri
	 * @param selectionKeys
	 * @param selectionValues
	 */
	public abstract void getSelectionArgs(Uri uri, Map<String, String> selection);

	/**
	 * @param uri
	 * @return
	 */
	public abstract HostBean createHost(Uri uri);

	/**
	 * @param context context containing the correct resources
	 * @return string that hints at the format for connection
	 */
	public static String getFormatHint(Context context) {
		return "???";
	}

	/**
	 * @return
	 */
	public abstract boolean usesNetwork();
}
