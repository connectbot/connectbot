/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

package org.connectbot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.util.Log

/**
 * Broadcast receiver that monitors network connectivity changes and manages WiFi locks.
 *
 * @author kroot
 */
class ConnectivityReceiver(
    private val terminalManager: TerminalManager,
    lockingWifi: Boolean
) : BroadcastReceiver() {

    private var isConnected = false
    private val wifiLock: WifiLock
    private var networkRef = 0
    private var lockingWifi: Boolean
    private val lock = Any()

    init {
        val cm = terminalManager.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = terminalManager.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(TAG)

        val info = cm.activeNetworkInfo
        if (info != null) {
            isConnected = info.state == NetworkInfo.State.CONNECTED
        }

        this.lockingWifi = lockingWifi

        val filter = IntentFilter().apply {
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        terminalManager.registerReceiver(this, filter)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action != ConnectivityManager.CONNECTIVITY_ACTION) {
            Log.w(TAG, "onReceived() called: $intent")
            return
        }

        val noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)
        val isFailover = intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false)

        Log.d(TAG, "onReceived() called; noConnectivity? $noConnectivity; isFailover? $isFailover")

        if (noConnectivity && !isFailover && isConnected) {
            isConnected = false
            terminalManager.onConnectivityLost()
        } else if (!isConnected) {
            val info = intent.extras?.get(ConnectivityManager.EXTRA_NETWORK_INFO) as? NetworkInfo

            isConnected = info?.state == NetworkInfo.State.CONNECTED
            if (isConnected) {
                terminalManager.onConnectivityRestored()
            }
        }
    }

    /**
     * Clean up resources and unregister the receiver.
     */
    fun cleanup() {
        if (wifiLock.isHeld) {
            wifiLock.release()
        }

        terminalManager.unregisterReceiver(this)
    }

    /**
     * Increase the number of things using the network. Acquire a Wi-Fi lock
     * if necessary.
     */
    fun incRef() {
        synchronized(lock) {
            networkRef += 1
            acquireWifiLockIfNecessaryLocked()
        }
    }

    /**
     * Decrease the number of things using the network. Release the Wi-Fi lock
     * if necessary.
     */
    fun decRef() {
        synchronized(lock) {
            networkRef -= 1
            releaseWifiLockIfNecessaryLocked()
        }
    }

    /**
     * Set whether we want to hold a WiFi lock when connected.
     *
     * @param lockingWifi true to lock WiFi when connected
     */
    fun setWantWifiLock(lockingWifi: Boolean) {
        synchronized(lock) {
            this.lockingWifi = lockingWifi

            if (lockingWifi) {
                acquireWifiLockIfNecessaryLocked()
            } else {
                releaseWifiLockIfNecessaryLocked()
            }
        }
    }

    private fun acquireWifiLockIfNecessaryLocked() {
        if (lockingWifi && networkRef > 0 && !wifiLock.isHeld) {
            wifiLock.acquire()
        }
    }

    private fun releaseWifiLockIfNecessaryLocked() {
        if (networkRef == 0 && wifiLock.isHeld) {
            wifiLock.release()
        }
    }

    /**
     * Check whether we're currently connected to a network.
     *
     * @return true if connected to a network
     */
    fun isConnected(): Boolean = isConnected

    companion object {
        private const val TAG = "CB.ConnectivityManager"
    }
}
