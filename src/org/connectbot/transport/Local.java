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

package org.connectbot.transport;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;

import org.connectbot.R;
import org.connectbot.bean.HostBean;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.util.HostDatabase;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

/**
 * @author Kenny Root
 *
 */
public class Local extends AbsTransport {
	private static final String TAG = "ConnectBot.Local";
	private static final String PROTOCOL = "local";

	private static final String DEFAULT_URI = "local:#Local";

	private static Method mExec_openSubprocess;
	private static Method mExec_waitFor;
	private static Method mExec_setPtyWindowSize;

	private FileDescriptor shellFd;

	private FileInputStream is;
	private FileOutputStream os;

	static {
		initPrivateAPI();
	}

	private static void initPrivateAPI() {
		try {
			Class<?> mExec = Class.forName("android.os.Exec");
			mExec_openSubprocess = mExec.getMethod("createSubprocess",
					String.class, String.class, String.class, int[].class);
			mExec_waitFor = mExec.getMethod("waitFor", int.class);
			mExec_setPtyWindowSize = mExec.getMethod("setPtyWindowSize",
					FileDescriptor.class, int.class, int.class, int.class, int.class);
		} catch (NoSuchMethodException e) {
			// Give up
		} catch (ClassNotFoundException e) {
			// Give up
		}
	}

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
			shellFd = (FileDescriptor) mExec_openSubprocess.invoke(null,
				"/system/bin/sh", "-", null, pids);
		} catch (Exception e) {
			bridge.outputLine(manager.res.getString(R.string.local_shell_unavailable));
			return;
		}

		final int shellPid = pids[0];
		Runnable exitWatcher = new Runnable() {
			public void run() {
				try {
					mExec_waitFor.invoke(null, shellPid);
				} catch (Exception e) {
					Log.e(TAG, "Couldn't wait for shell exit", e);
				}

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
			mExec_setPtyWindowSize.invoke(null, shellFd, rows, columns, width, height);
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
		return Uri.parse(DEFAULT_URI);
	}

	@Override
	public HostBean createHost(Uri uri) {
		HostBean host = new HostBean();

		host.setProtocol(PROTOCOL);
		host.setNickname(uri.getFragment());

		return host;
	}

	@Override
	public void getSelectionArgs(Uri uri, Map<String, String> selection) {
		selection.put(HostDatabase.FIELD_HOST_PROTOCOL, PROTOCOL);
		selection.put(HostDatabase.FIELD_HOST_NICKNAME, uri.getFragment());
	}

	public static String getFormatHint(Context context) {
		return "";
	}
}
