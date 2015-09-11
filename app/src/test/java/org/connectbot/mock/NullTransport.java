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

package org.connectbot.mock;

import java.io.IOException;
import java.util.Map;

import org.connectbot.bean.HostBean;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.transport.AbsTransport;

import android.net.Uri;

/**
 * @author kenny
 *
 */
public class NullTransport extends AbsTransport {

	/**
	 *
	 */
	public NullTransport() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param host
	 * @param bridge
	 * @param manager
	 */
	public NullTransport(HostBean host, TerminalBridge bridge,
			TerminalManager manager) {
		super(host, bridge, manager);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub

	}

	@Override
	public void connect() {
		// TODO Auto-generated method stub

	}

	@Override
	public HostBean createHost(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void flush() throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getDefaultNickname(String username, String hostname, int port) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getDefaultPort() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void getSelectionArgs(Uri uri, Map<String, String> selection) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isConnected() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSessionOpen() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setDimensions(int columns, int rows, int width, int height) {
		// TODO Auto-generated method stub

	}

	@Override
	public void write(byte[] buffer) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void write(int c) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean usesNetwork() {
		// TODO Auto-generated method stub
		return false;
	}

}
