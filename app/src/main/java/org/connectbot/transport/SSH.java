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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.trilead.ssh2.crypto.keys.Ed25519PrivateKey;
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey;
import com.trilead.ssh2.crypto.keys.Ed25519Provider;
import org.connectbot.R;
import org.connectbot.data.entity.Host;
import org.connectbot.data.entity.KeyStorageType;
import org.connectbot.data.entity.PortForward;
import org.connectbot.data.entity.Pubkey;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalBridgePromptsKt;
import org.connectbot.service.TerminalManager;
import org.connectbot.service.TerminalManager.KeyHolder;
import org.connectbot.util.HostConstants;
import org.connectbot.util.PubkeyUtils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.trilead.ssh2.AuthAgentCallback;
import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionInfo;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.DynamicPortForwarder;
import com.trilead.ssh2.ExtendedServerHostKeyVerifier;
import com.trilead.ssh2.InteractiveCallback;
import com.trilead.ssh2.KnownHosts;
import com.trilead.ssh2.LocalPortForwarder;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.crypto.PEMDecoder;
import com.trilead.ssh2.signature.DSASHA1Verify;
import com.trilead.ssh2.signature.ECDSASHA2Verify;
import com.trilead.ssh2.signature.Ed25519Verify;
import com.trilead.ssh2.signature.RSASHA1Verify;

/**
 * @author Kenny Root
 *
 */
public class SSH extends AbsTransport implements ConnectionMonitor, InteractiveCallback, AuthAgentCallback {
	static {
		// Since this class deals with Ed25519 keys, we need to make sure this is available.
		Ed25519Provider.insertIfNeeded();
	}

	public SSH() {
		super();
	}

	public SSH(Host host, TerminalBridge bridge, TerminalManager manager) {
		super(host, bridge, manager);
	}

	private static final String PROTOCOL = "ssh";
	private static final String TAG = "CB.SSH";
	private static final int DEFAULT_PORT = 22;

	private static final String AUTH_PUBLICKEY = "publickey",
		AUTH_PASSWORD = "password",
		AUTH_KEYBOARDINTERACTIVE = "keyboard-interactive";

	private final static int AUTH_TRIES = 20;

	private static final Pattern hostmask = Pattern.compile(
			"^(.+)@((?:[0-9a-z._-]+)|(?:\\[[a-f:0-9]+(?:%[-_.a-z0-9]+)?\\]))(?::(\\d+))?$", Pattern.CASE_INSENSITIVE);

	private boolean compression = false;
	private volatile boolean authenticated = false;
	private volatile boolean connected = false;
	private volatile boolean sessionOpen = false;

	private boolean pubkeysExhausted = false;
	private boolean interactiveCanContinue = true;

	private Connection connection;
	private Session session;

	private OutputStream stdin;
	private InputStream stdout;
	private InputStream stderr;

	private static final int conditions = ChannelCondition.STDOUT_DATA
		| ChannelCondition.STDERR_DATA
		| ChannelCondition.CLOSED
		| ChannelCondition.EOF;

	private final List<PortForward> portForwards = new ArrayList<>();

	private int columns;
	private int rows;

	private int width;
	private int height;

	private String useAuthAgent = HostConstants.AUTHAGENT_NO;
	private String agentLockPassphrase;

