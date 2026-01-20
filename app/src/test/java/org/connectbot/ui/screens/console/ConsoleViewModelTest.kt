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
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.terminal.ProgressState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for ConsoleViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsoleViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher
    )
    private lateinit var terminalManager: TerminalManager
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var bridgesFlow: MutableStateFlow<List<TerminalBridge>>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        terminalManager = mock()
        savedStateHandle = mock()
        bridgesFlow = MutableStateFlow(emptyList())
        whenever(terminalManager.bridgesFlow).thenReturn(bridgesFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_IsLoading() {
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(1L)
        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

        val state = viewModel.uiState.value
        assertTrue("Initial state should be loading", state.isLoading)
        assertEquals("Initial bridges should be empty", 0, state.bridges.size)
        assertEquals("Initial currentBridgeIndex should be 0", 0, state.currentBridgeIndex)
        assertNull("Initial error should be null", state.error)
    }

    @Test
    fun loadBridges_WithNoBridges_StopsLoading() = runTest {
        bridgesFlow.value = emptyList()
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Should stop loading after timeout", state.isLoading)
        assertEquals("Should have no bridges", 0, state.bridges.size)
    }

    @Test
    fun loadBridges_WithExistingBridges_LoadsSuccessfully() = runTest {
        val mockBridge = createMockBridge(1L, "test-host")
        bridgesFlow.value = listOf(mockBridge)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

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
        bridgesFlow.value = listOf(mockBridge1, mockBridge2, mockBridge3)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

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
        bridgesFlow.value = listOf(mockBridge1, mockBridge2)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        // Select the second bridge
        viewModel.selectBridge(1)

        val state = viewModel.uiState.value
        assertEquals("Current bridge index should be 1", 1, state.currentBridgeIndex)
    }

    @Test
    fun selectBridge_InvalidIndex_DoesNotUpdate() = runTest {
        val mockBridge = createMockBridge(1L, "test-host")
        bridgesFlow.value = listOf(mockBridge)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

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
        bridgesFlow.value = listOf(mockBridge)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

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
        bridgesFlow.value = listOf(mockBridge)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        val initialRevision = viewModel.uiState.value.revision

        viewModel.refreshMenuState()

        val newRevision = viewModel.uiState.value.revision
        assertEquals("Revision should increment by 1", initialRevision + 1, newRevision)
    }

    @Test
    fun refreshMenuState_MultipleRefreshes_IncrementsEachTime() = runTest {
        val mockBridge = createMockBridge(1L, "test-host")
        bridgesFlow.value = listOf(mockBridge)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

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

        bridgesFlow.value = listOf(mockBridge1, mockBridge2, mockBridge3)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        assertEquals("Should start with 3 bridges", 3, viewModel.uiState.value.bridges.size)

        // Simulate bridge removal via Flow
        bridgesFlow.value = listOf(mockBridge1, mockBridge3)
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

        bridgesFlow.value = listOf(mockBridge1, mockBridge2, mockBridge3)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        viewModel.selectBridge(2)
        assertEquals("Current index should be 2", 2, viewModel.uiState.value.currentBridgeIndex)

        // Simulate bridge removal via Flow
        bridgesFlow.value = listOf(mockBridge1, mockBridge2)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Should have 2 bridges", 2, state.bridges.size)
        assertTrue("Current index should be adjusted to valid range", state.currentBridgeIndex < state.bridges.size)
    }

    @Test
    fun onDisconnected_LastBridge_KeepsIndexAtZero() = runTest {
        val mockBridge = createMockBridge(1L, "test-host")

        bridgesFlow.value = listOf(mockBridge)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        assertEquals("Should have 1 bridge", 1, viewModel.uiState.value.bridges.size)

        // Simulate bridge removal via Flow
        bridgesFlow.value = emptyList()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Should have no bridges", 0, state.bridges.size)
        assertEquals("Index should remain 0", 0, state.currentBridgeIndex)
    }

    @Test
    fun progressState_UpdatesWhenBridgeEmitsProgress() = runTest {
        val progressFlow = MutableStateFlow<TerminalBridge.ProgressInfo?>(null)
        val mockBridge = createMockBridge(1L, "test-host", progressFlow)
        bridgesFlow.value = listOf(mockBridge)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        // Initially no progress
        assertNull("Initial progress state should be null", viewModel.uiState.value.progressState)
        assertEquals("Initial progress value should be 0", 0, viewModel.uiState.value.progressValue)

        // Emit progress update
        progressFlow.value = TerminalBridge.ProgressInfo(ProgressState.DEFAULT, 50)
        advanceUntilIdle()

        assertEquals("Progress state should be DEFAULT", ProgressState.DEFAULT, viewModel.uiState.value.progressState)
        assertEquals("Progress value should be 50", 50, viewModel.uiState.value.progressValue)
    }

    @Test
    fun progressState_ClearsWhenHidden() = runTest {
        val progressFlow = MutableStateFlow<TerminalBridge.ProgressInfo?>(null)
        val mockBridge = createMockBridge(1L, "test-host", progressFlow)
        bridgesFlow.value = listOf(mockBridge)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        // Set progress
        progressFlow.value = TerminalBridge.ProgressInfo(ProgressState.DEFAULT, 75)
        advanceUntilIdle()

        assertEquals("Progress should be set", ProgressState.DEFAULT, viewModel.uiState.value.progressState)

        // Hide progress
        progressFlow.value = TerminalBridge.ProgressInfo(ProgressState.HIDDEN, 0)
        advanceUntilIdle()

        assertNull("Progress state should be null when hidden", viewModel.uiState.value.progressState)
        assertEquals("Progress value should be 0 when hidden", 0, viewModel.uiState.value.progressValue)
    }

    @Test
    fun progressState_ClearsWhenNull() = runTest {
        val progressFlow = MutableStateFlow<TerminalBridge.ProgressInfo?>(null)
        val mockBridge = createMockBridge(1L, "test-host", progressFlow)
        bridgesFlow.value = listOf(mockBridge)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        // Set progress
        progressFlow.value = TerminalBridge.ProgressInfo(ProgressState.ERROR, 25)
        advanceUntilIdle()

        assertEquals("Progress should be ERROR", ProgressState.ERROR, viewModel.uiState.value.progressState)

        // Clear progress
        progressFlow.value = null
        advanceUntilIdle()

        assertNull("Progress state should be null", viewModel.uiState.value.progressState)
    }

    @Test
    fun progressState_ShowsCorrectStateTypes() = runTest {
        val progressFlow = MutableStateFlow<TerminalBridge.ProgressInfo?>(null)
        val mockBridge = createMockBridge(1L, "test-host", progressFlow)
        bridgesFlow.value = listOf(mockBridge)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        // Test ERROR state
        progressFlow.value = TerminalBridge.ProgressInfo(ProgressState.ERROR, 50)
        advanceUntilIdle()
        assertEquals("Should show ERROR state", ProgressState.ERROR, viewModel.uiState.value.progressState)

        // Test WARNING state
        progressFlow.value = TerminalBridge.ProgressInfo(ProgressState.WARNING, 75)
        advanceUntilIdle()
        assertEquals("Should show WARNING state", ProgressState.WARNING, viewModel.uiState.value.progressState)

        // Test INDETERMINATE state
        progressFlow.value = TerminalBridge.ProgressInfo(ProgressState.INDETERMINATE, 0)
        advanceUntilIdle()
        assertEquals("Should show INDETERMINATE state", ProgressState.INDETERMINATE, viewModel.uiState.value.progressState)
    }

    @Test
    fun progressState_OnlyShowsForCurrentBridge() = runTest {
        val progressFlow1 = MutableStateFlow<TerminalBridge.ProgressInfo?>(null)
        val progressFlow2 = MutableStateFlow<TerminalBridge.ProgressInfo?>(null)
        val mockBridge1 = createMockBridge(1L, "host1", progressFlow1)
        val mockBridge2 = createMockBridge(2L, "host2", progressFlow2)
        bridgesFlow.value = listOf(mockBridge1, mockBridge2)
        whenever(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        // Current bridge is index 0 (mockBridge1)
        assertEquals("Current bridge should be 0", 0, viewModel.uiState.value.currentBridgeIndex)

        // Emit progress on bridge 1 (current)
        progressFlow1.value = TerminalBridge.ProgressInfo(ProgressState.DEFAULT, 50)
        advanceUntilIdle()

        assertEquals("Should show progress from current bridge", 50, viewModel.uiState.value.progressValue)

        // Switch to bridge 2
        viewModel.selectBridge(1)
        advanceUntilIdle()

        // Emit progress on bridge 2 (now current)
        progressFlow2.value = TerminalBridge.ProgressInfo(ProgressState.ERROR, 75)
        advanceUntilIdle()

        assertEquals("Should show progress from new current bridge", ProgressState.ERROR, viewModel.uiState.value.progressState)
        assertEquals("Should show progress value from new current bridge", 75, viewModel.uiState.value.progressValue)
    }

    private fun createMockBridge(
        id: Long,
        hostname: String,
        progressFlow: MutableStateFlow<TerminalBridge.ProgressInfo?> = MutableStateFlow(null)
    ): TerminalBridge {
        val bridge = mock<TerminalBridge>()
        val host = Host(
            id = id,
            hostname = hostname,
            nickname = hostname,
            protocol = "ssh",
            port = 22,
            username = "test"
        )
        bridge.host = host
        whenever(bridge.isSessionOpen).thenReturn(true)
        whenever(bridge.isDisconnected).thenReturn(false)
        whenever(bridge.bellEvents).thenReturn(MutableSharedFlow())
        whenever(bridge.progressState).thenReturn(progressFlow)
        return bridge
    }
}
