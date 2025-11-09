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

package org.connectbot.service;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

import org.connectbot.R;
import org.connectbot.data.entity.Host;
import org.connectbot.data.entity.Pubkey;
import org.connectbot.data.ColorSchemeRepository;
import org.connectbot.data.HostRepository;
import org.connectbot.transport.TransportFactory;
import org.connectbot.util.PreferenceConstants;
import org.connectbot.util.ProviderLoader;
import org.connectbot.util.ProviderLoaderListener;
import org.connectbot.data.PubkeyRepository;
import org.connectbot.util.PubkeyUtils;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Manager for SSH connections that runs as a service. This service holds a list
 * of currently connected SSH bridges that are ready for connection up to a GUI
 * if needed.
 *
 * @author jsharkey
 */
public class TerminalManager extends Service implements BridgeDisconnectedListener, OnSharedPreferenceChangeListener, ProviderLoaderListener {
	public final static String TAG = "CB.TerminalManager";

	private final ArrayList<TerminalBridge> bridges = new ArrayList<>();
	public Map<Host, WeakReference<TerminalBridge>> mHostBridgeMap = new HashMap<>();
	public Map<String, WeakReference<TerminalBridge>> mNicknameBridgeMap = new HashMap<>();

	public TerminalBridge defaultBridge = null;

	public final List<Host> disconnected = new ArrayList<>();

	public BridgeDisconnectedListener disconnectListener = null;

	private final ArrayList<OnHostStatusChangedListener> hostStatusChangedListeners = new ArrayList<>();

	public Map<String, KeyHolder> loadedKeypairs = new HashMap<>();

	public Resources res;

	public HostRepository hostRepository;
	public ColorSchemeRepository colorRepository;
	public PubkeyRepository pubkeyRepository;

	protected SharedPreferences prefs;

	final private IBinder binder = new TerminalBinder();

	private ConnectivityReceiver connectivityManager;

	private MediaPlayer mediaPlayer;

	private Timer pubkeyTimer;

	private Timer idleTimer;
	private final long IDLE_TIMEOUT = 300000; // 5 minutes

	private Vibrator vibrator;
	private volatile boolean wantKeyVibration;
	public static final long VIBRATE_DURATION = 30;

	private boolean wantBellVibration;

	private boolean resizeAllowed = true;

	private boolean savingKeys;

	protected final List<WeakReference<TerminalBridge>> mPendingReconnect = new ArrayList<>();

	public boolean hardKeyboardHidden;

	@Override
	public void onCreate() {
		Log.i(TAG, "Starting service");

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);

		res = getResources();

		pubkeyTimer = new Timer("pubkeyTimer", true);

		hostRepository = HostRepository.Companion.get(this);
		colorRepository = ColorSchemeRepository.Companion.get(this);
		pubkeyRepository = PubkeyRepository.Companion.get(this);

		// load all marked pubkeys into memory
		updateSavingKeys();
		List<Pubkey> pubkeys = pubkeyRepository.getStartupKeysBlocking();

		for (Pubkey pubkey : pubkeys) {
			try {
				KeyPair pair = PubkeyUtils.convertToKeyPair(pubkey, null);
				addKey(pubkey, pair);
			} catch (Exception e) {
				Log.d(TAG, String.format("Problem adding key '%s' to in-memory cache", pubkey.getNickname()), e);
			}
		}

		vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		wantKeyVibration = prefs.getBoolean(PreferenceConstants.BUMPY_ARROWS, true);

		wantBellVibration = prefs.getBoolean(PreferenceConstants.BELL_VIBRATE, true);
		enableMediaPlayer();

		hardKeyboardHidden = (res.getConfiguration().hardKeyboardHidden ==
			Configuration.HARDKEYBOARDHIDDEN_YES);

		final boolean lockingWifi = prefs.getBoolean(PreferenceConstants.WIFI_LOCK, true);

		connectivityManager = new ConnectivityReceiver(this, lockingWifi);

