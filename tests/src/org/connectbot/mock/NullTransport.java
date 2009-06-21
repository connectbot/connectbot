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

}
