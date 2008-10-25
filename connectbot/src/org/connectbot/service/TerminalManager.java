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

import java.util.LinkedList;
import java.util.List;

import org.connectbot.R;
import org.connectbot.util.HostDatabase;

import com.trilead.ssh2.Connection;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Manager for SSH connections that runs as a background service. This service
 * holds a list of currently connected SSH bridges that are ready for connection
 * up to a GUI if needed.
 * 
 * @author jsharkey
 */
public class TerminalManager extends Service {
	
	public final static String TAG = TerminalManager.class.toString();
	
	public List<TerminalBridge> bridges = new LinkedList<TerminalBridge>();
	public TerminalBridge defaultBridge = null;
	
	protected SharedPreferences prefs;
	protected String pref_emulation, pref_scrollback;
	
	@Override
	public void onCreate() {
		Log.i(TAG, "Starting background service");
		this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
		this.pref_emulation = this.getResources().getString(R.string.pref_emulation);
		this.pref_scrollback = this.getResources().getString(R.string.pref_scrollback);
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "Destroying background service");

		// disconnect and dispose of any existing bridges
		for(TerminalBridge bridge : bridges)
			bridge.dispose();
		
	}
	
	/**
	 * Open a new SSH session using the given parameters.
	 */
	public void openConnection(String nickname, String hostname, String username, int port) throws Exception {
		// throw exception if terminal already open
		if(this.findBridge(nickname) != null) {
			throw new Exception("Connection already open for that nickname");
		}
		
		String emulation = prefs.getString(this.pref_emulation, "screen");
		int scrollback = 140;
		try {
			scrollback = Integer.parseInt(prefs.getString(this.pref_scrollback, "140"));
		} catch(Exception e) {
		}
		
		TerminalBridge bridge = new TerminalBridge(nickname, username, hostname, port, emulation, scrollback);
		this.bridges.add(bridge);
		
		// also update database with new connected time
		this.touchHost(nickname);
		
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
		HostDatabase hostdb = new HostDatabase(this);
		hostdb.touchHost(nickname);
		hostdb.close();
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

	/**
	 * Force disconnection of this {@link TerminalBridge} and remove it from our
	 * internal list of active connections.
	 */
	public void disconnect(TerminalBridge bridge) {
		bridge.dispose();
		this.bridges.remove(bridge);
		
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
