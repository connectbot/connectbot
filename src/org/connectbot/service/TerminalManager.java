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

package org.connectbot.service;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.connectbot.ConsoleActivity;
import org.connectbot.R;
import org.connectbot.bean.HostBean;
import org.connectbot.bean.PubkeyBean;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PreferenceConstants;
import org.connectbot.util.PubkeyDatabase;
import org.connectbot.util.PubkeyUtils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;

import com.nullwire.trace.ExceptionHandler;

/**
 * Manager for SSH connections that runs as a background service. This service
 * holds a list of currently connected SSH bridges that are ready for connection
 * up to a GUI if needed.
 *
 * @author jsharkey
 */
public class TerminalManager extends Service implements BridgeDisconnectedListener, OnSharedPreferenceChangeListener {

	public final static String TAG = TerminalManager.class.toString();

	public List<TerminalBridge> bridges = new LinkedList<TerminalBridge>();
	public TerminalBridge defaultBridge = null;

	public List<HostBean> disconnected = new LinkedList<HostBean>();

	public Handler disconnectHandler = null;

	protected HashMap<String, Object> loadedPubkeys = new HashMap<String, Object>();

	protected Resources res;

	protected HostDatabase hostdb;
	protected PubkeyDatabase pubkeydb;

	protected SharedPreferences prefs;

	private final IBinder binder = new TerminalBinder();

	private ConnectivityManager connectivityManager;
	private WifiManager.WifiLock wifilock;

	private MediaPlayer mediaPlayer;

	private Timer idleTimer;
	private final long IDLE_TIMEOUT = 300000; // 5 minutes

	private Vibrator vibrator;
	private volatile boolean wantKeyVibration;
	public static final long VIBRATE_DURATION = 30;

	private NotificationManager notificationManager;

	private boolean wantBellVibration;
	private static final int NOTIFICATION_ID = 1;

	@Override
	public void onCreate() {
		Log.i(TAG, "Starting background service");

		ExceptionHandler.register(this);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);

		res = getResources();

		hostdb = new HostDatabase(this);
		pubkeydb = new PubkeyDatabase(this);

		// load all marked pubkeys into memory
		List<PubkeyBean> pubkeys = pubkeydb.getAllStartPubkeys();

		for (PubkeyBean pubkey : pubkeys) {
			try {
				PrivateKey privKey = PubkeyUtils.decodePrivate(pubkey.getPrivateKey(), pubkey.getType());
				PublicKey pubKey = PubkeyUtils.decodePublic(pubkey.getPublicKey(), pubkey.getType());
				Object trileadKey = PubkeyUtils.convertToTrilead(privKey, pubKey);

				loadedPubkeys.put(pubkey.getNickname(), trileadKey);
				Log.d(TAG, String.format("Added key '%s' to in-memory cache", pubkey.getNickname()));
			} catch (Exception e) {
				Log.d(TAG, String.format("Problem adding key '%s' to in-memory cache", pubkey.getNickname()), e);
			}
		}

		connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

		WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		wifilock = manager.createWifiLock(TAG);

		vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		wantKeyVibration = prefs.getBoolean(PreferenceConstants.BUMPY_ARROWS, true);

