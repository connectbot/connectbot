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

package org.connectbot.ui.screens.console

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.bean.HostBean
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.util.HostDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Tests for ConsoleViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var terminalManager: TerminalManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Create mock TerminalManager
        terminalManager = mock(TerminalManager::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_IsLoading() {
        val viewModel = ConsoleViewModel(terminalManager, 1L)

        val state = viewModel.uiState.value
        assertTrue("Initial state should be loading", state.isLoading)
        assertEquals("Initial bridges should be empty", 0, state.bridges.size)
        assertEquals("Initial currentBridgeIndex should be 0", 0, state.currentBridgeIndex)
        assertNull("Initial error should be null", state.error)
    }

    @Test
    fun loadBridges_WithNoBridges_StopsLoading() = runTest {
        `when`(terminalManager.bridges).thenReturn(ArrayList())

        val viewModel = ConsoleViewModel(terminalManager, -1L)

        // Advance time past the polling timeout (5 seconds)
        advanceTimeBy(5500)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Should stop loading after timeout", state.isLoading)
        assertEquals("Should have no bridges", 0, state.bridges.size)
    }

    @Test
    fun loadBridges_WithExistingBridges_LoadsSuccessfully() = runTest {
        val mockBridge = createMockBridge(1L, "test-host")
        `when`(terminalManager.bridges).thenReturn(arrayListOf(mockBridge))

        val viewModel = ConsoleViewModel(terminalManager, -1L)

        // Advance time to allow polling to complete
        advanceTimeBy(1000)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Should not be loading", state.isLoading)
        assertEquals("Should have 1 bridge", 1, state.bridges.size)
        assertEquals("Bridge should match", mockBridge, state.bridges[0])
    }

    @Test
    fun loadBridges_WithMultipleBridges_LoadsAll() = runTest {
        val mockBridge1 = createMockBridge(1L, "host1")
        val mockBridge2 = createMockBridge(2L, "host2")
        val mockBridge3 = createMockBridge(3L, "host3")
        `when`(terminalManager.bridges).thenReturn(arrayListOf(mockBridge1, mockBridge2, mockBridge3))

        val viewModel = ConsoleViewModel(terminalManager, -1L)

        advanceTimeBy(1000)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Should have 3 bridges", 3, state.bridges.size)
        assertEquals("First bridge should match", mockBridge1, state.bridges[0])
        assertEquals("Second bridge should match", mockBridge2, state.bridges[1])
        assertEquals("Third bridge should match", mockBridge3, state.bridges[2])
    }

    @Test
    fun selectBridge_ValidIndex_UpdatesCurrentBridge() = runTest {
        val mockBridge1 = createMockBridge(1L, "host1")
        val mockBridge2 = createMockBridge(2L, "host2")
        `when`(terminalManager.bridges).thenReturn(arrayListOf(mockBridge1, mockBridge2))

        val viewModel = ConsoleViewModel(terminalManager, -1L)

        advanceTimeBy(1000)
        advanceUntilIdle()

        // Select the second bridge
        viewModel.selectBridge(1)

        val state = viewModel.uiState.value
        assertEquals("Current bridge index should be 1", 1, state.currentBridgeIndex)
    }

    @Test
    fun selectBridge_InvalidIndex_DoesNotUpdate() = runTest {
        val mockBridge = createMockBridge(1L, "test-host")
        `when`(terminalManager.bridges).thenReturn(arrayListOf(mockBridge))

        val viewModel = ConsoleViewModel(terminalManager, -1L)

        advanceTimeBy(1000)
        advanceUntilIdle()

        val initialState = viewModel.uiState.value

        // Try to select invalid index
        viewModel.selectBridge(5)

        val state = viewModel.uiState.value
        assertEquals("Current bridge index should remain 0", initialState.currentBridgeIndex, state.currentBridgeIndex)
    }

    @Test
    fun selectBridge_NegativeIndex_DoesNotUpdate() = runTest {
        val mockBridge = createMockBridge(1L, "test-host")
        `when`(terminalManager.bridges).thenReturn(arrayListOf(mockBridge))

        val viewModel = ConsoleViewModel(terminalManager, -1L)

        advanceTimeBy(1000)
        advanceUntilIdle()

        val initialState = viewModel.uiState.value

        // Try to select negative index
        viewModel.selectBridge(-1)

        val state = viewModel.uiState.value
        assertEquals("Current bridge index should remain 0", initialState.currentBridgeIndex, state.currentBridgeIndex)
    }

    @Test
    fun refreshMenuState_IncrementsRevision() = runTest {
        val mockBridge = createMockBridge(1L, "test-host")
        `when`(terminalManager.bridges).thenReturn(arrayListOf(mockBridge))

        val viewModel = ConsoleViewModel(terminalManager, -1L)

        advanceTimeBy(1000)
        advanceUntilIdle()

        val initialRevision = viewModel.uiState.value.revision

        viewModel.refreshMenuState()

        val newRevision = viewModel.uiState.value.revision
        assertEquals("Revision should increment by 1", initialRevision + 1, newRevision)
    }

    @Test
    fun refreshMenuState_MultipleRefreshes_IncrementsEachTime() = runTest {
        val mockBridge = createMockBridge(1L, "test-host")
        `when`(terminalManager.bridges).thenReturn(arrayListOf(mockBridge))

        val viewModel = ConsoleViewModel(terminalManager, -1L)

        advanceTimeBy(1000)
        advanceUntilIdle()

        val initialRevision = viewModel.uiState.value.revision

        viewModel.refreshMenuState()
        viewModel.refreshMenuState()
        viewModel.refreshMenuState()

        val finalRevision = viewModel.uiState.value.revision
        assertEquals("Revision should increment by 3", initialRevision + 3, finalRevision)
    }

    @Test
    fun onDisconnected_RemovesBridge() = runTest {
        val mockBridge1 = createMockBridge(1L, "host1")
        val mockBridge2 = createMockBridge(2L, "host2")
        val mockBridge3 = createMockBridge(3L, "host3")

        // Initially return all 3 bridges
        `when`(terminalManager.bridges).thenReturn(arrayListOf(mockBridge1, mockBridge2, mockBridge3))

        val viewModel = ConsoleViewModel(terminalManager, -1L)

        advanceTimeBy(1000)
        advanceUntilIdle()

        assertEquals("Should start with 3 bridges", 3, viewModel.uiState.value.bridges.size)

        // Simulate bridge2 disconnecting - now only 2 bridges remain
        `when`(terminalManager.bridges).thenReturn(arrayListOf(mockBridge1, mockBridge3))

        viewModel.onDisconnected(mockBridge2)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Should have 2 bridges after disconnect", 2, state.bridges.size)
        assertFalse("Bridge2 should be removed", state.bridges.contains(mockBridge2))
    }

    @Test
    fun onDisconnected_AdjustsCurrentBridgeIndex_WhenCurrentBridgeRemoved() = runTest {
        val mockBridge1 = createMockBridge(1L, "host1")
        val mockBridge2 = createMockBridge(2L, "host2")
        val mockBridge3 = createMockBridge(3L, "host3")

        `when`(terminalManager.bridges).thenReturn(arrayListOf(mockBridge1, mockBridge2, mockBridge3))

        val viewModel = ConsoleViewModel(terminalManager, -1L)

        advanceTimeBy(1000)
        advanceUntilIdle()

        // Select the last bridge (index 2)
        viewModel.selectBridge(2)
        assertEquals("Current index should be 2", 2, viewModel.uiState.value.currentBridgeIndex)

        // Disconnect the last bridge
        `when`(terminalManager.bridges).thenReturn(arrayListOf(mockBridge1, mockBridge2))

        viewModel.onDisconnected(mockBridge3)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Should have 2 bridges", 2, state.bridges.size)
        assertTrue("Current index should be adjusted to valid range", state.currentBridgeIndex < state.bridges.size)
    }

    @Test
    fun onDisconnected_LastBridge_KeepsIndexAtZero() = runTest {
        val mockBridge = createMockBridge(1L, "test-host")

        `when`(terminalManager.bridges).thenReturn(arrayListOf(mockBridge))

        val viewModel = ConsoleViewModel(terminalManager, -1L)

        advanceTimeBy(1000)
        advanceUntilIdle()

        assertEquals("Should have 1 bridge", 1, viewModel.uiState.value.bridges.size)

        // Disconnect the only bridge
        `when`(terminalManager.bridges).thenReturn(ArrayList())

        viewModel.onDisconnected(mockBridge)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Should have no bridges", 0, state.bridges.size)
        assertEquals("Index should remain 0", 0, state.currentBridgeIndex)
    }

    // Helper function to create mock bridges
    private fun createMockBridge(id: Long, hostname: String): TerminalBridge {
        val bridge = mock(TerminalBridge::class.java)
        val host = HostBean().apply {
            this.id = id
            this.hostname = hostname
            this.nickname = hostname
            this.protocol = "ssh"
            this.port = 22
            this.username = "test"
        }
        // Set the public field directly
        bridge.host = host
        // Mock the methods
        `when`(bridge.isSessionOpen).thenReturn(true)
        `when`(bridge.isDisconnected).thenReturn(false)
        return bridge
    }
}
