/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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

package org.connectbot.ui.screens.hostlist

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.ServiceError
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.transport.AbsTransport
import org.connectbot.util.DiscoveredSshServer
import org.connectbot.util.PreferenceConstants
import org.connectbot.util.SshDiscoveryEvent
import org.connectbot.util.SshServiceDiscovery
import org.connectbot.util.TailscaleNetworkDetector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for HostListViewModel, focusing on sort order preference persistence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HostListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )
    private lateinit var context: Context
    private lateinit var repository: HostRepository
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var hostsFlow: MutableStateFlow<List<Host>>
    private lateinit var hostsSortedByColorFlow: MutableStateFlow<List<Host>>
    private lateinit var portForwardsFlow: MutableStateFlow<List<PortForward>>
    private lateinit var sshServiceDiscovery: SshServiceDiscovery
    private lateinit var tailscaleNetworkDetector: TailscaleNetworkDetector

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mock()
        repository = mock()
        sharedPreferences = mock()
        editor = mock()
        hostsFlow = MutableStateFlow(emptyList())
        hostsSortedByColorFlow = MutableStateFlow(emptyList())
        portForwardsFlow = MutableStateFlow(emptyList())
        sshServiceDiscovery = mock()
        tailscaleNetworkDetector = mock()

        whenever(repository.observeHosts()).thenReturn(hostsFlow)
        whenever(repository.observeHostsSortedByColor()).thenReturn(hostsSortedByColorFlow)
        whenever(repository.observeAllPortForwards()).thenReturn(portForwardsFlow)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putBoolean(any(), any())).thenReturn(editor)
        whenever(sshServiceDiscovery.discover()).thenReturn(emptyFlow())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(sortedByColor: Boolean = false): HostListViewModel {
        whenever(sharedPreferences.getBoolean(PreferenceConstants.SORT_BY_COLOR, false))
            .thenReturn(sortedByColor)
        return HostListViewModel(
            context,
            repository,
            dispatchers,
            sharedPreferences,
            sshServiceDiscovery,
            tailscaleNetworkDetector,
        )
    }

    private fun createTerminalManager(bridges: List<TerminalBridge> = emptyList()): TerminalManager {
        val terminalManager = mock<TerminalManager>()
        whenever(terminalManager.bridgesFlow).thenReturn(MutableStateFlow(bridges))
        whenever(terminalManager.disconnectedFlow).thenReturn(MutableStateFlow(emptyList()))
        whenever(terminalManager.hostStatusChangedFlow).thenReturn(MutableSharedFlow())
        whenever(terminalManager.serviceErrors).thenReturn(MutableSharedFlow<ServiceError>())
        whenever(terminalManager.pendingStartupKeyPrompts).thenReturn(MutableStateFlow(emptyList()))
        return terminalManager
    }

    private fun createMockBridge(hostId: Long, portForwards: List<PortForward> = emptyList(), connected: Boolean = true): TerminalBridge {
        val bridge = mock<TerminalBridge>()
        val host = Host(id = hostId, nickname = "test-host", protocol = "ssh", username = "user", hostname = "example.com", port = 22)
        val transport = mock<AbsTransport>()

        whenever(bridge.host).thenReturn(host)
        whenever(bridge.transport).thenReturn(transport)
        whenever(transport.isConnected()).thenReturn(connected)
        whenever(bridge.portForwards).thenReturn(portForwards)

        return bridge
    }

    private fun createTestPortForward(
        id: Long = 1L,
        hostId: Long = 42L,
        nickname: String = "test-forward",
    ): PortForward = PortForward(
        id = id,
        hostId = hostId,
        nickname = nickname,
        type = "local",
        sourceAddr = "localhost",
        sourcePort = 8080,
        destAddr = "localhost",
        destPort = 80,
    )

    /**
     * Tests that sort order preference is loaded from SharedPreferences on initialization.
     *
     * Scenario: User previously selected "sort by color" and restarts the app.
     * Expected: The ViewModel initializes with sortedByColor=true.
     */
    @Test
    fun init_loadsSortOrderFromPreferences_whenSortedByColorTrue() = runTest {
        val viewModel = createViewModel(sortedByColor = true)
        advanceUntilIdle()

        assertTrue("sortedByColor should be true from preferences", viewModel.uiState.value.sortedByColor)
        verify(sharedPreferences).getBoolean(PreferenceConstants.SORT_BY_COLOR, false)
    }

    /**
     * Tests that sort order defaults to false when preference is not set.
     *
     * Scenario: Fresh install or preference never set.
     * Expected: The ViewModel initializes with sortedByColor=false.
     */
    @Test
    fun init_loadsSortOrderFromPreferences_whenSortedByColorFalse() = runTest {
        val viewModel = createViewModel(sortedByColor = false)
        advanceUntilIdle()

        assertFalse("sortedByColor should be false from preferences", viewModel.uiState.value.sortedByColor)
        verify(sharedPreferences).getBoolean(PreferenceConstants.SORT_BY_COLOR, false)
    }

    /**
     * Tests that toggleSortOrder persists the new value to SharedPreferences.
     *
     * Scenario: User toggles sort order from name to color.
     * Expected: The preference is saved to SharedPreferences.
     */
    @Test
    fun toggleSortOrder_persistsPreference_whenTogglingToTrue() = runTest {
        val viewModel = createViewModel(sortedByColor = false)
        advanceUntilIdle()

        viewModel.toggleSortOrder()
        advanceUntilIdle()

        assertTrue("sortedByColor should be true after toggle", viewModel.uiState.value.sortedByColor)
        verify(editor).putBoolean(PreferenceConstants.SORT_BY_COLOR, true)
        verify(editor).apply()
    }

    /**
     * Tests that toggleSortOrder persists false when toggling back to name sort.
     *
     * Scenario: User toggles sort order from color back to name.
     * Expected: The preference is saved to SharedPreferences with false.
     */
    @Test
    fun toggleSortOrder_persistsPreference_whenTogglingToFalse() = runTest {
        val viewModel = createViewModel(sortedByColor = true)
        advanceUntilIdle()

        viewModel.toggleSortOrder()
        advanceUntilIdle()

        assertFalse("sortedByColor should be false after toggle", viewModel.uiState.value.sortedByColor)
        verify(editor).putBoolean(PreferenceConstants.SORT_BY_COLOR, false)
        verify(editor).apply()
    }

    /**
     * Tests that toggling sort order switches the data source.
     *
     * Scenario: User toggles to sort by color.
     * Expected: The ViewModel observes hosts sorted by color from the repository.
     */
    @Test
    fun toggleSortOrder_switchesToColorSortedHosts() = runTest {
        val viewModel = createViewModel(sortedByColor = false)
        advanceUntilIdle()

        viewModel.toggleSortOrder()
        advanceUntilIdle()

        verify(repository).observeHostsSortedByColor()
    }

    /**
     * Tests that the ViewModel uses alphabetical sort when sortedByColor is false.
     *
     * Scenario: Default state or user prefers alphabetical sorting.
     * Expected: The ViewModel observes hosts sorted alphabetically from the repository.
     */
    @Test
    fun init_usesAlphabeticalSort_whenSortedByColorFalse() = runTest {
        createViewModel(sortedByColor = false)
        advanceUntilIdle()

        verify(repository).observeHosts()
    }

    /**
     * Tests that the ViewModel uses color sort on init when preference is true.
     *
     * Scenario: User previously selected color sort.
     * Expected: The ViewModel observes hosts sorted by color from the repository.
     */
    @Test
    fun init_usesColorSort_whenSortedByColorTrue() = runTest {
        createViewModel(sortedByColor = true)
        advanceUntilIdle()

        verify(repository).observeHostsSortedByColor()
    }

    @Test
    fun startSshDiscovery_addsResolvedServersToUiState() = runTest {
        val server = DiscoveredSshServer(
            key = "Raspberry Pi\u0000_ssh._tcp",
            serviceName = "Raspberry Pi",
            hostname = "raspberrypi.local",
            port = 22,
            username = "pi",
        )
        whenever(sshServiceDiscovery.discover()).thenReturn(
            flowOf(SshDiscoveryEvent.Found(server)),
        )
        val viewModel = createViewModel()

        viewModel.startSshDiscovery()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showSshDiscovery)
        assertEquals(listOf(server), viewModel.uiState.value.discoveredSshServers)
        assertFalse(viewModel.uiState.value.isDiscoveringSshServers)
    }

    @Test
    fun refreshTailscaleState_exposesActiveTailscaleNetwork() = runTest {
        whenever(tailscaleNetworkDetector.isActive()).thenReturn(true)
        val viewModel = createViewModel()

        viewModel.refreshTailscaleState()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isTailscaleActive)
    }

    @Test
    fun deleteHost_disconnectsActiveBridgeBeforeDeletingHost() = runTest {
        val viewModel = createViewModel()
        val terminalManager = createTerminalManager()
        val host = Host(id = 42L, nickname = "test", hostname = "example.com")
        viewModel.setTerminalManager(terminalManager)
        advanceUntilIdle()

        viewModel.deleteHost(host)
        advanceUntilIdle()

        val inOrder = inOrder(terminalManager, repository)
        inOrder.verify(terminalManager).disconnectHost(host.id)
        inOrder.verify(repository).deleteHost(host)
    }

    /**
     * Tests that port forwards are grouped by host and marked disabled without a connection.
     *
     * Scenario: Two hosts have configured forwards but neither is connected.
     * Expected: The uiState map contains both hosts' forwards, all disabled.
     */
    @Test
    fun portForwards_groupedByHostAndDisabled_whenNoConnection() = runTest {
        val viewModel = createViewModel()
        viewModel.setTerminalManager(createTerminalManager())
        portForwardsFlow.value = listOf(
            createTestPortForward(id = 1L, hostId = 42L),
            createTestPortForward(id = 2L, hostId = 42L, nickname = "second"),
            createTestPortForward(id = 3L, hostId = 7L, nickname = "other-host"),
        )
        advanceUntilIdle()

        val portForwards = viewModel.uiState.value.portForwards
        assertEquals("Forwards should be grouped into two hosts", 2, portForwards.size)
        assertEquals("Host 42 should have two forwards", 2, portForwards[42L]?.size)
        assertEquals("Host 7 should have one forward", 1, portForwards[7L]?.size)
        assertTrue(
            "All forwards should be disabled without a connection",
            portForwards.values.flatten().none { it.isEnabled() },
        )
    }

    /**
     * Tests that enabled state is read from the live bridge for connected hosts.
     *
     * Scenario: Host 42 is connected and one of its two forwards is enabled on the bridge.
     * Expected: The uiState copies reflect the bridge's enabled flags.
     */
    @Test
    fun portForwards_reflectBridgeEnabledState_whenConnected() = runTest {
        val enabledForward = createTestPortForward(id = 1L, hostId = 42L).apply { setEnabled(true) }
        val disabledForward = createTestPortForward(id = 2L, hostId = 42L, nickname = "second")
        val bridge = createMockBridge(hostId = 42L, portForwards = listOf(enabledForward, disabledForward))

        val viewModel = createViewModel()
        viewModel.setTerminalManager(createTerminalManager(bridges = listOf(bridge)))
        portForwardsFlow.value = listOf(
            createTestPortForward(id = 1L, hostId = 42L),
            createTestPortForward(id = 2L, hostId = 42L, nickname = "second"),
        )
        advanceUntilIdle()

        val forwards = viewModel.uiState.value.portForwards[42L].orEmpty()
        assertEquals(2, forwards.size)
        assertTrue("Forward 1 should be enabled from bridge", forwards.first { it.id == 1L }.isEnabled())
        assertFalse("Forward 2 should stay disabled", forwards.first { it.id == 2L }.isEnabled())
    }

    /**
     * Tests that toggling a port forward enables it on the bridge and refreshes state.
     *
     * Scenario: Host 42 is connected with one disabled forward; user flips the toggle on.
     * Expected: bridge.enablePortForward is called and uiState shows the forward enabled.
     */
    @Test
    fun togglePortForward_enablesOnBridge_andRefreshesState() = runTest {
        val bridgeForward = createTestPortForward(id = 1L, hostId = 42L)
        val bridge = createMockBridge(hostId = 42L, portForwards = listOf(bridgeForward))
        whenever(bridge.enablePortForward(bridgeForward)).thenAnswer {
            bridgeForward.setEnabled(true)
            true
        }

        val viewModel = createViewModel()
        viewModel.setTerminalManager(createTerminalManager(bridges = listOf(bridge)))
        portForwardsFlow.value = listOf(createTestPortForward(id = 1L, hostId = 42L))
        advanceUntilIdle()

        viewModel.togglePortForward(createTestPortForward(id = 1L, hostId = 42L), enable = true)
        advanceUntilIdle()

        verify(bridge).enablePortForward(bridgeForward)
        val forwards = viewModel.uiState.value.portForwards[42L].orEmpty()
        assertTrue("Forward should be enabled after toggle", forwards.first { it.id == 1L }.isEnabled())
        assertNull("No error should be set", viewModel.uiState.value.error)
    }

    /**
     * Tests that toggling without an active connection surfaces an error.
     *
     * Scenario: No bridge exists for the forward's host.
     * Expected: uiState.error explains there is no active connection.
     */
    @Test
    fun togglePortForward_setsError_whenNoActiveConnection() = runTest {
        val viewModel = createViewModel()
        viewModel.setTerminalManager(createTerminalManager())
        advanceUntilIdle()

        viewModel.togglePortForward(createTestPortForward(id = 1L, hostId = 42L), enable = true)
        advanceUntilIdle()

        assertEquals("No active connection for this host", viewModel.uiState.value.error)
    }
}