		wantBellVibration = prefs.getBoolean(PreferenceConstants.BELL_VIBRATE, true);
		enableMediaPlayer();

		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "Destroying background service");

		// disconnect and dispose of any existing bridges
		for(TerminalBridge bridge : bridges)
			bridge.dispatchDisconnect(true);

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
		}

		if (wifilock != null && wifilock.isHeld())
			wifilock.release();

		disableMediaPlayer();
	}

	/**
	 * Open a new SSH session using the given parameters.
	 */
	private void openConnection(HostBean host) throws IllegalArgumentException, IOException {
		// throw exception if terminal already open
		if (findBridge(host) != null) {
			throw new IllegalArgumentException("Connection already open for that nickname");
		}

		TerminalBridge bridge = new TerminalBridge(this, host);
		bridge.setOnDisconnectedListener(this);
		bridge.startConnection();
		bridges.add(bridge);

		// Add a reference to the WifiLock
		NetworkInfo info = connectivityManager.getActiveNetworkInfo();
		if (isLockingWifi() && info.getType() == ConnectivityManager.TYPE_WIFI) {
			Log.d(TAG, "Acquiring WifiLock");
			wifilock.acquire();
		}

		// also update database with new connected time
		touchHost(host);
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

	public boolean isSavingKeys() {
		return prefs.getBoolean(PreferenceConstants.MEMKEYS, true);
	}

	public String getKeyMode() {
		return prefs.getString(PreferenceConstants.KEYMODE, PreferenceConstants.KEYMODE_RIGHT); // "Use right-side keys"
	}

	public boolean isLockingWifi() {
		return prefs.getBoolean(PreferenceConstants.WIFI_LOCK, true);
	}

	/**
	 * Open a new SSH session by reading parameters from the given URI. Follows
	 * format <code>ssh://user@host:port/#nickname</code>
	 */
	public void openConnection(Uri uri) throws Exception {
		String nickname = uri.getFragment();
		String username = uri.getUserInfo();
		String hostname = uri.getHost();
		int port = uri.getPort();

		HostBean host = hostdb.findHost(nickname, username, hostname, port);

		if (host == null) {
			Log.d(TAG, String.format("Didn't find existing host (nickname=%s, username=%s, hostname=%s, port=%d)",
					nickname, username, hostname, port));
			host = new HostBean(nickname, username, hostname, port);
		}

		this.openConnection(host);
	}

	/**
	 * Update the last-connected value for the given nickname by passing through
	 * to {@link HostDatabase}.
	 */
	private void touchHost(HostBean host) {
		hostdb.touchHost(host);
	}

	/**
	 * Find the {@link TerminalBridge} with the given nickname.
	 */
	public TerminalBridge findBridge(HostBean host) {
		// find the first active bridge with given nickname
		for(TerminalBridge bridge : bridges) {
			if (bridge.host.equals(host))
				return bridge;
		}
		return null;
	}

	/**
	 * Called by child bridge when somehow it's been disconnected.
	 */
	public void onDisconnected(TerminalBridge bridge) {
		// remove this bridge from our list
		bridges.remove(bridge);

		if (bridges.size() == 0 && wifilock.isHeld()) {
			Log.d(TAG, "WifiLock was held, releasing");
			wifilock.release();
		}

		disconnected.add(bridge.host);

		// pass notification back up to gui
		if (disconnectHandler != null)
			Message.obtain(disconnectHandler, -1, bridge).sendToTarget();

	}

	public boolean isKeyLoaded(String nickname) {
		return loadedPubkeys.containsKey(nickname);
	}

	public void addKey(String nickname, Object trileadKey) {
		loadedPubkeys.remove(nickname);
		loadedPubkeys.put(nickname, trileadKey);
	}

	public void removeKey(String nickname) {
		loadedPubkeys.remove(nickname);
	}

	public Object getKey(String nickname) {
		return loadedPubkeys.get(nickname);
	}

	private void stopWithDelay() {
		// TODO add in a way to check whether keys loaded are encrypted and only
		// set timer when we have an encrypted key loaded

		if (loadedPubkeys.size() > 0) {
			synchronized (this) {
				if (idleTimer == null)
					idleTimer = new Timer(true);

				idleTimer.schedule(new IdleTask(), IDLE_TIMEOUT);
			}
		} else {
			Log.d(TAG, "Stopping background service immediately");
			stopSelf();
		}
	}

	protected void stopNow() {
		if (bridges.size() == 0)
			stopSelf();
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

		stopIdleTimer();

		// Make sure we stay running to maintain the bridges
		startService(new Intent(this, TerminalManager.class));

		return binder;
	}

	@Override
	public void onRebind(Intent intent) {
		super.onRebind(intent);

		Log.i(TAG, "Someone rebound to TerminalManager");

		stopIdleTimer();
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.i(TAG, "Someone unbound from TerminalManager");

		if (bridges.size() == 0)
			stopWithDelay();

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

	class BeepListener implements OnCompletionListener {
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

		String contentText = res.getString(
				R.string.notification_text, host.getNickname());

		Notification notification = new Notification(
				R.drawable.notification_icon, contentText,
				System.currentTimeMillis());
		notification.flags |= Notification.FLAG_AUTO_CANCEL;

		Context context = getApplicationContext();
		Intent notificationIntent = new Intent(this, ConsoleActivity.class);
		notificationIntent.setAction("android.intent.action.VIEW");
		notificationIntent.setData(host.getUri());

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, 0);

		notification.setLatestEventInfo(context, res.getString(R.string.app_name),
				contentText, contentIntent);

		notificationManager.notify(NOTIFICATION_ID, notification);
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
		}
	}
}
