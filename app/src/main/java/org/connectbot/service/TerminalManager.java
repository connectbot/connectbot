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
import org.connectbot.bean.HostBean;
import org.connectbot.bean.PortForwardBean;
import org.connectbot.bean.PubkeyBean;
import org.connectbot.data.ColorStorage;
import org.connectbot.data.HostStorage;
import org.connectbot.transport.TransportFactory;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.NetworkUtils;
import org.connectbot.util.PreferenceConstants;
import org.connectbot.util.ProviderLoader;
import org.connectbot.util.ProviderLoaderListener;
import org.connectbot.util.PubkeyDatabase;
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
	public Map<HostBean, WeakReference<TerminalBridge>> mHostBridgeMap = new HashMap<>();
	public Map<String, WeakReference<TerminalBridge>> mNicknameBridgeMap = new HashMap<>();

	public TerminalBridge defaultBridge = null;

	public final List<HostBean> disconnected = new ArrayList<>();

	public BridgeDisconnectedListener disconnectListener = null;

	private final ArrayList<OnHostStatusChangedListener> hostStatusChangedListeners = new ArrayList<>();

	public Map<String, KeyHolder> loadedKeypairs = new HashMap<>();

	public Resources res;

	public HostStorage hostdb;
	public ColorStorage colordb;
	public PubkeyDatabase pubkeydb;

	protected SharedPreferences prefs;

	final private IBinder binder = new TerminalBinder();

	private ConnectivityReceiver connectivityManager;
	private AccessPointReceiver accessPointReceiver;

	private MediaPlayer mediaPlayer;

	private Timer pubkeyTimer;

	private Timer idleTimer;
	
	private Timer apStateTimer;
	private boolean apMonitoringActive = false;
	private final long IDLE_TIMEOUT = 300000; // 5 minutes

	private Vibrator vibrator;
	private volatile boolean wantKeyVibration;
	public static final long VIBRATE_DURATION = 30;


	private boolean wantBellVibration;

	private boolean resizeAllowed = true;

	private boolean savingKeys;

	protected final List<WeakReference<TerminalBridge>> mPendingReconnect = new ArrayList<>();

	public boolean hardKeyboardHidden;

	/**
	 * Create a thread-safe copy of the bridges list for iteration.
	 * This helper prevents ConcurrentModificationException when iterating
	 * bridges from background threads while the list may be modified.
	 *
	 * @return array of bridges (empty array if no bridges, never null)
	 */
	private TerminalBridge[] getBridgesCopy() {
		synchronized (bridges) {
			return bridges.toArray(new TerminalBridge[bridges.size()]);
		}
	}

	/**
	 * Thread-safe check if there are any active bridges.
	 * This is more efficient than getBridgesCopy() when only checking existence.
	 *
	 * @return true if there are active bridges
	 */
	private boolean hasBridges() {
		synchronized (bridges) {
			return !bridges.isEmpty();
		}
	}

	/**
	 * Get the number of active bridges in a thread-safe manner.
	 * Primarily used for logging and debugging.
	 *
	 * @return number of active bridges
	 */
	private int getBridgeCount() {
		synchronized (bridges) {
			return bridges.size();
		}
	}

	@Override
	public void onCreate() {
		Log.i(TAG, "Starting service");

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);

		res = getResources();

		pubkeyTimer = new Timer("pubkeyTimer", true);
		apStateTimer = new Timer("apStateTimer", true);

		hostdb = HostDatabase.get(this);
		colordb = HostDatabase.get(this);
		pubkeydb = PubkeyDatabase.get(this);

		// load all marked pubkeys into memory
		updateSavingKeys();
		List<PubkeyBean> pubkeys = pubkeydb.getAllStartPubkeys();

		for (PubkeyBean pubkey : pubkeys) {
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

		updateAccessPointMonitoring();

		hardKeyboardHidden = (res.getConfiguration().hardKeyboardHidden ==
			Configuration.HARDKEYBOARDHIDDEN_YES);

		final boolean lockingWifi = prefs.getBoolean(PreferenceConstants.WIFI_LOCK, true);

		connectivityManager = new ConnectivityReceiver(this, lockingWifi);
		accessPointReceiver = new AccessPointReceiver(this);

		ProviderLoader.load(this, this);
	}

	private void updateSavingKeys() {
		savingKeys = prefs.getBoolean(PreferenceConstants.MEMKEYS, true);
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "Destroying service");

		disconnectAll(true, false);

		hostdb = null;
		pubkeydb = null;

		synchronized (this) {
			if (idleTimer != null)
				idleTimer.cancel();
			if (pubkeyTimer != null)
				pubkeyTimer.cancel();
			if (apStateTimer != null) {
				apStateTimer.cancel();
				apMonitoringActive = false;
			}
		}

		connectivityManager.cleanup();
		accessPointReceiver.cleanup();

		ConnectionNotifier.getInstance().hideRunningNotification(this);

		disableMediaPlayer();
	}

	/**
	 * Disconnect all currently connected bridges.
	 */
	public void disconnectAll(final boolean immediate, final boolean excludeLocal) {
		// disconnect and dispose of any existing bridges
		for (TerminalBridge tmpBridge : getBridgesCopy()) {
			if (excludeLocal && !tmpBridge.isUsingNetwork())
				continue;
			tmpBridge.dispatchDisconnect(immediate);
		}
	}

	/**
	 * Open a new SSH session using the given parameters.
	 */
	private TerminalBridge openConnection(HostBean host) throws IllegalArgumentException {
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
			updateRunningNotificationWithApInfo();
		}
		
		updateAccessPointMonitoring();

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
		HostBean host = TransportFactory.findHost(hostdb, uri);

		if (host == null)
			host = TransportFactory.getTransport(uri.getScheme()).createHost(uri);

		return openConnection(host);
	}

	/**
	 * Update the last-connected value for the given nickname by passing through
	 * to {@link HostDatabase}.
	 */
	private void touchHost(HostBean host) {
		hostdb.touchHost(host);
	}

	/**
	 * Find a connected {@link TerminalBridge} with the given HostBean.
	 *
	 * @param host the HostBean to search for
	 * @return TerminalBridge that uses the HostBean
	 */
	public TerminalBridge getConnectedBridge(HostBean host) {
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
		} else {
			// Update notification in case this bridge had AP forwards
			updateRunningNotificationWithApInfo();
		}
		
		updateAccessPointMonitoring();
	}

	public boolean isKeyLoaded(String nickname) {
		return loadedKeypairs.containsKey(nickname);
	}

	public void addKey(PubkeyBean pubkey, KeyPair pair) {
		addKey(pubkey, pair, false);
	}

	public void addKey(PubkeyBean pubkey, KeyPair pair, boolean force) {
		if (!savingKeys && !force)
			return;

		removeKey(pubkey.getNickname());

		byte[] sshPubKey = PubkeyUtils.extractOpenSSHPublic(pair);

		KeyHolder keyHolder = new KeyHolder();
		keyHolder.bean = pubkey;
		keyHolder.pair = pair;
		keyHolder.openSSHPubkey = sshPubKey;

		loadedKeypairs.put(pubkey.getNickname(), keyHolder);

		if (pubkey.getLifetime() > 0) {
			final String nickname = pubkey.getNickname();
			pubkeyTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					Log.d(TAG, "Unloading from memory key: " + nickname);
					removeKey(nickname);
				}
			}, pubkey.getLifetime() * 1000L);
		}

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
		if (!hasBridges()) {
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
		Log.i(TAG, "Someone bound to TerminalManager with " + getBridgeCount() + " bridges active");
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
		Log.i(TAG, "Someone rebound to TerminalManager with " + getBridgeCount() + " bridges active");
		keepServiceAlive();
		setResizeAllowed(true);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.i(TAG, "Someone unbound from TerminalManager with " + getBridgeCount() + " bridges active");

		setResizeAllowed(true);

		// Get snapshot once to avoid TOCTOU race between hasBridges() and getBridgesCopy()
		TerminalBridge[] bridgesCopy = getBridgesCopy();
		if (bridgesCopy.length == 0) {
			stopWithDelay();
		} else {
			// tell each bridge to forget about their previous prompt handler
			for (TerminalBridge bridge : bridgesCopy) {
				bridge.promptHelper.clearListener();
			}
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
	public void sendActivityNotification(HostBean host) {
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
		public PubkeyBean bean;
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

	public void notifyHostStatusChanged() {
		for (OnHostStatusChangedListener listener : hostStatusChangedListeners) {
			listener.onHostStatusChanged();
		}
	}
	
	/**
	 * Update access point notification state based on current port forwards
	 * Called when port forwards are enabled/disabled
	 */
	public void updateAccessPointNotification() {
		updateRunningNotificationWithApInfo();
		updateAccessPointMonitoring();
	}
	
	/**
	 * Check for AP state changes and update notification if needed
	 * Should be called periodically to keep notification in sync with AP state
	 */
	public void checkAccessPointStateChange() {
		if (NetworkUtils.hasAccessPointStateChanged(this)) {
			Log.d(TAG, "AP state changed, updating notification");
			
			// Check if AP became available - if so, retry failed AP port forwards
			String currentApIP = NetworkUtils.getAccessPointIP(this);
			if (currentApIP != null) {
				Log.d(TAG, "AP is now available, retrying failed AP port forwards");
				retryFailedAccessPointForwards();
			}
			
			updateRunningNotificationWithApInfo();
			
			// Notify host status listeners so UI can update (like port forwarding display in host list)
			notifyHostStatusChanged();
		}
	}
	
	/**
	 * Update access point monitoring state based on current needs
	 * Starts monitoring if there are active connections with AP port forwards
	 * Stops monitoring if there are no connections or no AP forwards configured
	 *
	 * Note: Android does not provide reliable broadcast intents for WiFi hotspot state changes.
	 * The WIFI_AP_STATE_CHANGED intent is not documented in the public API and may not work
	 * consistently across all devices and Android versions. Therefore, we use periodic polling
	 * to detect AP state changes for reliable cross-device compatibility.
	 */
	private void updateAccessPointMonitoring() {
		boolean shouldMonitor = hasBridges() && hasActiveAccessPointForwards();

		if (shouldMonitor && !apMonitoringActive) {
			// Start monitoring - hybrid approach: broadcast receiver for fast response + timer for reliability
			Log.d(TAG, "Starting AP state monitoring (hybrid: broadcast + 10s polling)");
			apStateTimer.schedule(new ApStateMonitorTask(), 0, 10000);  // 10s polling
			apMonitoringActive = true;
		} else if (!shouldMonitor && apMonitoringActive) {
			// Stop monitoring
			Log.d(TAG, "Stopping AP state monitoring");
			apStateTimer.cancel();
			apStateTimer = new Timer("apStateTimer", true);
			apMonitoringActive = false;
		}
	}
	
	/**
	 * Timer task to monitor access point state changes every 10 seconds.
	 * This provides a reliable fallback for AP state monitoring that works on all devices.
	 * Combined with AccessPointReceiver for immediate response on devices that support broadcasts.
	 * Only runs when monitoring is active (connections exist with AP port forwards).
	 */
	private class ApStateMonitorTask extends TimerTask {
		@Override
		public void run() {
			Log.d(TAG, "ApStateMonitorTask running - checking for AP state changes");
			checkAccessPointStateChange();
		}
	}
	
	/**
	 * Update the running notification to include AP information when relevant
	 */
	private void updateRunningNotificationWithApInfo() {
		// Only update if we have active connections (running notification is showing)
		if (!hasBridges()) {
			return;
		}

		boolean hasActiveForwards = hasActiveAccessPointForwards();
		String apIP = null;

		if (hasActiveForwards) {
			apIP = NetworkUtils.getAccessPointIP(this);
		}

		Log.d(TAG, "Updating running notification: hasApForwards=" + hasActiveForwards + ", apIP=" + apIP);
		ConnectionNotifier.getInstance().showRunningNotification(this, apIP, hasActiveForwards);
	}
	
	/**
	 * Check if there are any configured access point port forwards
	 * @return true if any access point forwards are configured (enabled or failed to enable)
	 */
	private boolean hasActiveAccessPointForwards() {
		// Check all active terminal bridges for access point forwards
		for (TerminalBridge bridge : getBridgesCopy()) {
			if (bridge != null) {
				List<PortForwardBean> forwards = bridge.getPortForwards();
				for (PortForwardBean forward : forwards) {
					// Count both enabled forwards and those configured for AP (even if failed to bind)
					if (NetworkUtils.BIND_ACCESS_POINT.equals(forward.getBindAddress())) {
						return true; // Found at least one
					}
				}
			}
		}

		return false;
	}
	
	/**
	 * Retry failed access point port forwards when AP becomes available
	 */
	private void retryFailedAccessPointForwards() {
		// Check all active terminal bridges for failed AP forwards
		for (TerminalBridge bridge : getBridgesCopy()) {
			if (bridge != null) {
				List<PortForwardBean> forwards = bridge.getPortForwards();
				for (PortForwardBean forward : forwards) {
					// Look for AP forwards that are not enabled (likely failed due to no AP)
					if (NetworkUtils.BIND_ACCESS_POINT.equals(forward.getBindAddress()) && !forward.isEnabled()) {
						Log.d(TAG, "Retrying failed AP port forward: " + forward.getNickname());
						// Ask the bridge to retry enabling this forward
						bridge.enablePortForward(forward);
					}
				}
			}
		}
	}
}
