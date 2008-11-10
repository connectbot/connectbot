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
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PubkeyDatabase;
import org.connectbot.util.PubkeyUtils;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
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
	
	public List<String> disconnected = new LinkedList<String>();
	
	protected HashMap<String, Object> loadedPubkeys = new HashMap<String, Object>();
	
	protected Resources res;
	
	public HostDatabase hostdb;
	protected PubkeyDatabase pubkeydb;

	protected SharedPreferences prefs;
	protected String pref_emulation, pref_scrollback, pref_keymode, pref_memkeys;
	
	@Override
	public void onCreate() {
		Log.i(TAG, "Starting background service");
		this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
		this.pref_emulation = this.getResources().getString(R.string.pref_emulation);
		this.pref_scrollback = this.getResources().getString(R.string.pref_scrollback);
		this.pref_keymode = this.getResources().getString(R.string.pref_keymode);
		this.pref_memkeys = this.getResources().getString(R.string.pref_memkeys);
		
		this.res = this.getResources();
		
		this.hostdb = new HostDatabase(this);
		this.pubkeydb = new PubkeyDatabase(this);
		
		// load all marked pubkeys into memory
		Cursor c = pubkeydb.getAllStartPubkeys();
		int COL_NICKNAME = c.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_NICKNAME),
			COL_TYPE = c.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_TYPE),
			COL_PRIVATE = c.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_PRIVATE),
			COL_PUBLIC = c.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_PUBLIC);
		
		while(c.moveToNext()) {
			String keyNickname = c.getString(COL_NICKNAME);
			try {
				PrivateKey privKey = PubkeyUtils.decodePrivate(c.getBlob(COL_PRIVATE), c.getString(COL_TYPE));
				PublicKey pubKey = PubkeyUtils.decodePublic(c.getBlob(COL_PUBLIC), c.getString(COL_TYPE));
				Object trileadKey = PubkeyUtils.convertToTrilead(privKey, pubKey);
				
				this.loadedPubkeys.put(keyNickname, trileadKey);
				Log.d(TAG, String.format("Added key '%s' to in-memory cache", keyNickname));
			} catch (Exception e) {
				Log.d(TAG, String.format("Problem adding key '%s' to in-memory cache", keyNickname), e);
			}
		}
		c.close();

		
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "Destroying background service");

		// disconnect and dispose of any existing bridges
		for(TerminalBridge bridge : bridges)
			bridge.dispatchDisconnect();
		
		if(this.hostdb != null) {
			this.hostdb.close();
			this.hostdb = null;
		}
		
		if(this.pubkeydb != null) {
			this.pubkeydb.close();
			this.pubkeydb = null;
		}
		
	}
	
	/**
	 * Open a new SSH session using the given parameters.
	 */
	public void openConnection(String nickname, String hostname, String username, int port) throws Exception {
		// throw exception if terminal already open
		if(this.findBridge(nickname) != null) {
			throw new Exception("Connection already open for that nickname");
		}
		
		TerminalBridge bridge = new TerminalBridge(this, nickname, username, hostname, port);
		bridge.setOnDisconnectedListener(this);
		bridge.startConnection();
		this.bridges.add(bridge);
		
		// also update database with new connected time
		this.touchHost(nickname);
		
	}
	
	public String getEmulation() {
		return prefs.getString(this.pref_emulation, "screen");
	}
	
	public int getScrollback() {
		int scrollback = 140;
		try {
			scrollback = Integer.parseInt(prefs.getString(this.pref_scrollback, "140"));
		} catch(Exception e) {
		}
		return scrollback;
	}
	
	public boolean isSavingKeys() {
		return prefs.getBoolean(this.pref_memkeys, true);
	}
	
	public String getPostLogin(String nickname) {
		return hostdb.getPostLogin(nickname);
	}
	
	public boolean getWantSession(String nickname) {
		return hostdb.getWantSession(nickname);
	}

	public String getKeyMode() {
		return prefs.getString(this.pref_keymode, getString(R.string.list_keymode_right)); // "Use right-side keys"
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
		
		this.openConnection(nickname, hostname, username, port);
	}
	
	/**
	 * Update the last-connected value for the given nickname by passing through
	 * to {@link HostDatabase}.
	 */
	protected void touchHost(String nickname) {
		hostdb.touchHost(nickname);
	}

	/**
	 * Find the {@link TerminalBridge} with the given nickname.  
	 */
	public TerminalBridge findBridge(String nickname) {
		// find the first active bridge with given nickname
		for(TerminalBridge bridge : bridges) {
			if(bridge.nickname.equals(nickname))
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
		this.bridges.remove(bridge);
		this.disconnected.add(bridge.nickname);
		
		// pass notification back up to gui
		if(this.disconnectHandler != null)
			Message.obtain(this.disconnectHandler, -1, bridge).sendToTarget();
		
	}
	
	public boolean isKeyLoaded(String nickname) {
		return this.loadedPubkeys.containsKey(nickname);
	}
	
	public void addKey(String nickname, Object trileadKey) {
		this.loadedPubkeys.remove(nickname);
		this.loadedPubkeys.put(nickname, trileadKey);
	}
	
	public void removeKey(String nickname) {
		this.loadedPubkeys.remove(nickname);
	}
	
	public Object getKey(String nickname) {
		return this.loadedPubkeys.get(nickname);
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
