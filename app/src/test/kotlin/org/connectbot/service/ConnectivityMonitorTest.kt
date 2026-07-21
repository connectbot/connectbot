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
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
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

    @Test
    fun `monitor includes VPN networks`() {
        `when`(connectivityManager.allNetworks).thenReturn(emptyArray())
        connectivityMonitor.init()

        val request = ArgumentCaptor.forClass(NetworkRequest::class.java)
        verify(connectivityManager).registerNetworkCallback(request.capture(), any(ConnectivityManager.NetworkCallback::class.java))
        assertFalse(request.value.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
    }

    @Test
    fun `default VPN validation update releases queued reconnects`() {
        val network = mock(Network::class.java)
        val initial = mock(NetworkCapabilities::class.java)
        `when`(initial.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(true)
        `when`(initial.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true)
        `when`(connectivityManager.getNetworkCapabilities(network)).thenReturn(initial)
        `when`(connectivityManager.getLinkProperties(network)).thenReturn(LinkProperties())
        val callback = defaultCallback()
        callback.onAvailable(network)
        assertFalse(connectivityMonitor.getCurrentNetworkInfo()!!.isConnected)

        // The event may be newer than a synchronous ConnectivityManager query.
        val validated = mock(NetworkCapabilities::class.java)
        `when`(validated.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).thenReturn(true)
        `when`(validated.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(true)
        `when`(validated.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true)
        callback.onCapabilitiesChanged(network, validated)

        assertTrue(connectivityMonitor.getCurrentNetworkInfo()!!.isConnected)
        assertEquals(NetworkCapabilities.TRANSPORT_VPN, connectivityMonitor.getCurrentNetworkInfo()!!.networkType)
        verify(terminalManager).onConnectivityRestored()
    }

    @Test
    fun `default VPN link update records tunnel addresses`() {
        val network = mock(Network::class.java)
        val capabilities = mock(NetworkCapabilities::class.java)
        `when`(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(true)
        `when`(connectivityManager.getNetworkCapabilities(network)).thenReturn(capabilities)
        `when`(connectivityManager.getLinkProperties(network)).thenReturn(LinkProperties())
        val callback = defaultCallback()
        callback.onAvailable(network)
        val address = mock(LinkAddress::class.java)
        `when`(address.address).thenReturn(InetAddress.getByName("172.16.1.101"))
        val properties = mock(LinkProperties::class.java)
        `when`(properties.linkAddresses).thenReturn(listOf(address))

        callback.onLinkPropertiesChanged(network, properties)

        assertEquals(setOf("172.16.1.101"), connectivityMonitor.getCurrentNetworkInfo()!!.ipAddresses)
    }

    private fun defaultCallback(): ConnectivityManager.NetworkCallback {
        val field = connectivityMonitor.javaClass.getDeclaredField("defaultNetworkCallback")
        field.isAccessible = true
        return field.get(connectivityMonitor) as ConnectivityManager.NetworkCallback
    }

    @Test
    fun `init should query only the active network`() {
        val activeNetwork = mock(Network::class.java)
        `when`(connectivityManager.allNetworks).thenReturn(emptyArray())
        `when`(connectivityManager.activeNetwork).thenReturn(activeNetwork)

        connectivityMonitor.init()

        verify(connectivityManager).activeNetwork
        verify(connectivityManager, never()).allNetworks
    }

    private fun anyString(): String = org.mockito.ArgumentMatchers.anyString()
}
