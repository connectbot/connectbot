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

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.connectbot.R;
import org.connectbot.bean.HostBean;
import org.connectbot.bean.PubkeyBean;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PubkeyDatabase;
import org.connectbot.util.PubkeyUtils;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Manager for SSH connections that runs as a background service. This service
 * holds a list of currently connected SSH bridges that are ready for connection
 * up to a GUI if needed.
 * 
 * @author jsharkey
 */
public class TerminalManager extends Service implements BridgeDisconnectedListener {
	
	public final static String TAG = TerminalManager.class.toString();
	
	public List<TerminalBridge> bridges = new LinkedList<TerminalBridge>();
	public TerminalBridge defaultBridge = null;
	
	public List<HostBean> disconnected = new LinkedList<HostBean>();
	
	protected HashMap<String, Object> loadedPubkeys = new HashMap<String, Object>();
	
	protected Resources res;
	
	protected HostDatabase hostdb;
	protected PubkeyDatabase pubkeydb;

	protected SharedPreferences prefs;
	private String pref_emulation, pref_scrollback, pref_keymode, pref_memkeys, pref_wifilock;
	
	private ConnectivityManager connectivityManager;
	private WifiManager.WifiLock wifilock;
	
	@Override
	public void onCreate() {
		Log.i(TAG, "Starting background service");
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		pref_emulation = getResources().getString(R.string.pref_emulation);
		pref_scrollback = getResources().getString(R.string.pref_scrollback);
		pref_keymode = getResources().getString(R.string.pref_keymode);
		pref_memkeys = getResources().getString(R.string.pref_memkeys);
		pref_wifilock = getResources().getString(R.string.pref_wifilock);
		
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
		
		if (wifilock != null)
			wifilock.release();
	}
	
	/**
	 * Open a new SSH session using the given parameters.
	 */
	private void openConnection(HostBean host) throws Exception {
		// throw exception if terminal already open
		if (findBridge(host) != null) {
			throw new Exception("Connection already open for that nickname");
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
		return prefs.getString(pref_emulation, "screen");
	}
	
	public int getScrollback() {
		int scrollback = 140;
		try {
			scrollback = Integer.parseInt(prefs.getString(pref_scrollback, "140"));
		} catch(Exception e) {
		}
		return scrollback;
	}
	
	public boolean isSavingKeys() {
		return prefs.getBoolean(pref_memkeys, true);
	}

	public String getKeyMode() {
		return prefs.getString(pref_keymode, getString(R.string.list_keymode_right)); // "Use right-side keys"
	}
	
	public boolean isLockingWifi() {
		return prefs.getBoolean(pref_wifilock, true);
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
	
	public Handler disconnectHandler = null;

//	/**
//	 * Force disconnection of this {@link TerminalBridge} and remove it from our
//	 * internal list of active connections.
//	 */
//	public void disconnect(TerminalBridge bridge) {
//		// we will be notified about this through call back up to onDisconnected()
//		bridge.disconnect();
//	}
	
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
		if(disconnectHandler != null)
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

	public class TerminalBinder extends Binder {
		public TerminalManager getService() {
			return TerminalManager.this;
		}
	}
	
	private final IBinder binder = new TerminalBinder();

	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "Someone bound to TerminalManager");
		return binder;
	}
}
