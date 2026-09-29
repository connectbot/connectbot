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

package org.connectbot.ui.screens.console

import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.sshlib.PortForwarder
import org.connectbot.transport.AbsTransport
import org.connectbot.util.HostConstants
import org.connectbot.util.NotificationPermissionHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionDetailsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
    private val manager = mock(TerminalManager::class.java)
    private val bridges = MutableStateFlow<List<TerminalBridge>>(emptyList())
    private lateinit var viewModel: ConsoleViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        `when`(manager.bridgesFlow).thenReturn(bridges)
        `when`(manager.hostStatusChangedFlow).thenReturn(MutableSharedFlow())
        viewModel = ConsoleViewModel(SavedStateHandle(), dispatchers, mock(SharedPreferences::class.java), mock(NotificationPermissionHelper::class.java))
        viewModel.setTerminalManager(manager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun bridge(id: Long): TerminalBridge {
        val bridge = mock(TerminalBridge::class.java)
        `when`(bridge.host).thenReturn(Host(id = id, hostname = "server$id"))
        `when`(bridge.progressState).thenReturn(MutableStateFlow(null))
        `when`(bridge.networkStatusMessages).thenReturn(MutableSharedFlow())
        return bridge
    }

    @Test
    fun noSelectedBridge_returnsNoDetails() = runTest {
        advanceUntilIdle()
        assertNull(viewModel.connectionDetails())
    }

    @Test
    fun switchingSessions_returnsCurrentHostAndTransport() = runTest {
        val first = bridge(1)
        val second = bridge(2)
        val transport = mock(AbsTransport::class.java)
        `when`(second.transport).thenReturn(transport)
        `when`(transport.getLocalIpAddress()).thenReturn("192.0.2.2")
        bridges.value = listOf(first, second)
        advanceUntilIdle()
        assertEquals("server1", viewModel.connectionDetails()!!.host.hostname)
        viewModel.selectBridge(1)
        assertEquals("server2", viewModel.connectionDetails()!!.host.hostname)
        assertEquals("192.0.2.2", viewModel.connectionDetails()!!.localAddress)
    }

    @Test
    fun connectionStateChanges_areReflectedInSnapshot() = runTest {
        val bridge = bridge(1)
        bridges.value = listOf(bridge)
        advanceUntilIdle()
        assertEquals(ConnectionStatus.CONNECTED, viewModel.connectionDetails()!!.status)
        `when`(bridge.isConnecting).thenReturn(true)
        assertEquals(ConnectionStatus.CONNECTING, viewModel.connectionDetails()!!.status)
        `when`(bridge.isDisconnected).thenReturn(true)
        assertEquals(ConnectionStatus.DISCONNECTED, viewModel.connectionDetails()!!.status)
    }

    @Test
    fun forwardingSnapshot_usesActualPortAndLiveStatusWithoutChangingConfiguration() = runTest {
        val bridge = bridge(1)
        val transport = mock(AbsTransport::class.java)
        val forwarder = mock(PortForwarder::class.java)
        val forward = PortForward(hostId = 1, nickname = "Database", type = HostConstants.PORTFORWARD_REMOTE, sourcePort = 0, destAddr = "localhost", destPort = 5432)
        forward.setIdentifier(forwarder)
        // The library's status takes precedence over the app's cached flag.
        forward.setEnabled(false)
        `when`(bridge.transport).thenReturn(transport)
        `when`(transport.getPortForwards()).thenReturn(listOf(forward))
        `when`(forwarder.boundPort).thenReturn(41234)
        `when`(forwarder.isActive).thenReturn(true)
        bridges.value = listOf(bridge)
        advanceUntilIdle()

        val active = viewModel.connectionDetails()!!.portForwards.single()
        assertEquals(41234, active.boundPort)
        assertEquals(0, active.configuredPort)
        assertEquals(0, forward.sourcePort)
        assertTrue(active.isActive)

        forward.setEnabled(true)
        `when`(forwarder.isActive).thenReturn(false)
        assertFalse(viewModel.connectionDetails()!!.portForwards.single().isActive)

        `when`(forwarder.isActive).thenReturn(true)
        `when`(bridge.isDisconnected).thenReturn(true)
        assertFalse(viewModel.connectionDetails()!!.portForwards.single().isActive)

        forward.setIdentifier(null)
        val unbound = viewModel.connectionDetails()!!.portForwards.single()
        assertNull(unbound.boundPort)
        assertFalse(unbound.isActive)
    }
}
