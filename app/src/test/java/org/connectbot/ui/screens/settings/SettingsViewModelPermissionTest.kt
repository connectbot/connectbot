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

package org.connectbot.ui.screens.settings

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.connectbot.util.PreferenceConstants
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for SettingsViewModel notification permission logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelPermissionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(testDispatcher)
        prefs = mock()
        prefsEditor = mock()

        // Setup mock SharedPreferences
        whenever(prefs.edit()).thenReturn(prefsEditor)
        whenever(prefsEditor.putBoolean(any(), any())).thenReturn(prefsEditor)
        whenever(prefsEditor.putString(any(), any())).thenReturn(prefsEditor)
        whenever(prefsEditor.putInt(any(), any())).thenReturn(prefsEditor)

        // Setup default preference values
        whenever(prefs.getBoolean(eq("memkeys"), any())).thenReturn(true)
        whenever(prefs.getBoolean(eq(PreferenceConstants.CONNECTION_PERSIST), any())).thenReturn(false)
        whenever(prefs.getBoolean(eq("wifilock"), any())).thenReturn(false)
        whenever(prefs.getBoolean(eq("backupkeys"), any())).thenReturn(false)
        whenever(prefs.getString(eq("emulation"), any())).thenReturn("xterm-256color")
        whenever(prefs.getInt(eq("scrollback"), any())).thenReturn(140)
        whenever(prefs.getString(eq("rotation"), any())).thenReturn("Default")
        whenever(prefs.getString(eq("fullscreen"), any())).thenReturn("Default")
        whenever(prefs.getBoolean(eq("titlebarhide"), any())).thenReturn(false)
        whenever(prefs.getBoolean(eq("pg_updn_gestures"), any())).thenReturn(false)
        whenever(prefs.getString(eq("volumefont"), any())).thenReturn("Default")
        whenever(prefs.getInt(eq("keepalive"), any())).thenReturn(0)
        whenever(prefs.getBoolean(eq("alwaysvisible"), any())).thenReturn(false)
        whenever(prefs.getBoolean(eq("shiftfkeys"), any())).thenReturn(false)
        whenever(prefs.getBoolean(eq("ctrlfkeys"), any())).thenReturn(false)
        whenever(prefs.getBoolean(eq("stickymodifiers"), any())).thenReturn(true)
        whenever(prefs.getString(eq("keymode"), any())).thenReturn("Use right-side keys")
        whenever(prefs.getString(eq("camera"), any())).thenReturn("None")
        whenever(prefs.getBoolean(eq("bumpyarrows"), any())).thenReturn(true)
        whenever(prefs.getString(eq("bell"), any())).thenReturn("Vibrate")
        whenever(prefs.getInt(eq("bellvolume"), any())).thenReturn(100)
        whenever(prefs.getBoolean(eq("bellvibrate"), any())).thenReturn(true)
        whenever(prefs.getBoolean(eq("bellnotification"), any())).thenReturn(false)
        whenever(prefs.getBoolean(eq(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED), any())).thenReturn(false)

        viewModel = SettingsViewModel(prefs)
        advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // region updateConnPersist tests

    @Test
    fun updateConnPersist_TurningOn_FirstTime_RequestsPermissionAndOptimisticallyUpdates() = runTest {
        val permissionRequests = mutableListOf<Unit>()
        val job = launch {
            viewModel.requestNotificationPermission.collect {
                permissionRequests.add(it)
            }
        }

        viewModel.updateConnPersist(true)
        advanceUntilIdle()

        assertEquals("Should request permission", 1, permissionRequests.size)
        // Should optimistically update preference to ON while waiting for permission result
        verify(prefsEditor).putBoolean(eq(PreferenceConstants.CONNECTION_PERSIST), eq(true))

        job.cancel()
    }

    @Test
    fun updateConnPersist_TurningOn_AfterDenied_ShowsPermissionDeniedDialog() = runTest {
        // First deny permission
        viewModel.updateConnPersist(true)
        advanceUntilIdle()
        viewModel.onNotificationPermissionResult(false)
        advanceUntilIdle()

        // Update mock to return true for wasPermissionDenied after denial
        whenever(prefs.getBoolean(eq(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED), any())).thenReturn(true)

        val dialogEvents = mutableListOf<Unit>()
        val job = launch {
            viewModel.showPermissionDeniedDialog.collect {
                dialogEvents.add(it)
            }
        }

        // Try to turn on again
        viewModel.updateConnPersist(true)
        advanceUntilIdle()

        assertEquals("Should show permission denied dialog", 1, dialogEvents.size)

        job.cancel()
    }

    @Test
    fun updateConnPersist_TurningOff_UpdatesPreference() = runTest {
        // Setup connPersist as ON
        whenever(prefs.getBoolean(eq(PreferenceConstants.CONNECTION_PERSIST), any())).thenReturn(true)
        viewModel = SettingsViewModel(prefs)
        advanceUntilIdle()

        viewModel.updateConnPersist(false)
        advanceUntilIdle()

        verify(prefsEditor).putBoolean(eq(PreferenceConstants.CONNECTION_PERSIST), eq(false))
        verify(prefsEditor).apply()
    }

    @Test
    fun updateConnPersist_AlreadyOn_UpdatesPreference() = runTest {
        // Setup connPersist as ON
        whenever(prefs.getBoolean(eq(PreferenceConstants.CONNECTION_PERSIST), any())).thenReturn(true)
        viewModel = SettingsViewModel(prefs)
        advanceUntilIdle()

        viewModel.updateConnPersist(true)
        advanceUntilIdle()

        verify(prefsEditor).putBoolean(eq(PreferenceConstants.CONNECTION_PERSIST), eq(true))
        verify(prefsEditor).apply()
    }

    // endregion

    // region onNotificationPermissionResult tests

    @Test
    fun onNotificationPermissionResult_Granted_EnablesConnPersistAndClearsDenialState() = runTest {
        // First deny permission to set the denial state
        viewModel.onNotificationPermissionResult(false)
        advanceUntilIdle()

        // Then grant permission (simulating user going to settings)
        viewModel.onNotificationPermissionResult(true)
        advanceUntilIdle()

        verify(prefsEditor).putBoolean(eq(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED), eq(false))
        verify(prefsEditor).putBoolean(eq(PreferenceConstants.CONNECTION_PERSIST), eq(true))
        verify(prefsEditor, atLeastOnce()).apply()

        // Verify denial state is persisted by recreating ViewModel
        whenever(prefs.getBoolean(eq(PreferenceConstants.CONNECTION_PERSIST), any())).thenReturn(false)
        // The NOTIFICATION_PERMISSION_DENIED flag is still false in SharedPreferences
        viewModel = SettingsViewModel(prefs)
        advanceUntilIdle()

        val permissionRequests = mutableListOf<Unit>()
        val job = launch {
            viewModel.requestNotificationPermission.collect {
                permissionRequests.add(it)
            }
        }

        viewModel.updateConnPersist(true)
        advanceUntilIdle()

        // Should request permission (not show dialog) because denial state was cleared and persisted
        assertEquals("New ViewModel should read persisted state: not denied", 1, permissionRequests.size)

        job.cancel()
    }

    @Test
    fun onNotificationPermissionResult_Denied_KeepsConnPersistOffAndSetsDenialState() = runTest {
        viewModel.onNotificationPermissionResult(false)
        advanceUntilIdle()

        verify(prefsEditor).putBoolean(eq(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED), eq(true))
        verify(prefsEditor).putBoolean(eq(PreferenceConstants.CONNECTION_PERSIST), eq(false))
        verify(prefsEditor, atLeastOnce()).apply()

        // Update mock to return true for wasPermissionDenied
        whenever(prefs.getBoolean(eq(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED), any())).thenReturn(true)

        // Verify denial state is set by trying to turn on again
        val dialogEvents = mutableListOf<Unit>()
        val job = launch {
            viewModel.showPermissionDeniedDialog.collect {
                dialogEvents.add(it)
            }
        }

        viewModel.updateConnPersist(true)
        advanceUntilIdle()

        assertEquals("Should show permission denied dialog", 1, dialogEvents.size)

        job.cancel()
    }

    @Test
    fun denialState_PersistsAcrossViewModelRecreation() = runTest {
        // Deny permission
        viewModel.onNotificationPermissionResult(false)
        advanceUntilIdle()

        // Update the mock to return true for NOTIFICATION_PERMISSION_DENIED (simulating persistence)
        whenever(prefs.getBoolean(eq(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED), any())).thenReturn(true)

        // Recreate ViewModel (simulating process death/configuration change)
        viewModel = SettingsViewModel(prefs)
        advanceUntilIdle()

        // Try to turn on connPersist
        val dialogEvents = mutableListOf<Unit>()
        val job = launch {
            viewModel.showPermissionDeniedDialog.collect {
                dialogEvents.add(it)
            }
        }

        viewModel.updateConnPersist(true)
        advanceUntilIdle()

        // Should show dialog (not request permission) because denial state was persisted
        assertEquals("Denial state should persist across ViewModel recreation", 1, dialogEvents.size)

        job.cancel()
    }

    // endregion

    // region Integration tests

    @Test
    fun permissionFlow_GrantedOnFirstAttempt_EnablesConnPersist() = runTest {
        val permissionRequests = mutableListOf<Unit>()
        val requestJob = launch {
            viewModel.requestNotificationPermission.collect {
                permissionRequests.add(it)
            }
        }

        // User turns on connPersist
        viewModel.updateConnPersist(true)
        advanceUntilIdle()

        assertEquals("Should request permission", 1, permissionRequests.size)

        // User grants permission
        viewModel.onNotificationPermissionResult(true)
        advanceUntilIdle()

        // Preference should be set to true (called twice: optimistic update + permission granted)
        verify(prefsEditor, atLeastOnce()).putBoolean(eq(PreferenceConstants.CONNECTION_PERSIST), eq(true))

        requestJob.cancel()
    }

    @Test
    fun permissionFlow_DeniedThenRetried_ShowsDialog() = runTest {
        // First attempt - request permission
        val permissionRequests = mutableListOf<Unit>()
        val requestJob = launch {
            viewModel.requestNotificationPermission.collect {
                permissionRequests.add(it)
            }
        }

        viewModel.updateConnPersist(true)
        advanceUntilIdle()

        assertEquals("Should request permission first time", 1, permissionRequests.size)

        // User denies
        viewModel.onNotificationPermissionResult(false)
        advanceUntilIdle()

        // Preference should end up false (called twice: first optimistic true, then denied false)
        verify(prefsEditor, atLeastOnce()).putBoolean(eq(PreferenceConstants.CONNECTION_PERSIST), eq(false))

        // Update mock to return true for wasPermissionDenied after denial
        whenever(prefs.getBoolean(eq(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED), any())).thenReturn(true)

        requestJob.cancel()

        // Second attempt - should show dialog instead
        val dialogEvents = mutableListOf<Unit>()
        val dialogJob = launch {
            viewModel.showPermissionDeniedDialog.collect {
                dialogEvents.add(it)
            }
        }

        viewModel.updateConnPersist(true)
        advanceUntilIdle()

        assertEquals("Should show dialog second time", 1, dialogEvents.size)

        dialogJob.cancel()
    }

    @Test
    fun permissionFlow_DeniedThenGranted_ClearsDenialState() = runTest {
        // First attempt - deny
        viewModel.updateConnPersist(true)
        advanceUntilIdle()
        viewModel.onNotificationPermissionResult(false)
        advanceUntilIdle()

        // Update mock to return true for wasPermissionDenied after denial
        whenever(prefs.getBoolean(eq(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED), any())).thenReturn(true)

        // Second attempt - should show dialog
        val dialogEvents = mutableListOf<Unit>()
        val dialogJob = launch {
            viewModel.showPermissionDeniedDialog.collect {
                dialogEvents.add(it)
            }
        }

        viewModel.updateConnPersist(true)
        advanceUntilIdle()

        assertEquals("Should show dialog", 1, dialogEvents.size)
        dialogJob.cancel()

        // User goes to settings and grants permission, then tries again
        viewModel.onNotificationPermissionResult(true)
        advanceUntilIdle()

        // Update mock to return false after granting permission
        whenever(prefs.getBoolean(eq(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED), any())).thenReturn(false)

        // Third attempt - should request permission again (not show dialog)
        val permissionRequests = mutableListOf<Unit>()
        val requestJob = launch {
            viewModel.requestNotificationPermission.collect {
                permissionRequests.add(it)
            }
        }

        viewModel.updateConnPersist(true)
        advanceUntilIdle()

        assertEquals("Should request permission after being granted", 1, permissionRequests.size)

        requestJob.cancel()
    }

    // endregion
}