		ProviderLoader.load(this, this);
	}

	private void updateSavingKeys() {
		savingKeys = prefs.getBoolean(PreferenceConstants.MEMKEYS, true);
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "Destroying service");

		disconnectAll(true, false);

		pubkeyRepository = null;

		synchronized (this) {
			if (idleTimer != null)
				idleTimer.cancel();
			if (pubkeyTimer != null)
				pubkeyTimer.cancel();
		}

		connectivityManager.cleanup();

		ConnectionNotifier.getInstance().hideRunningNotification(this);

		disableMediaPlayer();
	}

	/**
	 * Disconnect all currently connected bridges.
	 */
	public void disconnectAll(final boolean immediate, final boolean excludeLocal) {
		TerminalBridge[] tmpBridges = null;

		synchronized (bridges) {
			if (bridges.size() > 0) {
				tmpBridges = bridges.toArray(new TerminalBridge[bridges.size()]);
			}
		}

		if (tmpBridges != null) {
			// disconnect and dispose of any existing bridges
			for (TerminalBridge tmpBridge : tmpBridges) {
				if (excludeLocal && !tmpBridge.isUsingNetwork())
					continue;
				tmpBridge.dispatchDisconnect(immediate);
			}
		}
	}

	/**
	 * Open a new SSH session using the given parameters.
	 */
	private TerminalBridge openConnection(Host host) throws IllegalArgumentException {
		// throw exception if terminal already open
		if (getConnectedBridge(host) != null) {
			throw new IllegalArgumentException("Connection already open for that nickname");
		}

		TerminalBridge bridge = new TerminalBridge(this, host);
		bridge.setOnDisconnectedListener(this);
		bridge.startConnection();

		synchronized (bridges) {
			bridges.add(bridge);
			WeakReference<TerminalBridge> wr = new WeakReference<>(bridge);
			mHostBridgeMap.put(bridge.host, wr);
			mNicknameBridgeMap.put(bridge.host.getNickname(), wr);
		}

		synchronized (disconnected) {
			disconnected.remove(bridge.host);
		}

		if (bridge.isUsingNetwork()) {
			connectivityManager.incRef();
		}

		if (prefs.getBoolean(PreferenceConstants.CONNECTION_PERSIST, true)) {
			ConnectionNotifier.getInstance().showRunningNotification(this);
		}

		// also update database with new connected time
		touchHost(host);

		notifyHostStatusChanged();

		return bridge;
	}

	public String getEmulation() {
		return prefs.getString(PreferenceConstants.EMULATION, "xterm-256color");
	}

	public int getScrollback() {
		int scrollback = 140;
		try {
			scrollback = Integer.parseInt(prefs.getString(PreferenceConstants.SCROLLBACK, "140"));
		} catch (Exception ignored) {
		}
		return scrollback;
	}

	/**
	 * Open a new connection by reading parameters from the given URI. Follows
	 * format specified by an individual transport.
	 */
	public TerminalBridge openConnection(Uri uri) {
		Host host = TransportFactory.findHost(hostRepository, uri);

		if (host == null) {
			Log.d(TAG, "Cannot find host for URI: " + uri.toString() + ". Creating new host.");
			host = TransportFactory.getTransport(uri.getScheme()).createHost(uri);
		}

		return openConnection(host);
	}

	/**
	 * Update the last-connected value for the given nickname by passing through
	 * to {@link HostRepository}.
	 */
	private void touchHost(Host host) {
		hostRepository.touchHostBlocking(host);
	}

	/**
	 * Find a connected {@link TerminalBridge} with the given Host.
	 *
	 * @param host the Host to search for
	 * @return TerminalBridge that uses the Host
	 */
	public TerminalBridge getConnectedBridge(Host host) {
		WeakReference<TerminalBridge> wr = mHostBridgeMap.get(host);
		if (wr != null) {
			return wr.get();
		} else {
			return null;
		}
	}

	/**
	 * Find a connected {@link TerminalBridge} using its nickname.
	 *
	 * @param nickname
	 * @return TerminalBridge that matches nickname
	 */
	public TerminalBridge getConnectedBridge(final String nickname) {
		if (nickname == null) {
			return null;
		}
		WeakReference<TerminalBridge> wr = mNicknameBridgeMap.get(nickname);
		if (wr != null) {
			return wr.get();
		} else {
			return null;
		}
	}

	/**
	 * Called by child bridge when somehow it's been disconnected.
	 */
	@Override
	public void onDisconnected(TerminalBridge bridge) {
		boolean shouldHideRunningNotification = false;
		Log.d(TAG, "Bridge Disconnected. Removing it.");

		synchronized (bridges) {
			// remove this bridge from our list
			bridges.remove(bridge);

			mHostBridgeMap.remove(bridge.host);
			mNicknameBridgeMap.remove(bridge.host.getNickname());

			if (bridge.isUsingNetwork()) {
				connectivityManager.decRef();
			}

			if (bridges.isEmpty() && mPendingReconnect.isEmpty()) {
				shouldHideRunningNotification = true;
			}

			// pass notification back up to gui
			if (disconnectListener != null)
				disconnectListener.onDisconnected(bridge);
		}

		synchronized (disconnected) {
			disconnected.add(bridge.host);
		}

		notifyHostStatusChanged();

		if (shouldHideRunningNotification) {
			ConnectionNotifier.getInstance().hideRunningNotification(this);
		}
	}

	public boolean isKeyLoaded(String nickname) {
		return loadedKeypairs.containsKey(nickname);
	}

	public void addKey(Pubkey pubkey, KeyPair pair) {
		addKey(pubkey, pair, false);
	}

	public void addKey(Pubkey pubkey, KeyPair pair, boolean force) {
		if (!savingKeys && !force)
			return;

		removeKey(pubkey.getNickname());

		byte[] sshPubKey = PubkeyUtils.extractOpenSSHPublic(pair);

		KeyHolder keyHolder = new KeyHolder();
		keyHolder.pubkey = pubkey;
		keyHolder.pair = pair;
		keyHolder.openSSHPubkey = sshPubKey;

		loadedKeypairs.put(pubkey.getNickname(), keyHolder);

		// Note: Pubkey entity doesn't have lifetime field yet
		// This functionality may need to be re-added if needed

		Log.d(TAG, String.format("Added key '%s' to in-memory cache", pubkey.getNickname()));
	}

	public boolean removeKey(String nickname) {
		Log.d(TAG, String.format("Removed key '%s' to in-memory cache", nickname));
		return loadedKeypairs.remove(nickname) != null;
	}

	public boolean removeKey(byte[] publicKey) {
		String nickname = null;
		for (Entry<String, KeyHolder> entry : loadedKeypairs.entrySet()) {
			if (Arrays.equals(entry.getValue().openSSHPubkey, publicKey)) {
				nickname = entry.getKey();
				break;
			}
		}

		if (nickname != null) {
			Log.d(TAG, String.format("Removed key '%s' to in-memory cache", nickname));
			return removeKey(nickname);
		} else
			return false;
	}

	public KeyPair getKey(String nickname) {
		if (loadedKeypairs.containsKey(nickname)) {
			KeyHolder keyHolder = loadedKeypairs.get(nickname);
			return keyHolder.pair;
		} else
			return null;
	}

	public String getKeyNickname(byte[] publicKey) {
		for (Entry<String, KeyHolder> entry : loadedKeypairs.entrySet()) {
			if (Arrays.equals(entry.getValue().openSSHPubkey, publicKey))
				return entry.getKey();
		}
		return null;
	}

	private void stopWithDelay() {
		// TODO add in a way to check whether keys loaded are encrypted and only
		// set timer when we have an encrypted key loaded

		if (loadedKeypairs.size() > 0) {
			synchronized (this) {
				if (idleTimer == null)
					idleTimer = new Timer("idleTimer", true);

				idleTimer.schedule(new IdleTask(), IDLE_TIMEOUT);
			}
		} else {
			Log.d(TAG, "Stopping service immediately");
			stopSelf();
		}
	}

	protected void stopNow() {
		if (bridges.size() == 0) {
			stopSelf();
		}
	}

	private synchronized void stopIdleTimer() {
		if (idleTimer != null) {
			idleTimer.cancel();
			idleTimer = null;
		}
	}

	public ArrayList<TerminalBridge> getBridges() {
		return bridges;
	}

	@Override
	public void onProviderLoaderSuccess() {
		Log.d(TAG, "Installed crypto provider successfully");
	}

	@Override
	public void onProviderLoaderError() {
		Log.e(TAG, "Failure while installing crypto provider");
	}

	public class TerminalBinder extends Binder {
		public TerminalManager getService() {
			return TerminalManager.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "Someone bound to TerminalManager with " + bridges.size() + " bridges active");
		keepServiceAlive();
		setResizeAllowed(true);
		return binder;
	}

	/**
	 * Make sure we stay running to maintain the bridges. Later {@link #stopNow} should be called to stop the service.
	 */
	private void keepServiceAlive() {
		stopIdleTimer();
		startService(new Intent(this, TerminalManager.class));
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		/*
		 * We want this service to continue running until it is explicitly
		 * stopped, so return sticky.
		 */
		return START_STICKY;
	}

	@Override
	public void onRebind(Intent intent) {
		super.onRebind(intent);
		Log.i(TAG, "Someone rebound to TerminalManager with " + bridges.size() + " bridges active");
		keepServiceAlive();
		setResizeAllowed(true);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.i(TAG, "Someone unbound from TerminalManager with " + bridges.size() + " bridges active");

		setResizeAllowed(true);

		if (bridges.isEmpty()) {
			stopWithDelay();
		}

		return true;
	}

	private class IdleTask extends TimerTask {
		@Override
		public void run() {
			Log.d(TAG, String.format("Stopping service after timeout of ~%d seconds", IDLE_TIMEOUT / 1000));
			TerminalManager.this.stopNow();
		}
	}

	public void tryKeyVibrate() {
		if (wantKeyVibration)
			vibrate();
	}

	private void vibrate() {
		if (vibrator != null)
			vibrator.vibrate(VIBRATE_DURATION);
	}

	private void enableMediaPlayer() {
		mediaPlayer = new MediaPlayer();

		float volume = prefs.getFloat(PreferenceConstants.BELL_VOLUME,
				PreferenceConstants.DEFAULT_BELL_VOLUME);

		mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);

		AssetFileDescriptor file = res.openRawResourceFd(R.raw.bell);
		try {
			mediaPlayer.setLooping(false);
			mediaPlayer.setDataSource(file.getFileDescriptor(), file
					.getStartOffset(), file.getLength());
			file.close();
			mediaPlayer.setVolume(volume, volume);
			mediaPlayer.prepare();
		} catch (IOException e) {
			Log.e(TAG, "Error setting up bell media player", e);
		}
	}

	private void disableMediaPlayer() {
		if (mediaPlayer != null) {
			mediaPlayer.release();
			mediaPlayer = null;
		}
	}

	public void playBeep() {
		if (mediaPlayer != null) {
			mediaPlayer.seekTo(0);
			mediaPlayer.start();
		}

		if (wantBellVibration)
			vibrate();
	}

	/**
	 * Send system notification to user for a certain host. When user selects
	 * the notification, it will bring them directly to the ConsoleActivity
	 * displaying the host.
	 *
	 * @param host
	 */
	public void sendActivityNotification(Host host) {
		if (!prefs.getBoolean(PreferenceConstants.BELL_NOTIFICATION, false))
			return;

		ConnectionNotifier.getInstance().showActivityNotification(this, host);
	}

	/* (non-Javadoc)
	 * @see android.content.SharedPreferences.OnSharedPreferenceChangeListener#onSharedPreferenceChanged(android.content.SharedPreferences, java.lang.String)
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (PreferenceConstants.BELL.equals(key)) {
			boolean wantAudible = sharedPreferences.getBoolean(
					PreferenceConstants.BELL, true);
			if (wantAudible && mediaPlayer == null)
				enableMediaPlayer();
			else if (!wantAudible && mediaPlayer != null)
				disableMediaPlayer();
		} else if (PreferenceConstants.BELL_VOLUME.equals(key)) {
			if (mediaPlayer != null) {
				float volume = sharedPreferences.getFloat(
						PreferenceConstants.BELL_VOLUME,
						PreferenceConstants.DEFAULT_BELL_VOLUME);
				mediaPlayer.setVolume(volume, volume);
			}
		} else if (PreferenceConstants.BELL_VIBRATE.equals(key)) {
			wantBellVibration = sharedPreferences.getBoolean(
					PreferenceConstants.BELL_VIBRATE, true);
		} else if (PreferenceConstants.BUMPY_ARROWS.equals(key)) {
			wantKeyVibration = sharedPreferences.getBoolean(
					PreferenceConstants.BUMPY_ARROWS, true);
		} else if (PreferenceConstants.WIFI_LOCK.equals(key)) {
			final boolean lockingWifi = prefs.getBoolean(PreferenceConstants.WIFI_LOCK, true);
			connectivityManager.setWantWifiLock(lockingWifi);
		} else if (PreferenceConstants.MEMKEYS.equals(key)) {
			updateSavingKeys();
		}
	}

	/**
	 * Allow {@link TerminalBridge} to resize when the parent has changed.
	 * @param resizeAllowed
	 */
	public void setResizeAllowed(boolean resizeAllowed) {
		this.resizeAllowed  = resizeAllowed;
	}

	public boolean isResizeAllowed() {
		return resizeAllowed;
	}

	public static class KeyHolder {
		public Pubkey pubkey;
		public KeyPair pair;
		public byte[] openSSHPubkey;
	}

	/**
	 * Called when connectivity to the network is lost and it doesn't appear
	 * we'll be getting a different connection any time soon.
	 */
	public void onConnectivityLost() {
		final Thread t = new Thread() {
			@Override
			public void run() {
				disconnectAll(false, true);
			}
		};
		t.setName("Disconnector");
		t.start();
	}

	/**
	 * Called when connectivity to the network is restored.
	 */
	public void onConnectivityRestored() {
		final Thread t = new Thread() {
			@Override
			public void run() {
				reconnectPending();
			}
		};
		t.setName("Reconnector");
		t.start();
	}

	/**
	 * Insert request into reconnect queue to be executed either immediately
	 * or later when connectivity is restored depending on whether we're
	 * currently connected.
	 *
	 * @param bridge the TerminalBridge to reconnect when possible
	 */
	public void requestReconnect(TerminalBridge bridge) {
		synchronized (mPendingReconnect) {
			mPendingReconnect.add(new WeakReference<>(bridge));
			if (!bridge.isUsingNetwork() ||
					connectivityManager.isConnected()) {
				reconnectPending();
			}
		}
	}

	/**
	 * Reconnect all bridges that were pending a reconnect when connectivity
	 * was lost.
	 */
	private void reconnectPending() {
		synchronized (mPendingReconnect) {
			for (WeakReference<TerminalBridge> ref : mPendingReconnect) {
				TerminalBridge bridge = ref.get();
				if (bridge == null) {
					continue;
				}
				bridge.startConnection();
			}
			mPendingReconnect.clear();
		}
	}

	/**
	 * Register a {@code listener} that wants to know when a host's status materially changes.
	 * @see #hostStatusChangedListeners
	 */
	public void registerOnHostStatusChangedListener(OnHostStatusChangedListener listener) {
		if (!hostStatusChangedListeners.contains(listener)) {
			hostStatusChangedListeners.add(listener);
		}
	}

	/**
	 * Unregister a {@code listener} that wants to know when a host's status materially changes.
	 * @see #hostStatusChangedListeners
	 */
	public void unregisterOnHostStatusChangedListener(OnHostStatusChangedListener listener) {
		hostStatusChangedListeners.remove(listener);
	}

	private void notifyHostStatusChanged() {
		for (OnHostStatusChangedListener listener : hostStatusChangedListeners) {
			listener.onHostStatusChanged();
		}
	}
}
