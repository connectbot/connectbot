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

package org.connectbot.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.migration.DatabaseMigrator
import org.connectbot.service.TerminalManager
import org.connectbot.util.PreferenceConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for AppViewModel notification permission logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppViewModelPermissionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var migrator: DatabaseMigrator
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var viewModel: AppViewModel
    private lateinit var context: Context

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(testDispatcher)
        migrator = mock()
        prefs = mock()
        prefsEditor = mock()
        context = mock()

        whenever(migrator.isMigrationNeeded()).thenReturn(false)
        whenever(prefs.edit()).thenReturn(prefsEditor)
        whenever(prefsEditor.putBoolean(any(), any())).thenReturn(prefsEditor)

        viewModel = AppViewModel(migrator, prefs)
        advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region onNotificationPermissionResult tests

    @Test
    fun onNotificationPermissionResult_Granted_WithPendingUri_ReturnsPendingUriAndClearsIt() = runTest {
        val uri = "ssh://user@host".toUri()
        viewModel.setPendingConnectionUri(uri)

        val result = viewModel.onNotificationPermissionResult(isGranted = true)

        assertEquals("Should return the pending URI", uri, result)
        assertNull("Should clear pending URI", viewModel.pendingConnectionUri.value)
    }

    @Test
    fun onNotificationPermissionResult_Granted_NoPendingUri_ReturnsNull() = runTest {
        val result = viewModel.onNotificationPermissionResult(isGranted = true)

        assertNull("Should return null when no pending URI", result)
    }

    @Test
    fun onNotificationPermissionResult_Denied_WithPendingUri_ReturnsPendingUriAndClearsIt() = runTest {
        val uri = "ssh://user@host".toUri()
        viewModel.setPendingConnectionUri(uri)

        val result = viewModel.onNotificationPermissionResult(isGranted = false)

        assertEquals("Should return pending URI even when denied", uri, result)
        assertNull("Should clear pending URI", viewModel.pendingConnectionUri.value)
        verify(prefsEditor).putBoolean(eq(PreferenceConstants.CONNECTION_PERSIST), eq(false))
        verify(prefsEditor).apply()
    }

    @Test
    fun onNotificationPermissionResult_Denied_NoPendingUri_ReturnsNull() = runTest {
        val result = viewModel.onNotificationPermissionResult(isGranted = false)

        assertNull("Should return null when no pending URI", result)
        verify(prefsEditor).putBoolean(eq(PreferenceConstants.CONNECTION_PERSIST), eq(false))
        verify(prefsEditor).apply()
    }

    // endregion

    // region executePendingDisconnectAllIfReady tests

    @Test
    fun executePendingDisconnectAll_NotReady_ReturnsFalse() = runTest {
        viewModel.setPendingDisconnectAll(true)

        val result = viewModel.executePendingDisconnectAllIfReady()

        assertFalse("Should return false when not ready", result)
        assertTrue("Should keep pending flag", viewModel.pendingDisconnectAll.value)
    }

    @Test
    fun executePendingDisconnectAll_ReadyButNotPending_ReturnsFalse() = runTest {
        val terminalManager = mock<TerminalManager>()
        viewModel.setTerminalManager(terminalManager)
        advanceUntilIdle()

        val result = viewModel.executePendingDisconnectAllIfReady()

        assertFalse("Should return false when not pending", result)
        verify(terminalManager, never()).disconnectAll(any(), any())
    }

    @Test
    fun executePendingDisconnectAll_ReadyAndPending_ExecutesDisconnectAndEmitsFinishEvent() = runTest {
        val terminalManager = mock<TerminalManager>()
        viewModel.setTerminalManager(terminalManager)
        advanceUntilIdle()

        viewModel.setPendingDisconnectAll(true)

        val finishEvents = mutableListOf<Unit>()
        val job = launch {
            viewModel.finishActivity.collect {
                finishEvents.add(it)
            }
        }

        val result = viewModel.executePendingDisconnectAllIfReady()
        advanceUntilIdle()

        assertTrue("Should return true when executed", result)
        assertFalse("Should clear pending flag", viewModel.pendingDisconnectAll.value)
        verify(terminalManager).disconnectAll(immediate = true, excludeLocal = false)
        assertEquals("Should emit finish activity event", 1, finishEvents.size)

        job.cancel()
    }

    // endregion

    // region Pending state management tests

    @Test
    fun setPendingConnectionUri_UpdatesStateFlow() = runTest {
        val uri = "ssh://test@example.com".toUri()

        viewModel.setPendingConnectionUri(uri)

        assertEquals("Should update pending URI", uri, viewModel.pendingConnectionUri.value)
    }

    @Test
    fun clearPendingConnectionUri_ClearsStateFlow() = runTest {
        val uri = "ssh://test@example.com".toUri()
        viewModel.setPendingConnectionUri(uri)

        viewModel.clearPendingConnectionUri()

        assertNull("Should clear pending URI", viewModel.pendingConnectionUri.value)
    }

    @Test
    fun setPendingDisconnectAll_UpdatesStateFlow() = runTest {
        viewModel.setPendingDisconnectAll(true)
        assertTrue("Should set pending disconnect all", viewModel.pendingDisconnectAll.value)

        viewModel.setPendingDisconnectAll(false)
        assertFalse("Should clear pending disconnect all", viewModel.pendingDisconnectAll.value)
    }

    // endregion

    // region Integration tests

    @Test
    fun disconnectFlow_FullCycle_WhenReady() = runTest {
        val terminalManager = mock<TerminalManager>()

        val finishEvents = mutableListOf<Unit>()
        val finishJob = launch {
            viewModel.finishActivity.collect {
                finishEvents.add(it)
            }
        }

        // Step 1: Set pending before ready
        viewModel.setPendingDisconnectAll(true)
        var result = viewModel.executePendingDisconnectAllIfReady()
        advanceUntilIdle()

        assertFalse("Should not execute before ready", result)
        assertEquals("Should not emit finish event yet", 0, finishEvents.size)

        // Step 2: Become ready
        viewModel.setTerminalManager(terminalManager)
        advanceUntilIdle()

        // Step 3: Execute now that we're ready
        result = viewModel.executePendingDisconnectAllIfReady()
        advanceUntilIdle()

        assertTrue("Should execute when ready", result)
        verify(terminalManager).disconnectAll(immediate = true, excludeLocal = false)
        assertEquals("Should emit finish event", 1, finishEvents.size)
        assertFalse("Should clear pending flag", viewModel.pendingDisconnectAll.value)

        finishJob.cancel()
    }

    // endregion
}