	public class HostKeyVerifier extends ExtendedServerHostKeyVerifier {
		@Override
		public boolean verifyServerHostKey(String hostname, int port,
				String serverHostKeyAlgorithm, byte[] serverHostKey) throws IOException {

			// read in all known hosts from hostdb
			KnownHosts hosts = manager.hostRepository.getKnownHostsBlocking();
			Boolean result;

			String matchName = String.format(Locale.US, "%s:%d", hostname, port);

			String fingerprint = KnownHosts.createHexFingerprint(serverHostKeyAlgorithm, serverHostKey);

			String algorithmName;
			if ("ssh-rsa".equals(serverHostKeyAlgorithm))
				algorithmName = "RSA";
			else if ("ssh-dss".equals(serverHostKeyAlgorithm))
				algorithmName = "DSA";
			else if (serverHostKeyAlgorithm.startsWith("ecdsa-"))
				algorithmName = "EC";
			else if ("ssh-ed25519".equals(serverHostKeyAlgorithm))
				algorithmName = "Ed25519";
			else
				algorithmName = serverHostKeyAlgorithm;

			switch (hosts.verifyHostkey(matchName, serverHostKeyAlgorithm, serverHostKey)) {
			case KnownHosts.HOSTKEY_IS_OK:
				bridge.outputLine(manager.res.getString(R.string.terminal_sucess, algorithmName, fingerprint));
				return true;

			case KnownHosts.HOSTKEY_IS_NEW:
				// prompt user
				bridge.outputLine(manager.res.getString(R.string.host_authenticity_warning, hostname));
				bridge.outputLine(manager.res.getString(R.string.host_fingerprint, algorithmName, fingerprint));

				result = TerminalBridgePromptsKt.requestBooleanPrompt(bridge, null, manager.res.getString(R.string.prompt_continue_connecting));
				if (result == null) {
					return false;
				}
				if (result) {
					// save this key in known database
					manager.hostRepository.saveKnownHostBlocking(host, hostname, port, serverHostKeyAlgorithm, serverHostKey);
				}
				return result;

			case KnownHosts.HOSTKEY_HAS_CHANGED:
				String header = String.format("@   %s   @",
						manager.res.getString(R.string.host_verification_failure_warning_header));

				char[] atsigns = new char[header.length()];
				Arrays.fill(atsigns, '@');
				String border = new String(atsigns);

				bridge.outputLine(border);
				bridge.outputLine(header);
				bridge.outputLine(border);

				bridge.outputLine(manager.res.getString(R.string.host_verification_failure_warning));

				bridge.outputLine(String.format(manager.res.getString(R.string.host_fingerprint),
						algorithmName, fingerprint));

				// Users have no way to delete keys, so we'll prompt them for now.
				result = TerminalBridgePromptsKt.requestBooleanPrompt(bridge, null, manager.res.getString(R.string.prompt_continue_connecting));
				if (result != null && result) {
					// save this key in known database
					manager.hostRepository.saveKnownHostBlocking(host, hostname, port, serverHostKeyAlgorithm, serverHostKey);
					return true;
				} else {
					return false;
				}

			default:
				bridge.outputLine(manager.res.getString(R.string.terminal_failed));
				return false;
			}
		}

		@Override
		public List<String> getKnownKeyAlgorithmsForHost(String host, int port) {
			return manager.hostRepository.getHostKeyAlgorithmsForHostBlocking(host, port);
		}

		@Override
		public void removeServerHostKey(String host, int port, String algorithm, byte[] hostKey) {
			manager.hostRepository.removeKnownHostBlocking(host, port, algorithm, hostKey);
		}

		@Override
		public void addServerHostKey(String hostname, int port, String algorithm, byte[] hostKey) {
			manager.hostRepository.saveKnownHostBlocking(host, hostname, port, algorithm, hostKey);
		}
	}

