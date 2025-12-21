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

package org.connectbot.ui.screens.portforwardlist

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.transport.AbsTransport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class PortForwardListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: HostRepository
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var terminalManager: TerminalManager
    private lateinit var portForwardsFlow: MutableStateFlow<List<PortForward>>
    private lateinit var bridgesFlow: MutableStateFlow<List<TerminalBridge>>
    private lateinit var viewModel: PortForwardListViewModel

    private val testHostId = 1L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Plant a no-op tree for Timber
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                // No-op for tests
            }
        })

        repository = mock()
        savedStateHandle = mock()
        terminalManager = mock()
        portForwardsFlow = MutableStateFlow(emptyList())
        bridgesFlow = MutableStateFlow(emptyList())

        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(testHostId)
        whenever(repository.observePortForwardsForHost(testHostId)).thenReturn(portForwardsFlow)
        whenever(terminalManager.bridgesFlow).thenReturn(bridgesFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Timber.uprootAll()
    }

    private fun createViewModel(): PortForwardListViewModel {
        val vm = PortForwardListViewModel(savedStateHandle, repository, testDispatcher)
        vm.setTerminalManager(terminalManager)
        return vm
    }

    private fun createMockBridge(hostId: Long, connected: Boolean = true): TerminalBridge {
        val bridge = mock<TerminalBridge>()
        val host = Host(id = hostId, nickname = "test-host", protocol = "ssh", username = "user", hostname = "example.com", port = 22)
        val transport = mock<AbsTransport>()

        whenever(bridge.host).thenReturn(host)
        whenever(bridge.transport).thenReturn(transport)
        whenever(transport.isConnected()).thenReturn(connected)
        whenever(bridge.portForwards).thenReturn(emptyList())

        return bridge
    }

    private fun createTestPortForward(
        id: Long = 1L,
        nickname: String = "test-forward",
        type: String = "local",
        sourcePort: Int = 8080,
        destAddr: String = "localhost",
        destPort: Int = 80
    ): PortForward {
        return PortForward(
            id = id,
            hostId = testHostId,
            nickname = nickname,
            type = type,
            sourcePort = sourcePort,
            destAddr = destAddr,
            destPort = destPort
        )
    }

    @Test
    fun initialState_LoadsSuccessfully() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Should stop loading after initialization", state.isLoading)
        assertEquals("Initial port forwards should be empty", 0, state.portForwards.size)
        assertFalse("Initial hasLiveConnection should be false", state.hasLiveConnection)
        assertNull("Initial error should be null", state.error)
    }

    @Test
    fun loadPortForwards_WithNoConnection_LoadsSuccessfully() = runTest {
        val testPortForward = createTestPortForward()
        portForwardsFlow.value = listOf(testPortForward)

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Should stop loading", state.isLoading)
        assertEquals("Should have 1 port forward", 1, state.portForwards.size)
        assertFalse("Should not have live connection", state.hasLiveConnection)
        assertFalse("Port forward should be disabled", state.portForwards[0].isEnabled())
    }

    @Test
    fun loadPortForwards_WithActiveConnection_SyncsEnabledState() = runTest {
        val testPortForward = createTestPortForward()
        val enabledPortForward = createTestPortForward().apply {
            setEnabled(true)
        }

        portForwardsFlow.value = listOf(testPortForward)

        val bridge = createMockBridge(testHostId, connected = true)
        whenever(bridge.portForwards).thenReturn(listOf(enabledPortForward))
        bridgesFlow.value = listOf(bridge)

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Should stop loading", state.isLoading)
        assertEquals("Should have 1 port forward", 1, state.portForwards.size)
        assertTrue("Should have live connection", state.hasLiveConnection)
        assertTrue("Port forward should be enabled", state.portForwards[0].isEnabled())
    }

    @Test
    fun addPortForward_WithoutConnection_SavesToRepository() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addPortForward(
            nickname = "new-forward",
            type = "local",
            sourcePort = "9090",
            destination = "example.com:443"
        )
        advanceUntilIdle()

        val captor = argumentCaptor<PortForward>()
        verify(repository).savePortForward(captor.capture())

        val saved = captor.firstValue
        assertEquals("new-forward", saved.nickname)
        assertEquals("local", saved.type)
        assertEquals(9090, saved.sourcePort)
        assertEquals("example.com", saved.destAddr)
        assertEquals(443, saved.destPort)
        assertEquals(testHostId, saved.hostId)
    }

    @Test
    fun addPortForward_WithActiveConnection_AddsToTransport() = runTest {
        // Mock repository to return the saved port forward with an ID
        val savedPortForward = createTestPortForward(id = 100L, nickname = "new-forward", sourcePort = 9090, destAddr = "example.com", destPort = 443)
        whenever(repository.savePortForward(any())).thenReturn(savedPortForward)

        val transport = mock<AbsTransport>()
        whenever(transport.isConnected()).thenReturn(true)
        whenever(transport.addPortForward(any())).thenReturn(true)

        val bridge = createMockBridge(testHostId, connected = true)
        whenever(bridge.transport).thenReturn(transport)
        bridgesFlow.value = listOf(bridge)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addPortForward(
            nickname = "new-forward",
            type = "local",
            sourcePort = "9090",
            destination = "example.com:443"
        )
        advanceUntilIdle()

        verify(repository).savePortForward(any())
        verify(transport).addPortForward(eq(savedPortForward))
    }

    @Test
    fun addPortForward_WithoutActiveConnection_DoesNotAddToTransport() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addPortForward(
            nickname = "new-forward",
            type = "local",
            sourcePort = "9090",
            destination = "example.com:443"
        )
        advanceUntilIdle()

        verify(repository).savePortForward(any())
        // No transport interaction since there's no active connection
    }

    @Test
    fun updatePortForward_WithoutConnection_SavesToRepository() = runTest {
        val existingPortForward = createTestPortForward()

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updatePortForward(
            portForward = existingPortForward,
            nickname = "updated-forward",
            type = "remote",
            sourcePort = "8888",
            destination = "newhost.com:22"
        )
        advanceUntilIdle()

        val captor = argumentCaptor<PortForward>()
        verify(repository).savePortForward(captor.capture())

        val updated = captor.firstValue
        assertEquals("updated-forward", updated.nickname)
        assertEquals("remote", updated.type)
        assertEquals(8888, updated.sourcePort)
        assertEquals("newhost.com", updated.destAddr)
        assertEquals(22, updated.destPort)
        assertEquals(testHostId, updated.hostId)
        assertEquals(existingPortForward.id, updated.id)
    }

    @Test
    fun updatePortForward_WithActiveConnection_UpdatesTransport() = runTest {
        val existingPortForward = createTestPortForward()
        val bridgePortForward = createTestPortForward()

        val transport = mock<AbsTransport>()
        whenever(transport.isConnected()).thenReturn(true)
        whenever(transport.removePortForward(any())).thenReturn(true)
        whenever(transport.addPortForward(any())).thenReturn(true)

        val bridge = createMockBridge(testHostId, connected = true)
        whenever(bridge.transport).thenReturn(transport)
        whenever(bridge.portForwards).thenReturn(listOf(bridgePortForward))
        bridgesFlow.value = listOf(bridge)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updatePortForward(
            portForward = existingPortForward,
            nickname = "updated-forward",
            type = "remote",
            sourcePort = "8888",
            destination = "newhost.com:22"
        )
        advanceUntilIdle()

        verify(repository).savePortForward(any())
        verify(transport).removePortForward(eq(bridgePortForward))
        verify(transport).addPortForward(any())
    }

    @Test
    fun deletePortForward_WithoutConnection_DeletesFromRepository() = runTest {
        val testPortForward = createTestPortForward()

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deletePortForward(testPortForward)
        advanceUntilIdle()

        verify(repository).deletePortForward(eq(testPortForward))
    }

    @Test
    fun deletePortForward_WithActiveConnection_RemovesFromTransport() = runTest {
        val testPortForward = createTestPortForward()
        val bridgePortForward = createTestPortForward()

        val transport = mock<AbsTransport>()
        whenever(transport.isConnected()).thenReturn(true)
        whenever(transport.removePortForward(any())).thenReturn(true)

        val bridge = createMockBridge(testHostId, connected = true)
        whenever(bridge.transport).thenReturn(transport)
        whenever(bridge.portForwards).thenReturn(listOf(bridgePortForward))
        bridgesFlow.value = listOf(bridge)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deletePortForward(testPortForward)
        advanceUntilIdle()

        verify(repository).deletePortForward(eq(testPortForward))
        verify(transport).removePortForward(eq(bridgePortForward))
    }

    @Test
    fun enablePortForward_WithActiveConnection_EnablesAndRefreshes() = runTest {
        val testPortForward = createTestPortForward()
        val bridgePortForward = createTestPortForward()

        portForwardsFlow.value = listOf(testPortForward)

        val bridge = createMockBridge(testHostId, connected = true)
        whenever(bridge.portForwards).thenReturn(listOf(bridgePortForward))
        whenever(bridge.enablePortForward(bridgePortForward)).thenReturn(true)
        bridgesFlow.value = listOf(bridge)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.enablePortForward(testPortForward)
        advanceUntilIdle()

        verify(bridge).enablePortForward(eq(bridgePortForward))
    }

    @Test
    fun disablePortForward_WithActiveConnection_DisablesAndRefreshes() = runTest {
        val testPortForward = createTestPortForward()
        val bridgePortForward = createTestPortForward()

        portForwardsFlow.value = listOf(testPortForward)

        val bridge = createMockBridge(testHostId, connected = true)
        whenever(bridge.portForwards).thenReturn(listOf(bridgePortForward))
        whenever(bridge.disablePortForward(bridgePortForward)).thenReturn(true)
        bridgesFlow.value = listOf(bridge)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.disablePortForward(testPortForward)
        advanceUntilIdle()

        verify(bridge).disablePortForward(eq(bridgePortForward))
    }

    @Test
    fun enablePortForward_WithoutConnection_SetsError() = runTest {
        val testPortForward = createTestPortForward()

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.enablePortForward(testPortForward)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("No active connection for this host", state.error)
    }

    @Test
    fun parseDestination_WithValidHostAndPort() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addPortForward(
            nickname = "test",
            type = "local",
            sourcePort = "8080",
            destination = "example.com:443"
        )
        advanceUntilIdle()

        val captor = argumentCaptor<PortForward>()
        verify(repository).savePortForward(captor.capture())

        assertEquals("example.com", captor.firstValue.destAddr)
        assertEquals(443, captor.firstValue.destPort)
    }

    @Test
    fun parseDestination_WithOnlyHost() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addPortForward(
            nickname = "test",
            type = "local",
            sourcePort = "8080",
            destination = "example.com"
        )
        advanceUntilIdle()

        val captor = argumentCaptor<PortForward>()
        verify(repository).savePortForward(captor.capture())

        assertEquals("example.com", captor.firstValue.destAddr)
        assertEquals(0, captor.firstValue.destPort)
    }

    @Test
    fun parseDestination_WithInvalidPort() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addPortForward(
            nickname = "test",
            type = "local",
            sourcePort = "8080",
            destination = "example.com:invalid"
        )
        advanceUntilIdle()

        val captor = argumentCaptor<PortForward>()
        verify(repository).savePortForward(captor.capture())

        assertEquals("example.com", captor.firstValue.destAddr)
        assertEquals(0, captor.firstValue.destPort)
    }
}
