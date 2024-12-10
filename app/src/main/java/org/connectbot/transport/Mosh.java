/*
 * Mosh support Copyright 2012 Daniel Drown
 *
 * Code based on ConnectBot's SSH client
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
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.regex.Matcher;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;

import org.connectbot.R;
import org.connectbot.bean.HostBean;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.util.InstallMosh;

import android.net.Uri;
import android.util.Log;

import com.trilead.ssh2.AuthAgentCallback;
import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.InteractiveCallback;
import com.trilead.ssh2.ServerHostKeyVerifier;

import org.mosh.MoshClient;

public class Mosh extends SSH implements ConnectionMonitor, InteractiveCallback, AuthAgentCallback {
	private String moshPort, moshKey, moshIP;
	private boolean sshDone = false;

	private MoshClient moshClient;

	private FileInputStream is;
	private FileOutputStream os;

	public static final String PROTOCOL = "mosh";
	private static final String TAG = "ConnectBot.MOSH";
	private static final int DEFAULT_PORT = 22;

	private boolean stoppedForBackground = false;

	public Mosh() {
		super();
	}

	/**
	 * @param bridge
	 * @param db
	 */
	public Mosh(HostBean host, TerminalBridge bridge, TerminalManager manager) {
		super(host, bridge, manager);
	}

	private static final int SIGTERM = 15;
	private static final int SIGCONT = 18;
	private static final int SIGSTOP = 19;

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
			Log.e(TAG, "Couldn't close mosh", e);
		}

		if (connected)
			super.close();

		if (moshClient != null && moshClient.processId != null) {
			synchronized (moshClient) {
				if (moshClient.processId > 0) {
					moshClient.kill(SIGCONT); // in case it's stopped
					moshClient.kill(SIGTERM);
				}
			}
		}
	}

	public void onBackground() {
		if (sshDone) {
			synchronized (moshClient) {
				if (moshClient.processId > 0)
					moshClient.kill(SIGSTOP);
				stoppedForBackground = true;
			}
		}
	}

	public void onForeground() {
		if (sshDone) {
			synchronized (moshClient) {
				if (moshClient.processId > 0)
					moshClient.kill(SIGCONT);
				stoppedForBackground = false;
			}
		}
	}

	public void onScreenOff() {
		if (sshDone) {
			synchronized (moshClient) {
				if (moshClient.processId > 0 && !stoppedForBackground)
					moshClient.kill(SIGSTOP);
			}
		}
	}

	public void onScreenOn() {
		if (sshDone) {
			synchronized (moshClient) {
				if (moshClient.processId > 0 && !stoppedForBackground)
					moshClient.kill(SIGCONT);
			}
		}
	}

	/**
	 * Internal method to request actual PTY terminal once we've finished
	 * authentication. If called before authenticated, it will just fail.
	 */
	@Override
	protected void finishConnection() {
		authenticated = true;

		try {
			bridge.outputLine("trying to run mosh-server on the remote server");
			session = connection.openSession();

			session.requestPTY(getEmulation(), columns, rows, width, height, null);
				/* TODO: try {
					session.sendEnvironment("LANG",host.getLocale());
				} catch(IOException e) {
					bridge.outputLine("ssh rejected our LANG environment variable: "+e.getMessage());
				}*/

			String serverCommand = host.getMoshServer();
			if (serverCommand == null) {
				serverCommand = "env TERM="+getEmulation();
				serverCommand += " mosh-server";
			}
			serverCommand += " new -s -c 256 -l LANG=" + host.getLocale();
			serverCommand += " -l TERM="+getEmulation();
			serverCommand += " -- sh -c 'TERM="+getEmulation()+" exec $SHELL'";
			if (host.getMoshPort() > 0) {
				serverCommand += " -p " + host.getMoshPort();
			}
			bridge.outputLine("ssh$ " + serverCommand);
			session.execCommand(serverCommand);

			stdin = session.getStdin();
			stdout = session.getStdout();
			stderr = session.getStderr();

			// means SSH session
			sessionOpen = true;

			bridge.onConnected(false);
		} catch (IOException e1) {
			Log.e(TAG, "Problem while trying to create PTY in finishConnection()", e1);
		}
	}

	// use this class to pass the actual hostname to the actual HostKeyVerifier, otherwise it gets the raw IP
	public class MoshHostKeyVerifier extends HostKeyVerifier implements ServerHostKeyVerifier {
		String realHostname;

		public MoshHostKeyVerifier(String hostname) {
			realHostname = hostname;
		}

		public boolean verifyServerHostKey(String hostname, int port,
				String serverHostKeyAlgorithm, byte[] serverHostKey) throws IOException {
			return super.verifyServerHostKey(realHostname, port, serverHostKeyAlgorithm, serverHostKey);
		}
	}

	@Override
	public void connect() {
		if (!InstallMosh.isInstallStarted()) {
			// check that InstallMosh was called by the Activity
			bridge.outputLine("curses not found");
			onDisconnect();
			return;
		}
		if (!InstallMosh.isInstallDone()) {
			bridge.outputLine("waiting for curses to be applied");
			InstallMosh.waitForInstall();
		}

		if (!InstallMosh.getTerminfoInstallStatus()) {
			bridge.outputLine("curses expression failed");
			bridge.outputLine(InstallMosh.getInstallMessages());
			onDisconnect();
			return;
		}

		bridge.outputLine(InstallMosh.getInstallMessages());

		InetAddress addresses[];
		try {
			addresses = InetAddress.getAllByName(host.getHostname());
		} catch (UnknownHostException e) {
			bridge.outputLine("Launching mosh server via SSH failed, Unknown hostname: " + host.getHostname());

			onDisconnect();
			return;
		}

		moshIP = null;
		int try_family = 4;
		for (int i = 0; i < addresses.length || try_family == 4; i++) {
			if (i == addresses.length) {
				i = 0;
				try_family = 6;
			}
			if (addresses.length == 0) {
				break;
			}
			if (try_family == 4 && addresses[i] instanceof Inet4Address) {
				moshIP = addresses[i].getHostAddress();
				break;
			}
			if (try_family == 6 && addresses[i] instanceof Inet6Address) {
				moshIP = addresses[i].getHostAddress();
				break;
			}
		}
		if (moshIP == null) {
			bridge.outputLine("No address records found for hostname: " + host.getHostname());

			onDisconnect();
			return;
		}
		bridge.outputLine("Mosh IP = " + moshIP);

		connection = new Connection(moshIP, host.getPort());
		connection.addConnectionMonitor(this);

		try {
			connection.setCompression(compression);
		} catch (IOException e) {
			Log.e(TAG, "Could not enable compression!", e);
		}

		try {
			connectionInfo = connection.connect(new MoshHostKeyVerifier(host.getHostname()));
			connected = true;

			if (connectionInfo.clientToServerCryptoAlgorithm
					.equals(connectionInfo.serverToClientCryptoAlgorithm)
					&& connectionInfo.clientToServerMACAlgorithm
					.equals(connectionInfo.serverToClientMACAlgorithm)) {
				bridge.outputLine(manager.res.getString(R.string.terminal_using_algorithm,
						connectionInfo.clientToServerCryptoAlgorithm,
						connectionInfo.clientToServerMACAlgorithm));
			} else {
				bridge.outputLine(manager.res.getString(
						R.string.terminal_using_c2s_algorithm,
						connectionInfo.clientToServerCryptoAlgorithm,
						connectionInfo.clientToServerMACAlgorithm));

				bridge.outputLine(manager.res.getString(
						R.string.terminal_using_s2c_algorithm,
						connectionInfo.serverToClientCryptoAlgorithm,
						connectionInfo.serverToClientMACAlgorithm));
			}
		} catch (IOException e) {
			Log.e(TAG, "Problem in SSH connection thread during authentication", e);

			// Display the reason in the text.
			bridge.outputLine(e.getCause().getMessage());

			onDisconnect();
			return;
		}

		try {
			// enter a loop to keep trying until authentication
			int tries = 0;
			while (connected && !connection.isAuthenticationComplete() && tries++ < AUTH_TRIES) {
				authenticate();

				// sleep to make sure we dont kill system
				Thread.sleep(1000);
			}
		} catch (Exception e) {
			Log.e(TAG, "Problem in SSH connection thread during authentication", e);
		}
	}

	public String instanceProtocolName() {
		return PROTOCOL;
	}

	public static String getProtocolName() {
		return PROTOCOL;
	}

	@Override
	public String getDefaultNickname(String username, String hostname, int port) {
		if (port == DEFAULT_PORT) {
			return String.format("mosh %s@%s", username, hostname);
		} else {
			return String.format("mosh %s@%s:%d", username, hostname, port);
		}
	}

	public static Uri getUri(String input) {
		Matcher matcher = hostmask.matcher(input);

		if (!matcher.matches())
			return null;

		StringBuilder sb = new StringBuilder();
		StringBuilder nickname = new StringBuilder();

		String username = matcher.group(1);
		String hostname = matcher.group(2);

		sb.append(getProtocolName())
				.append("://")
				.append(Uri.encode(username))
				.append('@')
				.append(hostname);
		nickname.append("mosh " + username + "@" + hostname);

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
			sb.append(':')
					.append(port);
			nickname.append(":" + port);
		}

		sb.append("/#")
				.append(Uri.encode(nickname.toString()));

		Uri uri = Uri.parse(sb.toString());

		return uri;
	}

	@Override
	public void flush() throws IOException {
		if (sshDone) {
			os.flush();
		} else {
			super.flush();
		}
	}

	@Override
	public boolean isConnected() {
		if (sshDone) {
			return is != null && os != null;
		} else {
			return super.isConnected();
		}
	}

	@Override
	public void connectionLost(Throwable reason) {
		if (!sshDone)
			onDisconnect();
	}

	@Override
	public boolean isSessionOpen() {
		if (sshDone) {
			return is != null && os != null;
		} else {
			return super.isSessionOpen();
		}
	}

	private void launchMosh() {
		MoshClient.setenv("MOSH_KEY", moshKey);
		bridge.outputLine("MOSH_KEY := " + moshKey);
		MoshClient.setenv("TERM", getEmulation());
		bridge.outputLine("TERM := " + getEmulation());
		MoshClient.setenv("TERMINFO", InstallMosh.getTerminfoPath());
		bridge.outputLine("TERMINFO := " + InstallMosh.getTerminfoPath());
		try {
			moshClient = new MoshClient(moshIP, Integer.valueOf(moshPort));
			bridge.outputLine("[" + moshClient.processId + "]: mosh-client " + moshIP + " " + moshPort);
			moshClient.setPtyWindowSize(rows, columns, width, height);
		} catch (Exception e) {
			bridge.outputLine("failed to start mosh-client: " + e.toString());
			Log.e(TAG, "Cannot start mosh-client", e);
			onDisconnect();
			return;
		} finally {
			MoshClient.setenv("MOSH_KEY", "");
		}

		Runnable exitWatcher = new Runnable() {
			public void run() {
				moshClient.waitFor();
				synchronized (moshClient) {
					moshClient = null;
				}

				bridge.dispatchDisconnect(false);
			}
		};

		Thread exitWatcherThread = new Thread(exitWatcher);
		exitWatcherThread.setName("LocalExitWatcher");
		exitWatcherThread.setDaemon(true);
		exitWatcherThread.start();

		is = new FileInputStream(moshClient.clientFd);
		os = new FileOutputStream(moshClient.clientFd);

		bridge.postLogin();
	}

	@Override
	public int read(byte[] buffer, int start, int len) throws IOException {
		if (sshDone) {
			return mosh_read(buffer, start, len);
		} else {
			return ssh_read(buffer, start, len);
		}
	}

	private int mosh_read(byte[] buffer, int start, int len) throws IOException {
		if (is == null) {
			bridge.dispatchDisconnect(false);
			throw new IOException("session closed");
		}
		return is.read(buffer, start, len);
	}

	private int ssh_read(byte[] buffer, int start, int len) throws IOException {
		int bytesRead = 0;

		if (session == null)
			return 0;

		int newConditions = session.waitForCondition(conditions, 0);

		if ((newConditions & ChannelCondition.STDOUT_DATA) != 0) {
			bytesRead = stdout.read(buffer, start, len);
			String connectTag = "MOSH CONNECT";
			String data = new String(buffer);
			int connectOffset = data.indexOf(connectTag);

			if (connectOffset > -1) {
				int connectDataOffset = connectOffset + connectTag.length() + 1;
				int end = data.indexOf(" ", connectDataOffset);
				if (end > -1) {
					moshPort = data.substring(connectDataOffset, end);
					int keyEnd = data.indexOf("\n", end + 1);
					if (keyEnd > -1) {
						moshKey = data.substring(end + 1, keyEnd - 1);
						sshDone = true;
						launchMosh();
					}
				}
			}
		}

		if ((newConditions & ChannelCondition.STDERR_DATA) != 0) {
			byte discard[] = new byte[256];
			while (stderr.available() > 0) {
				stderr.read(discard);
			}
		}

		if ((newConditions & ChannelCondition.EOF) != 0) {
			if (!sshDone) {
				onDisconnect();
				throw new IOException("Remote end closed connection");
			}
		}

		return bytesRead;
	}

	@Override
	public void setDimensions(int columns, int rows, int width, int height) {
		if (sshDone) {
			try {
				moshClient.setPtyWindowSize(rows, columns, width, height);
			} catch (Exception e) {
				Log.e(TAG, "Couldn't resize pty", e);
			}
		} else {
			super.setDimensions(columns, rows, width, height);
		}
	}

	@Override
	public void write(byte[] buffer) throws IOException {
		if (sshDone) {
			if (os != null)
				os.write(buffer);
		} else {
			super.write(buffer);
		}
	}

	@Override
	public void write(int c) throws IOException {
		if (sshDone) {
			if (os != null)
				os.write(c);
		} else {
			super.write(c);
		}
	}

	@Override
	public boolean canForwardPorts() {
		return false;
	}

	@Override
	public boolean usesNetwork() {
		return true; // don't hold wifilock
	}

	@Override
	public boolean resetOnConnectionChange() {
		return false;
	}
}