	private void authenticate() {
		try {
			if (connection.authenticateWithNone(host.getUsername())) {
				finishConnection();
				return;
			}
		} catch (Exception e) {
			Log.d(TAG, "Host does not support 'none' authentication.");
		}

		bridge.outputLine(manager.res.getString(R.string.terminal_auth));

		try {
			long pubkeyId = host.getPubkeyId();

			if (!pubkeysExhausted &&
					pubkeyId != HostConstants.PUBKEYID_NEVER &&
					connection.isAuthMethodAvailable(host.getUsername(), AUTH_PUBLICKEY)) {

				// if explicit pubkey defined for this host, then prompt for password as needed
				// otherwise just try all in-memory keys held in terminalmanager

				if (pubkeyId == HostConstants.PUBKEYID_ANY) {
					// try each of the in-memory keys
					bridge.outputLine(manager.res
							.getString(R.string.terminal_auth_pubkey_any));
					for (Entry<String, KeyHolder> entry : manager.loadedKeypairs.entrySet()) {
						if (entry.getValue().pubkey.getConfirmation()
								&& !promptForPubkeyUse(entry.getKey()))
							continue;

						if (this.tryPublicKey(host.getUsername(), entry.getKey(),
								entry.getValue().pair)) {
							finishConnection();
							break;
						}
					}
				} else {
					bridge.outputLine(manager.res.getString(R.string.terminal_auth_pubkey_specific));
					// use a specific key for this host, as requested
					Pubkey pubkey = manager.pubkeyRepository.getByIdBlocking(pubkeyId);

					if (pubkey == null)
						bridge.outputLine(manager.res.getString(R.string.terminal_auth_pubkey_invalid));
					else
						if (tryPublicKey(pubkey))
							finishConnection();
				}

				pubkeysExhausted = true;
			} else if (interactiveCanContinue &&
					connection.isAuthMethodAvailable(host.getUsername(), AUTH_KEYBOARDINTERACTIVE)) {
				// this auth method will talk with us using InteractiveCallback interface
				// it blocks until authentication finishes
				bridge.outputLine(manager.res.getString(R.string.terminal_auth_ki));
				interactiveCanContinue = false;
				if (connection.authenticateWithKeyboardInteractive(host.getUsername(), this)) {
					finishConnection();
				} else {
					bridge.outputLine(manager.res.getString(R.string.terminal_auth_ki_fail));
				}
			} else if (connection.isAuthMethodAvailable(host.getUsername(), AUTH_PASSWORD)) {
				bridge.outputLine(manager.res.getString(R.string.terminal_auth_pass));
				String password = TerminalBridgePromptsKt.requestStringPrompt(bridge, null,
						manager.res.getString(R.string.prompt_password), true);
				if (password != null
						&& connection.authenticateWithPassword(host.getUsername(), password)) {
					finishConnection();
				} else {
					bridge.outputLine(manager.res.getString(R.string.terminal_auth_pass_fail));
				}
			} else {
				bridge.outputLine(manager.res.getString(R.string.terminal_auth_fail));
			}
		} catch (IllegalStateException e) {
			Log.e(TAG, "Connection went away while we were trying to authenticate", e);
		} catch (Exception e) {
			Log.e(TAG, "Problem during handleAuthentication()", e);
		}
	}

	/**
	 * Attempt connection with given {@code pubkey}.
	 * @return {@code true} for successful authentication
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeySpecException
	 * @throws IOException
	 */
	private boolean tryPublicKey(Pubkey pubkey) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
		KeyPair pair = null;

		if (manager.isKeyLoaded(pubkey.getNickname())) {
			// load this key from memory if its already there
			Log.d(TAG, String.format("Found unlocked key '%s' already in-memory", pubkey.getNickname()));

			if (pubkey.getConfirmation()) {
				if (!promptForPubkeyUse(pubkey.getNickname()))
					return false;
			}

			pair = manager.getKey(pubkey.getNickname());
		} else {
			// otherwise load key from database and prompt for password as needed
			String password = null;
			if (pubkey.getEncrypted()) {
				password = TerminalBridgePromptsKt.requestStringPrompt(bridge, null,
						manager.res.getString(R.string.prompt_pubkey_password, pubkey.getNickname()), true);

				// Something must have interrupted the prompt.
				if (password == null)
					return false;
			}

			if ("IMPORTED".equals(pubkey.getType())) {
				// load specific key using pem format
				pair = PEMDecoder.decode(new String(pubkey.getPrivateKey(), StandardCharsets.UTF_8).toCharArray(), password);
			} else {
				// load using internal generated format
				PrivateKey privKey;
				try {
					privKey = PubkeyUtils.decodePrivate(pubkey.getPrivateKey(),
							pubkey.getType(), password);
				} catch (Exception e) {
					String message = String.format("Bad password for key '%s'. Authentication failed.", pubkey.getNickname());
					Log.e(TAG, message, e);
					bridge.outputLine(message);
					return false;
				}

				PublicKey pubKey = PubkeyUtils.decodePublic(pubkey.getPublicKey(), pubkey.getType());

				// convert key to trilead format
				pair = new KeyPair(pubKey, privKey);
				Log.d(TAG, "Unlocked key " + PubkeyUtils.formatKey(pubKey));
			}

			Log.d(TAG, String.format("Unlocked key '%s'", pubkey.getNickname()));

			// save this key in memory
			manager.addKey(pubkey, pair);
		}

