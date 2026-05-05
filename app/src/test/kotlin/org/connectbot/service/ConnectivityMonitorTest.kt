/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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
import android.net.Network
import android.net.wifi.WifiManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
class ConnectivityMonitorTest {

    @Mock
    lateinit var terminalManager: TerminalManager

    @Mock
    lateinit var connectivityManager: ConnectivityManager

    @Mock
    lateinit var wifiManager: WifiManager

    private lateinit var connectivityMonitor: ConnectivityMonitor

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(terminalManager.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        `when`(terminalManager.applicationContext).thenReturn(terminalManager)
        `when`(terminalManager.getSystemService(Context.WIFI_SERVICE)).thenReturn(wifiManager)
        `when`(wifiManager.createWifiLock(anyString())).thenReturn(mock(WifiManager.WifiLock::class.java))

        connectivityMonitor = ConnectivityMonitor(terminalManager, false)
    }

    @Test
    fun `onLost should notify terminalManager with IPs`() {
        val network = mock(Network::class.java)
        val ipAddresses = setOf("192.168.1.100")

        // Use reflection to populate allNetworks
        val allNetworksField = connectivityMonitor.javaClass.getDeclaredField("allNetworks")
        allNetworksField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val allNetworks = allNetworksField.get(connectivityMonitor) as ConcurrentHashMap<Network, ConnectivityMonitor.NetworkInfo>

        val networkInfo = ConnectivityMonitor.NetworkInfo(
            isConnected = true,
            ipAddresses = ipAddresses,
            networkId = "net1",
            networkType = 1,
        )
        allNetworks[network] = networkInfo

        // Trigger onLost via reflection on the private callback
        val callbackField = connectivityMonitor.javaClass.getDeclaredField("networkCallback")
        callbackField.isAccessible = true
        val callback = callbackField.get(connectivityMonitor) as ConnectivityManager.NetworkCallback

        callback.onLost(network)

        // Verify notification
        verify(terminalManager).onConnectivityLost(network, ipAddresses)
    }

    private fun anyString(): String = org.mockito.ArgumentMatchers.anyString()
}
