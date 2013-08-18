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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

import org.connectbot.R;
import org.connectbot.bean.HostBean;
import org.connectbot.bean.PubkeyBean;
import org.connectbot.transport.TransportFactory;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PreferenceConstants;
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
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
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
public class TerminalManager extends Service implements BridgeDisconnectedListener, OnSharedPreferenceChangeListener {
	public final static String TAG = "ConnectBot.TerminalManager";

	public List<TerminalBridge> bridges = new LinkedList<TerminalBridge>();
	public Map<HostBean, WeakReference<TerminalBridge>> mHostBridgeMap =
		new HashMap<HostBean, WeakReference<TerminalBridge>>();
	public Map<String, WeakReference<TerminalBridge>> mNicknameBridgeMap =
		new HashMap<String, WeakReference<TerminalBridge>>();

	public TerminalBridge defaultBridge = null;

	public List<HostBean> disconnected = new LinkedList<HostBean>();

	public Handler disconnectHandler = null;

	public Map<String, KeyHolder> loadedKeypairs = new HashMap<String, KeyHolder>();

	public Resources res;

	public HostDatabase hostdb;
	public PubkeyDatabase pubkeydb;

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

	protected List<WeakReference<TerminalBridge>> mPendingReconnect
			= new LinkedList<WeakReference<TerminalBridge>>();

	public boolean hardKeyboardHidden;

	@Override
	public void onCreate() {
		Log.i(TAG, "Starting service");

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);

		res = getResources();

		pubkeyTimer = new Timer("pubkeyTimer", true);

		hostdb = new HostDatabase(this);
		pubkeydb = new PubkeyDatabase(this);

		// load all marked pubkeys into memory
		updateSavingKeys();
		List<PubkeyBean> pubkeys = pubkeydb.getAllStartPubkeys();

		for (PubkeyBean pubkey : pubkeys) {
			try {
				PrivateKey privKey = PubkeyUtils.decodePrivate(pubkey.getPrivateKey(), pubkey.getType());
				PublicKey pubKey = PubkeyUtils.decodePublic(pubkey.getPublicKey(), pubkey.getType());
				KeyPair pair = new KeyPair(pubKey, privKey);

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

	}

	private void updateSavingKeys() {
		savingKeys = prefs.getBoolean(PreferenceConstants.MEMKEYS, true);
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "Destroying service");

		disconnectAll(true);

		if(hostdb != null) {
			hostdb.close();
			hostdb = null;
		}

		if(pubkeydb != null) {
			pubkeydb.close();
			pubkeydb = null;
		}

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
	private void disconnectAll(final boolean immediate) {
		TerminalBridge[] tmpBridges = null;

		synchronized (bridges) {
			if (bridges.size() > 0) {
				tmpBridges = bridges.toArray(new TerminalBridge[bridges.size()]);
			}
		}

		if (tmpBridges != null) {
			// disconnect and dispose of any existing bridges
			for (int i = 0; i < tmpBridges.length; i++)
				tmpBridges[i].dispatchDisconnect(immediate);
		}
	}

	/**
	 * Open a new SSH session using the given parameters.
	 */
	private TerminalBridge openConnection(HostBean host) throws IllegalArgumentException, IOException {
		// throw exception if terminal already open
		if (getConnectedBridge(host) != null) {
			throw new IllegalArgumentException("Connection already open for that nickname");
		}

		TerminalBridge bridge = new TerminalBridge(this, host);
		bridge.setOnDisconnectedListener(this);
		bridge.startConnection();

		synchronized (bridges) {
			bridges.add(bridge);
			WeakReference<TerminalBridge> wr = new WeakReference<TerminalBridge>(bridge);
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

		return bridge;
	}

	public String getEmulation() {
		return prefs.getString(PreferenceConstants.EMULATION, "screen");
	}

	public int getScrollback() {
		int scrollback = 140;
		try {
			scrollback = Integer.parseInt(prefs.getString(PreferenceConstants.SCROLLBACK, "140"));
		} catch(Exception e) {
		}
		return scrollback;
	}

	/**
	 * Open a new connection by reading parameters from the given URI. Follows
	 * format specified by an individual transport.
	 */
	public TerminalBridge openConnection(Uri uri) throws Exception {
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
	public void onDisconnected(TerminalBridge bridge) {
		boolean shouldHideRunningNotification = false;

		synchronized (bridges) {
			// remove this bridge from our list
			bridges.remove(bridge);

			mHostBridgeMap.remove(bridge.host);
			mNicknameBridgeMap.remove(bridge.host.getNickname());

			if (bridge.isUsingNetwork()) {
				connectivityManager.decRef();
			}

			if (bridges.size() == 0 &&
					mPendingReconnect.size() == 0) {
				shouldHideRunningNotification = true;
			}
		}

		synchronized (disconnected) {
			disconnected.add(bridge.host);
		}

		if (shouldHideRunningNotification) {
			ConnectionNotifier.getInstance().hideRunningNotification(this);
		}

		// pass notification back up to gui
		if (disconnectHandler != null)
			Message.obtain(disconnectHandler, -1, bridge).sendToTarget();
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
			}, pubkey.getLifetime() * 1000);
		}

		Log.d(TAG, String.format("Added key '%s' to in-memory cache", pubkey.getNickname()));
	}

	public boolean removeKey(String nickname) {
		Log.d(TAG, String.format("Removed key '%s' to in-memory cache", nickname));
		return loadedKeypairs.remove(nickname) != null;
	}

	public boolean removeKey(byte[] publicKey) {
		String nickname = null;
		for (Entry<String,KeyHolder> entry : loadedKeypairs.entrySet()) {
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

	public KeyPair getKey(byte[] publicKey) {
		for (KeyHolder keyHolder : loadedKeypairs.values()) {
			if (Arrays.equals(keyHolder.openSSHPubkey, publicKey))
				return keyHolder.pair;
		}
		return null;
	}

	public String getKeyNickname(byte[] publicKey) {
		for (Entry<String,KeyHolder> entry : loadedKeypairs.entrySet()) {
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

	public class TerminalBinder extends Binder {
		public TerminalManager getService() {
			return TerminalManager.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "Someone bound to TerminalManager");

		setResizeAllowed(true);

		stopIdleTimer();

		// Make sure we stay running to maintain the bridges
		startService(new Intent(this, TerminalManager.class));

		return binder;
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

		setResizeAllowed(true);

		Log.i(TAG, "Someone rebound to TerminalManager");

		stopIdleTimer();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.i(TAG, "Someone unbound from TerminalManager");

		setResizeAllowed(true);

		if (bridges.size() == 0) {
			stopWithDelay();
		}

		return true;
	}

	private class IdleTask extends TimerTask {
		/* (non-Javadoc)
		 * @see java.util.TimerTask#run()
		 */
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
		mediaPlayer.setOnCompletionListener(new BeepListener());

		AssetFileDescriptor file = res.openRawResourceFd(R.raw.bell);
		try {
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
		if (mediaPlayer != null)
			mediaPlayer.start();

		if (wantBellVibration)
			vibrate();
	}

	private static class BeepListener implements OnCompletionListener {
		public void onCompletion(MediaPlayer mp) {
			mp.seekTo(0);
		}
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
				disconnectAll(false);
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
			mPendingReconnect.add(new WeakReference<TerminalBridge>(bridge));
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
}
