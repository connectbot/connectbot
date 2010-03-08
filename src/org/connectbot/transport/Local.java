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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import org.connectbot.R;
import org.connectbot.bean.HostBean;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.util.HostDatabase;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.ase.Exec;

/**
 * @author Kenny Root
 *
 */
public class Local extends AbsTransport {
	private static final String TAG = "ConnectBot.Local";
	private static final String PROTOCOL = "local";

	private static final String DEFAULT_URI = "local:#Local";

	private FileDescriptor shellFd;

	private FileInputStream is;
	private FileOutputStream os;

	/**
	 *
	 */
	public Local() {
	}

	/**
	 * @param host
	 * @param bridge
	 * @param manager
	 */
	public Local(HostBean host, TerminalBridge bridge, TerminalManager manager) {
		super(host, bridge, manager);
	}

	public static String getProtocolName() {
		return PROTOCOL;
	}

	@Override
	public void close() {
		try {
			if (os != null) {
				os.close();
				os = null;
			}
			if (is != null) {
				is.close();
				is = null;
			}
		} catch (IOException e) {
			Log.e(TAG, "Couldn't close shell", e);
		}
	}

	@Override
	public void connect() {
		int[] pids = new int[1];

		try {
			shellFd = Exec.createSubprocess("/system/bin/sh", "-", null, pids);
		} catch (Exception e) {
			bridge.outputLine(manager.res.getString(R.string.local_shell_unavailable));
			Log.e(TAG, "Cannot start local shell", e);
			return;
		}

		final int shellPid = pids[0];
		Runnable exitWatcher = new Runnable() {
			public void run() {
				Exec.waitFor(shellPid);

				bridge.dispatchDisconnect(false);
			}
		};

		Thread exitWatcherThread = new Thread(exitWatcher);
		exitWatcherThread.setName("LocalExitWatcher");
		exitWatcherThread.setDaemon(true);
		exitWatcherThread.start();

		is = new FileInputStream(shellFd);
		os = new FileOutputStream(shellFd);

		bridge.onConnected();
	}

	@Override
	public void flush() throws IOException {
		os.flush();
	}

	@Override
	public String getDefaultNickname(String username, String hostname, int port) {
		return DEFAULT_URI;
	}

	@Override
	public int getDefaultPort() {
		return 0;
	}

	@Override
	public boolean isConnected() {
		return is != null && os != null;
	}

	@Override
	public boolean isSessionOpen() {
		return is != null && os != null;
	}

	@Override
	public int read(byte[] buffer, int start, int len) throws IOException {
		if (is == null) {
			bridge.dispatchDisconnect(false);
			throw new IOException("session closed");
		}
		return is.read(buffer, start, len);
	}

	@Override
	public void setDimensions(int columns, int rows, int width, int height) {
		try {
			Exec.setPtyWindowSize(shellFd, rows, columns, width, height);
		} catch (Exception e) {
			Log.e(TAG, "Couldn't resize pty", e);
		}
	}

	@Override
	public void write(byte[] buffer) throws IOException {
		if (os != null)
			os.write(buffer);
	}

	@Override
	public void write(int c) throws IOException {
		if (os != null)
			os.write(c);
	}

	public static Uri getUri(String input) {
		Uri uri = Uri.parse(DEFAULT_URI);

		if (input != null && input.length() > 0) {
			uri = uri.buildUpon().fragment(input).build();
		}

		return uri;
	}

	@Override
	public HostBean createHost(Uri uri) {
		HostBean host = new HostBean();

		host.setProtocol(PROTOCOL);

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
	}

	public static String getFormatHint(Context context) {
		return context.getString(R.string.hostpref_nickname_title);
	}

	/* (non-Javadoc)
	 * @see org.connectbot.transport.AbsTransport#usesNetwork()
	 */
	@Override
	public boolean usesNetwork() {
		return false;
	}
}