		return tryPublicKey(host.getUsername(), pubkey.getNickname(), pair);
	}

	private boolean tryPublicKey(String username, String keyNickname, KeyPair pair) throws IOException {
		//bridge.outputLine(String.format("Attempting 'publickey' with key '%s' [%s]...", keyNickname, trileadKey.toString()));
		boolean success = connection.authenticateWithPublicKey(username, pair);
		if (!success)
			bridge.outputLine(manager.res.getString(R.string.terminal_auth_pubkey_fail, keyNickname));
		return success;
	}

	/**
	 * Internal method to request actual PTY terminal once we've finished
	 * authentication. If called before authenticated, it will just fail.
	 */
	private void finishConnection() {
		authenticated = true;

		for (PortForward portForward : portForwards) {
			try {
				enablePortForward(portForward);
				bridge.outputLine(manager.res.getString(R.string.terminal_enable_portfoward, portForward.getDescription()));
			} catch (Exception e) {
				Log.e(TAG, "Error setting up port forward during connect", e);
			}
		}

		if (!host.getWantSession()) {
			bridge.outputLine(manager.res.getString(R.string.terminal_no_session));
			bridge.onConnected();
			return;
		}

		try {
			session = connection.openSession();

			if (useAuthAgent != null && !useAuthAgent.equals(HostConstants.AUTHAGENT_NO))
				session.requestAuthAgentForwarding(this);

			session.requestPTY(getEmulation(), columns, rows, width, height, null);
			session.startShell();

			stdin = session.getStdin();
			stdout = session.getStdout();
			stderr = session.getStderr();

			sessionOpen = true;

			bridge.onConnected();
		} catch (IOException e1) {
			Log.e(TAG, "Problem while trying to create PTY in finishConnection()", e1);
		}

	}

	@Override
	public void connect() {
		connection = new Connection(host.getHostname(), host.getPort());
		connection.addConnectionMonitor(this);

		try {
			connection.setCompression(compression);
		} catch (IOException e) {
			Log.e(TAG, "Could not enable compression!", e);
		}

		try {
			/* Uncomment when debugging SSH protocol:
			DebugLogger logger = new DebugLogger() {

				public void log(int level, String className, String message) {
					Log.d("SSH", message);
				}

			};
			Logger.enabled = true;
			Logger.logger = logger;
			*/
			ConnectionInfo connectionInfo = connection.connect(new HostKeyVerifier());
			String c2sMac = Objects.requireNonNullElse(connectionInfo.clientToServerMACAlgorithm, "");
			String s2cMac = Objects.requireNonNullElse(connectionInfo.serverToClientMACAlgorithm, "");

			connected = true;

			bridge.outputLine(manager.res.getString(R.string.terminal_kex_algorithm,
					connectionInfo.keyExchangeAlgorithm));

			if (connectionInfo.clientToServerCryptoAlgorithm
					.equals(connectionInfo.serverToClientCryptoAlgorithm)
					&& c2sMac.equals(s2cMac)) {
				bridge.outputLine(manager.res.getString(R.string.terminal_using_algorithm,
						connectionInfo.clientToServerCryptoAlgorithm,
						c2sMac));
			} else {
				bridge.outputLine(manager.res.getString(
						R.string.terminal_using_c2s_algorithm,
						connectionInfo.clientToServerCryptoAlgorithm,
						c2sMac));

				bridge.outputLine(manager.res.getString(
						R.string.terminal_using_s2c_algorithm,
						connectionInfo.serverToClientCryptoAlgorithm,
						s2cMac));
			}
		} catch (IOException e) {
			Log.e(TAG, "Problem in SSH connection thread during authentication", e);

			// Display the reason in the text.
			Throwable t = e;
			while (t != null) {
				String message = t.getMessage();
				if (message != null) {
					bridge.outputLine(message);
					if (t instanceof NoRouteToHostException)
						bridge.outputLine(manager.res.getString(R.string.terminal_no_route));
				}
				t = t.getCause();
			}

			close();
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

	@Override
	public void close() {
		connected = false;

		if (session != null) {
			session.close();
			session = null;
		}

		if (connection != null) {
			connection.close();
			connection = null;
		}
	}

	private void onDisconnect() {
		bridge.dispatchDisconnect(false);
	}

	@Override
	public void flush() throws IOException {
		if (stdin != null)
			stdin.flush();
	}

	@Override
	public int read(byte[] buffer, int start, int len) throws IOException {
		int bytesRead = 0;

		if (session == null)
			return 0;

		int newConditions = session.waitForCondition(conditions, 0);

		if ((newConditions & ChannelCondition.STDOUT_DATA) != 0) {
			bytesRead = stdout.read(buffer, start, len);
		}

		if ((newConditions & ChannelCondition.STDERR_DATA) != 0) {
			byte[] discard = new byte[256];
			while (stderr.available() > 0) {
				stderr.read(discard);
			}
		}

		if ((newConditions & ChannelCondition.EOF) != 0) {
			close();
			onDisconnect();
			throw new IOException("Remote end closed connection");
		}

		return bytesRead;
	}

	@Override
	public void write(byte[] buffer) throws IOException {
		if (stdin != null)
			stdin.write(buffer);
	}

	@Override
	public void write(int c) throws IOException {
		if (stdin != null)
			stdin.write(c);
	}

	@Override
	public Map<String, String> getOptions() {
		Map<String, String> options = new HashMap<>();

		options.put("compression", Boolean.toString(compression));

		return options;
	}

	@Override
	public void setOptions(Map<String, String> options) {
		if (options.containsKey("compression"))
			compression = Boolean.parseBoolean(options.get("compression"));
	}

	public static String getProtocolName() {
		return PROTOCOL;
	}

	@Override
	public boolean isSessionOpen() {
		return sessionOpen;
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public void connectionLost(Throwable reason) {
		onDisconnect();
	}

	@Override
	public boolean canForwardPorts() {
		return true;
	}

	@Override
	public List<PortForward> getPortForwards() {
		return portForwards;
	}

	@Override
	public boolean addPortForward(PortForward portForward) {
		return portForwards.add(portForward);
	}

	@Override
	public boolean removePortForward(PortForward portForward) {
		// Make sure we don't have a phantom forwarder.
		disablePortForward(portForward);

		return portForwards.remove(portForward);
	}

	@Override
	public boolean enablePortForward(PortForward portForward) {
		if (!portForwards.contains(portForward)) {
			Log.e(TAG, "Attempt to enable port forward not in list");
			return false;
		}

		if (!authenticated)
			return false;

		if (HostConstants.PORTFORWARD_LOCAL.equals(portForward.getType())) {
			LocalPortForwarder lpf = null;
			try {
				lpf = connection.createLocalPortForwarder(
						new InetSocketAddress(InetAddress.getLocalHost(), portForward.getSourcePort()),
						portForward.getDestAddr(), portForward.getDestPort());
			} catch (Exception e) {
				Log.e(TAG, "Could not create local port forward", e);
				return false;
			}

			if (lpf == null) {
				Log.e(TAG, "returned LocalPortForwarder object is null");
				return false;
			}

			portForward.setIdentifier(lpf);
			portForward.setEnabled(true);
			return true;
		} else if (HostConstants.PORTFORWARD_REMOTE.equals(portForward.getType())) {
			try {
				connection.requestRemotePortForwarding("", portForward.getSourcePort(), portForward.getDestAddr(), portForward.getDestPort());
			} catch (Exception e) {
				Log.e(TAG, "Could not create remote port forward", e);
				return false;
			}

			portForward.setEnabled(true);
			return true;
		} else if (HostConstants.PORTFORWARD_DYNAMIC5.equals(portForward.getType())) {
			DynamicPortForwarder dpf = null;

			try {
				dpf = connection.createDynamicPortForwarder(
						new InetSocketAddress(InetAddress.getLocalHost(), portForward.getSourcePort()));
			} catch (Exception e) {
				Log.e(TAG, "Could not create dynamic port forward", e);
				return false;
			}

			portForward.setIdentifier(dpf);
			portForward.setEnabled(true);
			return true;
		} else {
			// Unsupported type
			Log.e(TAG, String.format("attempt to forward unknown type %s", portForward.getType()));
			return false;
		}
	}

	@Override
	public boolean disablePortForward(PortForward portForward) {
		if (!portForwards.contains(portForward)) {
			Log.e(TAG, "Attempt to disable port forward not in list");
			return false;
		}

		if (!authenticated)
			return false;

		if (HostConstants.PORTFORWARD_LOCAL.equals(portForward.getType())) {
			LocalPortForwarder lpf = null;
			lpf = (LocalPortForwarder) portForward.getIdentifier();

			if (!portForward.isEnabled() || lpf == null) {
				Log.d(TAG, String.format("Could not disable %s; it appears to be not enabled or have no handler", portForward.getNickname()));
				return false;
			}

			portForward.setEnabled(false);

			lpf.close();

			return true;
		} else if (HostConstants.PORTFORWARD_REMOTE.equals(portForward.getType())) {
			portForward.setEnabled(false);

			try {
				connection.cancelRemotePortForwarding(portForward.getSourcePort());
			} catch (IOException e) {
				Log.e(TAG, "Could not stop remote port forwarding, setting enabled to false", e);
				return false;
			}

			return true;
		} else if (HostConstants.PORTFORWARD_DYNAMIC5.equals(portForward.getType())) {
			DynamicPortForwarder dpf;
			dpf = (DynamicPortForwarder) portForward.getIdentifier();

			if (!portForward.isEnabled() || dpf == null) {
				Log.d(TAG, String.format("Could not disable %s; it appears to be not enabled or have no handler", portForward.getNickname()));
				return false;
			}

			portForward.setEnabled(false);

			dpf.close();

			return true;
		} else {
			// Unsupported type
			Log.e(TAG, String.format("attempt to forward unknown type %s", portForward.getType()));
			return false;
		}
	}

	@Override
	public void setDimensions(int columns, int rows, int width, int height) {
		this.columns = columns;
		this.rows = rows;

		if (sessionOpen) {
			try {
				session.resizePTY(columns, rows, width, height);
			} catch (IOException e) {
				Log.e(TAG, "Couldn't send resize PTY packet", e);
			}
		}
	}

	@Override
	public int getDefaultPort() {
		return DEFAULT_PORT;
	}

	@Override
	public String getDefaultNickname(String username, String hostname, int port) {
		if (port == DEFAULT_PORT) {
			return String.format(Locale.US, "%s@%s", username, hostname);
		} else {
			return String.format(Locale.US, "%s@%s:%d", username, hostname, port);
		}
	}

	public static Uri getUri(String input) {
		Matcher matcher = hostmask.matcher(input);

		if (!matcher.matches())
			return null;

		StringBuilder sb = new StringBuilder();

		sb.append(PROTOCOL)
			.append("://")
			.append(Uri.encode(matcher.group(1)))
			.append('@')
			.append(Uri.encode(matcher.group(2)));

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
		}

		sb.append("/#")
			.append(Uri.encode(input));

		return Uri.parse(sb.toString());
	}

	/**
	 * Handle challenges from keyboard-interactive authentication mode.
	 */
	@Override
	public String[] replyToChallenge(String name, String instruction, int numPrompts, String[] prompt, boolean[] echo) {
		interactiveCanContinue = true;
		String[] responses = new String[numPrompts];
		for (int i = 0; i < numPrompts; i++) {
			// request response from user for each prompt
			boolean isPassword = echo != null && i < echo.length && !echo[i];
			responses[i] = TerminalBridgePromptsKt.requestStringPrompt(bridge, instruction, prompt[i], isPassword);
		}
		return responses;
	}

	@Override
	public Host createHost(Uri uri) {
		String hostname = uri.getHost();
		String username = uri.getUserInfo();
		int port = uri.getPort();
		if (port < 0)
			port = DEFAULT_PORT;
		String nickname = getDefaultNickname(username, hostname, port);

		return Host.createSshHost(
				nickname,
				hostname != null ? hostname : "",
				port,
				username != null ? username : "");
	}

	@Override
	public void getSelectionArgs(Uri uri, Map<String, String> selection) {
		selection.put(HostConstants.FIELD_HOST_PROTOCOL, PROTOCOL);
		selection.put(HostConstants.FIELD_HOST_NICKNAME, uri.getFragment());
		selection.put(HostConstants.FIELD_HOST_HOSTNAME, uri.getHost());

		int port = uri.getPort();
		if (port < 0)
			port = DEFAULT_PORT;
		selection.put(HostConstants.FIELD_HOST_PORT, Integer.toString(port));
		selection.put(HostConstants.FIELD_HOST_USERNAME, uri.getUserInfo());
	}

	@Override
	public void setCompression(boolean compression) {
		this.compression = compression;
	}

	public static String getFormatHint(Context context) {
		return String.format("%s@%s:%s",
				context.getString(R.string.format_username),
				context.getString(R.string.format_hostname),
				context.getString(R.string.format_port));
	}

	@Override
	public void setUseAuthAgent(String useAuthAgent) {
		this.useAuthAgent = useAuthAgent;
	}

	@Override
	public Map<String, byte[]> retrieveIdentities() {
		Map<String, byte[]> pubKeys = new HashMap<>(manager.loadedKeypairs.size());

		for (Entry<String, KeyHolder> entry : manager.loadedKeypairs.entrySet()) {
			KeyPair pair = entry.getValue().pair;

			try {
				PrivateKey privKey = pair.getPrivate();
				if (privKey instanceof RSAPrivateKey) {
					RSAPublicKey pubkey = (RSAPublicKey) pair.getPublic();
					pubKeys.put(entry.getKey(), RSASHA1Verify.get().encodePublicKey(pubkey));
				} else if (privKey instanceof DSAPrivateKey) {
					DSAPublicKey pubkey = (DSAPublicKey) pair.getPublic();
					pubKeys.put(entry.getKey(), DSASHA1Verify.get().encodePublicKey(pubkey));
				} else if (privKey instanceof ECPrivateKey) {
					ECPublicKey pubkey = (ECPublicKey) pair.getPublic();
					pubKeys.put(entry.getKey(), ECDSASHA2Verify.getVerifierForKey(pubkey).encodePublicKey(pubkey));
				} else if (privKey instanceof Ed25519PrivateKey) {
					Ed25519PublicKey pubkey = (Ed25519PublicKey) pair.getPublic();
					pubKeys.put(entry.getKey(), Ed25519Verify.get().encodePublicKey(pubkey));
				}
			} catch (IOException ignored) {
			}
		}

		return pubKeys;
	}

	@Override
	public KeyPair getKeyPair(byte[] publicKey) {
		String nickname = manager.getKeyNickname(publicKey);

		if (nickname == null)
			return null;

		if (useAuthAgent == null || useAuthAgent.equals(HostConstants.AUTHAGENT_NO)) {
			Log.e(TAG, "");
			return null;
		}
		if (useAuthAgent.equals(HostConstants.AUTHAGENT_CONFIRM)) {
			KeyHolder holder = manager.loadedKeypairs.get(nickname);
			if (holder != null && holder.pubkey.getConfirmation() && !promptForPubkeyUse(nickname))
				return null;
		}
		return manager.getKey(nickname);
	}

	private boolean promptForPubkeyUse(String nickname) {
		Boolean result = TerminalBridgePromptsKt.requestBooleanPrompt(bridge, null,
				manager.res.getString(R.string.prompt_allow_agent_to_use_key, nickname));
		return result != null && result;
	}

	@Override
	public boolean addIdentity(KeyPair pair, String comment, boolean confirmUse, int lifetime) {
		// Create a temporary pubkey for in-memory storage (not persisted to database)
		// Note: lifetime functionality is not yet implemented in Pubkey entity
		Pubkey pubkey = new Pubkey(
			0L, // temporary, not saved to database
			comment, // nickname
			"IMPORTED", // type
			null, // privateKey - not needed for agent forwarding
			pair.getPublic().getEncoded(), // publicKey
			false, // encrypted
			false, // startup
			confirmUse, // confirmation
			System.currentTimeMillis(), // createdDate
			KeyStorageType.EXPORTABLE, // storageType
			true, // allowBackup
			null // keystoreAlias
		);
		manager.addKey(pubkey, pair);
		return true;
	}

	@Override
	public boolean removeAllIdentities() {
		manager.loadedKeypairs.clear();
		return true;
	}

	@Override
	public boolean removeIdentity(byte[] publicKey) {
		return manager.removeKey(publicKey);
	}

	@Override
	public boolean isAgentLocked() {
		return agentLockPassphrase != null;
	}

	@Override
	public boolean requestAgentUnlock(String unlockPassphrase) {
		if (agentLockPassphrase == null)
			return false;

		if (agentLockPassphrase.equals(unlockPassphrase))
			agentLockPassphrase = null;

		return agentLockPassphrase == null;
	}

	@Override
	public boolean setAgentLock(String lockPassphrase) {
		if (agentLockPassphrase != null)
			return false;

		agentLockPassphrase = lockPassphrase;
		return true;
	}

	/* (non-Javadoc)
	 * @see org.connectbot.transport.AbsTransport#usesNetwork()
	 */
	@Override
	public boolean usesNetwork() {
		return true;
	}
}
