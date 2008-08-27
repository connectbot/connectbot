package org.connectbot.service;

import java.util.LinkedList;
import java.util.List;

import org.theb.ssh.InteractiveHostKeyVerifier;

import com.trilead.ssh2.Connection;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class TerminalManager extends Service {
	
	public List<TerminalBridge> bridges = new LinkedList<TerminalBridge>();
	public TerminalBridge defaultBridge = null;

	@Override
	public void onCreate() {
		Log.w(this.getClass().toString(), "onCreate()");
	}

	@Override
	public void onDestroy() {
		Log.w(this.getClass().toString(), "onDestroy()");
		// disconnect and dispose of any bridges
		for(TerminalBridge bridge : bridges)
			bridge.dispose();
	}
	
	public void openConnection(Connection conn, String nickname, String emulation, int scrollback) throws Exception {
		try {
			TerminalBridge bridge = new TerminalBridge(conn, nickname, emulation, scrollback);
			this.bridges.add(bridge);
		} catch (Exception e) {
			throw e;
		}
	}

	public void openConnection(String nickname, String host, int port, String user, String pass, String emulation, int scrollback) throws Exception {
		try {
			Connection conn = new Connection(host, port);
			conn.connect(new InteractiveHostKeyVerifier());
			if(conn.isAuthMethodAvailable(user, "password")) {
				conn.authenticateWithPassword(user, pass);
			}
			TerminalBridge bridge = new TerminalBridge(conn, nickname, emulation, scrollback);
			this.bridges.add(bridge);
		} catch (Exception e) {
			throw e;
		}
	}
	
	public TerminalBridge findBridge(String nickname) {
		// find the first active bridge with given nickname
		for(TerminalBridge bridge : bridges) {
			if(bridge.overlay.equals(nickname))
				return bridge;
		}
		return null;
	}


	public class TerminalBinder extends Binder {
		public TerminalManager getService() {
			return TerminalManager.this;
		}
	}
	
	private final IBinder binder = new TerminalBinder();

	@Override
	public IBinder onBind(Intent intent) {
		Log.w(this.getClass().toString(), "onBind()");
		return binder;
	}

}
