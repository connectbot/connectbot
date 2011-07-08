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
import java.util.concurrent.LinkedBlockingQueue;

import org.connectbot.bean.HostBean;
import org.connectbot.bean.PortForwardBean;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

/**
 * Wraps write(), flush() and close() to be dispatched asynchronously.
 *
 * A nicer implementation might better abstract out the I/O related APIs and
 * wrap those only.
 * @author Perry Nguyen
 */
public class TransportWrapper extends AbsTransport implements Runnable {

	private final static String TAG = "ConnectBot.TransportWrapper";
	private AbsTransport transport;
	private LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();
	private final static String CMD_CLOSE = "close";
	private final static String CMD_FLUSH = "flush";
	public TransportWrapper(AbsTransport t) {
		transport = t;
	}

	public void connect() {
		new Thread(this, TAG).start();
		transport.connect();
	}

	public int read(byte[] buffer, int offset, int length) throws IOException {
		return transport.read(buffer, offset, length);
	}

	public void write(byte[] buffer) throws IOException {
		queue.add(buffer);
	}

	public void write(int c) throws IOException {
		queue.add(c);
	}

	public void flush() throws IOException {
		queue.add(CMD_FLUSH);
	}

	public void close() {
		queue.add(CMD_CLOSE);
	}

	public void setDimensions(int columns, int rows, int width, int height) {
		transport.setDimensions(columns, rows, width, height);
	}

	public void setOptions(Map<String,String> options) {
		transport.setOptions(options);
	}

	public Map<String,String> getOptions() {
		return transport.getOptions();
	}

	public void setCompression(boolean compression) {
		transport.setCompression(compression);
	}

	public void setUseAuthAgent(String useAuthAgent) {
		transport.setUseAuthAgent(useAuthAgent);
	}

	public void setEmulation(String emulation) {
		super.setEmulation(emulation);
		transport.setEmulation(emulation);
	}

	public String getEmulation() {
		return transport.getEmulation();
	}

	public void setHost(HostBean host) {
		super.setHost(host);
		transport.setHost(host);
	}

	public void setBridge(TerminalBridge bridge) {
		super.setBridge(bridge);
		transport.setBridge(bridge);
	}

	public void setManager(TerminalManager manager) {
		super.setManager(manager);
		transport.setManager(manager);
	}

	public boolean canForwardPorts() {
		return transport.canForwardPorts();
	}

	public boolean addPortForward(PortForwardBean portForward) {
		return transport.addPortForward(portForward);
	}

	public boolean enablePortForward(PortForwardBean portForward) {
		return transport.enablePortForward(portForward);
	}

	public boolean disablePortForward(PortForwardBean portForward) {
		return transport.disablePortForward(portForward);
	}

	public boolean removePortForward(PortForwardBean portForward) {
		return transport.removePortForward(portForward);
	}

	public List<PortForwardBean> getPortForwards() {
		return transport.getPortForwards();
	}

	public boolean isConnected() { return transport.isConnected(); }
	public boolean isSessionOpen() { return transport.isSessionOpen(); }

	public int getDefaultPort() { return transport.getDefaultPort(); }

	public String getDefaultNickname(String username, String hostname, int port) {
		return transport.getDefaultNickname(username, hostname, port);
	}

	public void getSelectionArgs(Uri uri, Map<String, String> selection) {
		transport.getSelectionArgs(uri, selection);
	}

	public HostBean createHost(Uri uri) {
		return transport.createHost(uri);
	}

	public boolean usesNetwork() { return transport.usesNetwork(); }
	public void run() {
		Object o;
		try {
			while ((o = queue.take()) != null) { // should never be null...
				try {
					if (CMD_CLOSE.equals(o)) {
						transport.close();
						break; // thread exits on-close
					} else if (CMD_FLUSH.equals(o)) {
						transport.flush();
					} else if (o instanceof byte[]) {
						transport.write((byte[]) o);
					} else if (o instanceof Integer) {
						transport.write((Integer) o);
					}
				} catch (IOException e) {
					Log.e(TAG, "Unable to send deferred data", e);
					try {
						transport.flush();
					} catch (IOException ioe) {
						Log.d(TAG, "transport was closed, dispatching disconnect event");
						bridge.dispatchDisconnect(false);
						break; // thread should exit
					}
				}
			}
		} catch (InterruptedException e) {
			Log.e(TAG, "received an unexpected thread interrupt, exiting", e);
			transport.close();
			bridge.dispatchDisconnect(false);
		}
	}
}
