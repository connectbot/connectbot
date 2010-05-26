/**
 *
 */
package org.connectbot.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.util.Log;

/**
 * @author kroot
 *
 */
public class ConnectivityReceiver extends BroadcastReceiver {
	private static final String TAG = "ConnectBot.ConnectivityManager";

	private boolean mIsConnected = false;

	final private TerminalManager mTerminalManager;

	final private WifiLock mWifiLock;

	private int mNetworkRef = 0;

	private boolean mLockingWifi;

	public ConnectivityReceiver(TerminalManager manager, boolean lockingWifi) {
		mTerminalManager = manager;

		final ConnectivityManager cm =
				(ConnectivityManager) manager.getSystemService(Context.CONNECTIVITY_SERVICE);

		final WifiManager wm = (WifiManager) manager.getSystemService(Context.WIFI_SERVICE);
		mWifiLock = wm.createWifiLock(TAG);

		final NetworkInfo info = cm.getActiveNetworkInfo();
		if (info != null) {
			mIsConnected = (info.getState() == State.CONNECTED);
		}

		mLockingWifi = lockingWifi;

		final IntentFilter filter = new IntentFilter();
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		manager.registerReceiver(this, filter);
	}

	/* (non-Javadoc)
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();

		if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            Log.w(TAG, "onReceived() called: " + intent);
            return;
		}

		boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
		boolean isFailover = intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);

		Log.d(TAG, "onReceived() called; noConnectivity? " + noConnectivity + "; isFailover? " + isFailover);

		if (noConnectivity && !isFailover && mIsConnected) {
			mIsConnected = false;
			mTerminalManager.onConnectivityLost();
		} else if (!mIsConnected) {
			NetworkInfo info = (NetworkInfo) intent.getExtras()
					.get(ConnectivityManager.EXTRA_NETWORK_INFO);

			if (mIsConnected = (info.getState() == State.CONNECTED)) {
				mTerminalManager.onConnectivityRestored();
			}
		}
	}

	/**
	 *
	 */
	public void cleanup() {
		if (mWifiLock.isHeld())
			mWifiLock.release();

		mTerminalManager.unregisterReceiver(this);
	}

	/**
	 * Increase the number of things using the network. Acquire a Wifi lock if necessary.
	 */
	public void incRef() {
		synchronized (this) {
			acquireWifiLockIfNecessary();

			mNetworkRef  += 1;
		}
	}

	public void decRef() {
		synchronized (this) {
			mNetworkRef -= 1;

			releaseWifiLockIfNecessary();
		}
	}

	/**
	 * @param mLockingWifi
	 */
	public void setWantWifiLock(boolean lockingWifi) {
		synchronized (this) {
			mLockingWifi = lockingWifi;

			if (mLockingWifi) {
				acquireWifiLockIfNecessary();
			} else if (!mLockingWifi) {
				releaseWifiLockIfNecessary();
			}
		}
	}

	/**
	 *
	 */
	private void acquireWifiLockIfNecessary() {
		synchronized (this) {
			if (mLockingWifi && mNetworkRef > 0 && !mWifiLock.isHeld()) {
				mWifiLock.acquire();
			}
		}
	}

	/**
	 *
	 */
	private void releaseWifiLockIfNecessary() {
		if (mNetworkRef == 0 && mWifiLock.isHeld()) {
			mWifiLock.release();
		}
	}

	/**
	 * @return whether we're connected to a network
	 */
	public boolean isConnected() {
		return mIsConnected;
	}
}
