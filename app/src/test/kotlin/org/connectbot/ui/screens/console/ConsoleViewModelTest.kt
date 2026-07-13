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
import org.connectbot.data.KeyboardLayoutRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.AuthenticationMethod
import org.connectbot.service.AuthorizedKeyInstallResult
import org.connectbot.service.BridgeConnectionPhase
import org.connectbot.service.BridgeConnectionState
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.terminal.ProgressState
import org.connectbot.transport.AbsTransport
import org.connectbot.util.NotificationPermissionHelper
import org.connectbot.util.PreferenceConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

/**
 * Tests for ConsoleViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConsoleViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )
    private lateinit var terminalManager: TerminalManager
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var bridgesFlow: MutableStateFlow<List<TerminalBridge>>
    private lateinit var prefs: SharedPreferences
    private lateinit var keyboardLayoutRepository: KeyboardLayoutRepository
    private lateinit var notificationPermissionHelper: NotificationPermissionHelper

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        terminalManager = Mockito.mock(TerminalManager::class.java)
        savedStateHandle = Mockito.mock(SavedStateHandle::class.java)
        prefs = Mockito.mock(SharedPreferences::class.java)
        keyboardLayoutRepository = Mockito.mock(KeyboardLayoutRepository::class.java)
        notificationPermissionHelper = Mockito.mock(NotificationPermissionHelper::class.java)
        bridgesFlow = MutableStateFlow(emptyList())
        Mockito.`when`(terminalManager.bridgesFlow).thenReturn(bridgesFlow)
        Mockito.`when`(terminalManager.hostStatusChangedFlow).thenReturn(MutableSharedFlow())
        Mockito.`when`(notificationPermissionHelper.isGranted()).thenReturn(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_IsLoading() {
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(1L)
        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Should not be loading", state.isLoading)
        assertEquals("Should have 1 bridge", 1, state.bridges.size)
        assertEquals("Bridge should match", mockBridge, state.bridges[0])
    }

    @Test
    fun loadBridges_WithRequestedHost_ShowsAllBridgesAndSelectsRequestedHost() = runTest {
        val mockBridge1 = createMockBridge(1L, "host1")
        val mockBridge2 = createMockBridge(2L, "host2")
        bridgesFlow.value = listOf(mockBridge1, mockBridge2)
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(2L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Should stop loading once the requested bridge is present", state.isLoading)
        assertEquals("Should keep all active bridges visible", 2, state.bridges.size)
        assertEquals("Requested host should be the selected bridge", 1, state.currentBridgeIndex)
        assertEquals("Requested bridge should be active", 2L, state.bridges[state.currentBridgeIndex].host.id)
    }

    @Test
    fun loadBridges_WhenRequestedHostIsOpening_KeepsLoadingUntilItAppears() = runTest {
        val mockBridge1 = createMockBridge(1L, "host1")
        val mockBridge2 = createMockBridge(2L, "host2")
        bridgesFlow.value = listOf(mockBridge1)
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(2L)
        Mockito.`when`(terminalManager.openConnectionForHostId(2L)).thenReturn(mockBridge2)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        assertTrue("Should keep showing loading until the requested bridge is active", viewModel.uiState.value.isLoading)
        Mockito.verify(terminalManager).openConnectionForHostId(2L)

        bridgesFlow.value = listOf(mockBridge1, mockBridge2)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Should stop loading once the requested bridge appears", state.isLoading)
        assertEquals("Requested bridge should become active when it appears", 2L, state.bridges[state.currentBridgeIndex].host.id)
    }

    @Test
    fun loadBridges_WithMultipleBridges_LoadsAll() = runTest {
        val mockBridge1 = createMockBridge(1L, "host1")
        val mockBridge2 = createMockBridge(2L, "host2")
        val mockBridge3 = createMockBridge(3L, "host3")
        bridgesFlow.value = listOf(mockBridge1, mockBridge2, mockBridge3)
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        // Select the second bridge
        viewModel.selectBridge(1)

        val state = viewModel.uiState.value
        assertEquals("Current bridge index should be 1", 1, state.currentBridgeIndex)
    }

    @Test
    fun selectNextBridge_UpdatesCurrentBridge() = runTest {
        val mockBridge1 = createMockBridge(1L, "host1")
        val mockBridge2 = createMockBridge(2L, "host2")
        bridgesFlow.value = listOf(mockBridge1, mockBridge2)
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()

        viewModel.selectNextBridge()

        assertEquals("Current bridge index should advance by one", 1, viewModel.uiState.value.currentBridgeIndex)
    }

    @Test
    fun selectPreviousBridge_UpdatesCurrentBridge() = runTest {
        val mockBridge1 = createMockBridge(1L, "host1")
        val mockBridge2 = createMockBridge(2L, "host2")
        bridgesFlow.value = listOf(mockBridge1, mockBridge2)
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()
        viewModel.selectBridge(1)

        viewModel.selectPreviousBridge()

        assertEquals("Current bridge index should move back by one", 0, viewModel.uiState.value.currentBridgeIndex)
    }

    @Test
    fun selectBridge_InvalidIndex_DoesNotUpdate() = runTest {
        val mockBridge = createMockBridge(1L, "test-host")
        bridgesFlow.value = listOf(mockBridge)
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
    fun onDisconnected_PreservesSelectedHost_WhenAnotherBridgeIsRemoved() = runTest {
        val mockBridge1 = createMockBridge(1L, "host1")
        val mockBridge2 = createMockBridge(2L, "host2")
        val mockBridge3 = createMockBridge(3L, "host3")

        bridgesFlow.value = listOf(mockBridge1, mockBridge2, mockBridge3)
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()
        viewModel.selectBridge(2)

        bridgesFlow.value = listOf(mockBridge2, mockBridge3)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Should still have 2 bridges", 2, state.bridges.size)
        assertEquals("Selected host should remain the same after another bridge is removed", 3L, state.bridges[state.currentBridgeIndex].host.id)
    }

    @Test
    fun onDisconnected_LastBridge_KeepsIndexAtZero() = runTest {
        val mockBridge = createMockBridge(1L, "test-host")

        bridgesFlow.value = listOf(mockBridge)
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
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

    @Test
    fun shouldShowNotificationWarning_PermissionNeverRequested_ReturnsFalse() = runTest {
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)
        Mockito.`when`(prefs.contains(Mockito.eq(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED))).thenReturn(false)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)

        assertFalse("Should not show warning before permission has ever been requested", viewModel.shouldShowNotificationWarning())
    }

    @Test
    fun shouldShowNotificationWarning_ConnPersistFalse_ReturnsTrue() = runTest {
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)
        Mockito.`when`(prefs.contains(Mockito.eq(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED))).thenReturn(true)
        Mockito.`when`(prefs.getBoolean(Mockito.eq(PreferenceConstants.CONNECTION_PERSIST), Mockito.anyBoolean())).thenReturn(false)
        Mockito.`when`(notificationPermissionHelper.isGranted()).thenReturn(true)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)

        assertTrue("Should show warning when connPersist is false", viewModel.shouldShowNotificationWarning())
    }

    @Test
    fun shouldShowNotificationWarning_PermissionDenied_ReturnsTrue() = runTest {
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)
        Mockito.`when`(prefs.contains(Mockito.eq(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED))).thenReturn(true)
        Mockito.`when`(prefs.getBoolean(Mockito.eq(PreferenceConstants.CONNECTION_PERSIST), Mockito.anyBoolean())).thenReturn(true)
        Mockito.`when`(notificationPermissionHelper.isGranted()).thenReturn(false)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)

        assertTrue("Should show warning when permission denied", viewModel.shouldShowNotificationWarning())
    }

    @Test
    fun shouldShowNotificationWarning_ConnPersistTrueAndPermissionGranted_ReturnsFalse() = runTest {
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)
        Mockito.`when`(prefs.contains(Mockito.eq(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED))).thenReturn(true)
        Mockito.`when`(prefs.getBoolean(Mockito.eq(PreferenceConstants.CONNECTION_PERSIST), Mockito.anyBoolean())).thenReturn(true)
        Mockito.`when`(notificationPermissionHelper.isGranted()).thenReturn(true)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)

        assertFalse("Should not show warning when connPersist is true and permission granted", viewModel.shouldShowNotificationWarning())
    }

    @Test
    fun progressState_SwitchingBridgesShowsExistingProgressImmediately() = runTest {
        val progressFlow1 =
            MutableStateFlow<TerminalBridge.ProgressInfo?>(TerminalBridge.ProgressInfo(ProgressState.DEFAULT, 10))
        val progressFlow2 =
            MutableStateFlow<TerminalBridge.ProgressInfo?>(TerminalBridge.ProgressInfo(ProgressState.WARNING, 90))
        val mockBridge1 = createMockBridge(1L, "host1", progressFlow1)
        val mockBridge2 = createMockBridge(2L, "host2", progressFlow2)
        bridgesFlow.value = listOf(mockBridge1, mockBridge2)
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
        viewModel.setTerminalManager(terminalManager)

        advanceUntilIdle()
        viewModel.selectBridge(1)

        assertEquals("Switching bridges should show the selected bridge's current progress state", ProgressState.WARNING, viewModel.uiState.value.progressState)
        assertEquals("Switching bridges should show the selected bridge's current progress value", 90, viewModel.uiState.value.progressValue)
    }

    @Test
    fun setupKeyLogin_completionPreservesAnotherHostsOffer() = runTest {
        val firstBridge = createMockBridge(
            id = 1L,
            hostname = "first-host",
            authenticationMethod = AuthenticationMethod.PASSWORD,
            canOpenExecChannels = true,
        )
        val secondBridge = createMockBridge(
            id = 2L,
            hostname = "second-host",
            authenticationMethod = AuthenticationMethod.PASSWORD,
            canOpenExecChannels = true,
        )
        bridgesFlow.value = listOf(firstBridge, secondBridge)
        Mockito.`when`(savedStateHandle.get<Long>("hostId")).thenReturn(-1L)
        Mockito.`when`(terminalManager.installAuthorizedKey(firstBridge)).thenReturn(
            AuthorizedKeyInstallResult.Success(Mockito.mock(Pubkey::class.java)),
        )

        val viewModel = ConsoleViewModel(savedStateHandle, dispatchers, prefs, keyboardLayoutRepository, notificationPermissionHelper)
        viewModel.setTerminalManager(terminalManager)
        advanceUntilIdle()

        viewModel.setupKeyLogin()
        assertEquals(1L, viewModel.uiState.value.keySetupInstallingHostId)

        viewModel.selectBridge(1)
        assertEquals(2L, viewModel.uiState.value.keySetupOfferHostId)
        assertEquals(1L, viewModel.uiState.value.keySetupInstallingHostId)

        advanceUntilIdle()

        assertNull(viewModel.uiState.value.keySetupInstallingHostId)
        assertEquals(2L, viewModel.uiState.value.keySetupOfferHostId)
    }

    private fun createMockBridge(
        id: Long,
        hostname: String,
        progressFlow: MutableStateFlow<TerminalBridge.ProgressInfo?> = MutableStateFlow(null),
        authenticationMethod: AuthenticationMethod? = null,
        canOpenExecChannels: Boolean = false,
    ): TerminalBridge {
        val bridge = Mockito.mock(TerminalBridge::class.java)
        val host = Host(
            id = id,
            hostname = hostname,
            nickname = hostname,
            protocol = "ssh",
            port = 22,
            username = "test",
        )
        Mockito.`when`(bridge.host).thenReturn(host)
        Mockito.`when`(bridge.isSessionOpen).thenReturn(true)
        Mockito.`when`(bridge.isDisconnected).thenReturn(false)
        Mockito.`when`(bridge.bellEvents).thenReturn(MutableSharedFlow())
        Mockito.`when`(bridge.commandCompletions).thenReturn(MutableSharedFlow())
        Mockito.`when`(bridge.progressState).thenReturn(progressFlow)
        Mockito.`when`(bridge.networkStatusMessages).thenReturn(MutableSharedFlow())
        Mockito.`when`(bridge.authenticationMethod).thenReturn(MutableStateFlow(authenticationMethod))
        Mockito.`when`(bridge.connectionState).thenReturn(
            MutableStateFlow(BridgeConnectionState(phase = BridgeConnectionPhase.CONNECTED)),
        )
        if (canOpenExecChannels) {
            val transport = Mockito.mock(AbsTransport::class.java)
            Mockito.`when`(transport.canOpenExecChannels()).thenReturn(true)
            Mockito.`when`(bridge.transport).thenReturn(transport)
        }
        return bridge
    }
}
