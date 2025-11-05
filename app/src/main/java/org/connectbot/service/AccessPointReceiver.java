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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

/**
 * BroadcastReceiver to monitor WiFi hotspot state changes for faster response times.
 * This provides immediate notification when the hotspot state changes, complementing
 * the polling timer fallback for devices that don't reliably send these broadcasts.
 *
 * @author ConnectBot Team
 */
public class AccessPointReceiver extends BroadcastReceiver {
	private static final String TAG = "CB.AccessPointReceiver";

	// WiFi AP state change action (may not be reliable on all devices)
	private static final String WIFI_AP_STATE_CHANGED_ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED";

	final private TerminalManager mTerminalManager;

	public AccessPointReceiver(TerminalManager manager) {
		mTerminalManager = manager;

		// Register for WiFi AP state changes
		// Note: This action is not officially part of the Android API and may not work on all devices
		// That's why we keep the polling timer as a reliable fallback
		final IntentFilter filter = new IntentFilter();
		filter.addAction(WIFI_AP_STATE_CHANGED_ACTION);
		
		try {
			manager.registerReceiver(this, filter);
			Log.d(TAG, "AccessPointReceiver registered for WIFI_AP_STATE_CHANGED");
		} catch (Exception e) {
			// Some devices might not support this broadcast
			Log.w(TAG, "Failed to register for WIFI_AP_STATE_CHANGED: " + e.getMessage());
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();

		if (!WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
			Log.w(TAG, "onReceive() called with unexpected action: " + action);
			return;
		}

		Log.d(TAG, "WiFi AP state change detected via broadcast - triggering immediate check");
		
		// Trigger immediate AP state check for faster response
		// The existing checkAccessPointStateChange() method handles all the logic
		mTerminalManager.checkAccessPointStateChange();
	}

	/**
	 * Cleanup and unregister the receiver
	 */
	public void cleanup() {
		try {
			mTerminalManager.unregisterReceiver(this);
			Log.d(TAG, "AccessPointReceiver unregistered");
		} catch (Exception e) {
			// Receiver might not have been registered successfully
			Log.w(TAG, "Failed to unregister AccessPointReceiver: " + e.getMessage());
		}
	}
}