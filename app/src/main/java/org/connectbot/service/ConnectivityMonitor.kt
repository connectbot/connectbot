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

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import timber.log.Timber

/**
 * Tracks network availability and IP addresses for connection resilience.
 *
 * @author Kenny Root
 */
class ConnectivityMonitor(
    private val terminalManager: TerminalManager,
    private var lockingWifi: Boolean
) {
    private val connectivityManager: ConnectivityManager = terminalManager.getSystemService(
        Context.CONNECTIVITY_SERVICE
    ) as ConnectivityManager

    private val wifiManager: WifiManager = terminalManager.applicationContext.getSystemService(
        Context.WIFI_SERVICE
    ) as WifiManager

    private val wifiLock: WifiLock = wifiManager.createWifiLock(TAG)

    private var networkRef = 0
    private val lock = Any()

    @Volatile
    private var currentNetworkInfo: NetworkInfo? = null

    /**
     * Network information containing connection state and IP addresses.
     */
    data class NetworkInfo(
        val isConnected: Boolean,
        val ipAddresses: Set<String>,
        val networkId: String,
        val networkType: Int
    )

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Timber.i("Network available: $network")
            updateNetworkInfo(network)
        }

        override fun onLost(network: Network) {
            Timber.i("Network lost: $network")
            currentNetworkInfo = null
            terminalManager.onConnectivityLost()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            Timber.i("Link properties changed for network: $network")
            updateNetworkInfo(network, linkProperties)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            Timber.i("Network capabilities changed: $network")
            // Update network info to reflect capability changes
            updateNetworkInfo(network)
        }
    }

    /**
     * Initialize network monitoring by registering the NetworkCallback.
     * Must be called before using this monitor.
     */
    fun init() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Initialize current network state
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null) {
            updateNetworkInfo(activeNetwork)
        }
    }

    /**
     * Clean up resources and unregister the network callback.
     */
    fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: IllegalArgumentException) {
            // Callback was not registered, ignore
            Timber.w(e, "Failed to unregister network callback")
        }

        if (wifiLock.isHeld) {
            wifiLock.release()
        }
    }

    /**
     * Get the current network information including IP addresses.
     *
     * @return current NetworkInfo or null if not connected
     */
    fun getCurrentNetworkInfo(): NetworkInfo? = currentNetworkInfo

    /**
     * Update network info based on current active network.
     */
    private fun updateNetworkInfo(network: Network, linkProperties: LinkProperties? = null) {
        val props = linkProperties ?: connectivityManager.getLinkProperties(network)
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        if (props == null || capabilities == null) {
            Timber.w("Could not get link properties or capabilities for network: $network")
            return
        }

        val ipAddresses = extractIpAddresses(props)
        val networkId = network.toString()
        val networkType = getNetworkType(capabilities)

        val wasConnected = currentNetworkInfo?.isConnected == true
        val newNetworkInfo = NetworkInfo(
            isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            ipAddresses = ipAddresses,
            networkId = networkId,
            networkType = networkType
        )

        currentNetworkInfo = newNetworkInfo

        Timber.i("Network info updated: ${ipAddresses.size} IPs, type=$networkType, id=$networkId")

        // Notify terminal manager if we just became connected
        if (!wasConnected && newNetworkInfo.isConnected) {
            terminalManager.onConnectivityRestored()
        }
    }

    /**
     * Extract IP addresses from LinkProperties.
     */
    private fun extractIpAddresses(linkProperties: LinkProperties): Set<String> = linkProperties.linkAddresses
        .mapNotNull { it.address.hostAddress }
        .toSet()

    /**
     * Determine network type from NetworkCapabilities.
     */
    private fun getNetworkType(capabilities: NetworkCapabilities): Int = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
            NetworkCapabilities.TRANSPORT_WIFI

        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
            NetworkCapabilities.TRANSPORT_CELLULAR

        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
            NetworkCapabilities.TRANSPORT_ETHERNET

        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ->
            NetworkCapabilities.TRANSPORT_VPN

        else -> -1
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
            Timber.d("WiFi lock acquired (ref count: $networkRef)")
        }
    }

    private fun releaseWifiLockIfNecessaryLocked() {
        if (networkRef == 0 && wifiLock.isHeld) {
            wifiLock.release()
            Timber.d("WiFi lock released")
        }
    }

    companion object {
        private const val TAG = "CB.ConnectivityMonitor"
    }
}
