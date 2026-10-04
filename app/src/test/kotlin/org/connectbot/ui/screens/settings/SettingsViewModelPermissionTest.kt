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

package org.connectbot.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.ProfileRepository
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.di.FakeLanguagePackManager
import org.connectbot.util.PreferenceConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 34])
class SettingsViewModelPermissionTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var prefs: SharedPreferences
    private lateinit var repository: ProfileRepository

    @Before
    fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("alerts", Context.MODE_PRIVATE)
        repository = mock(ProfileRepository::class.java)
        `when`(repository.getAll()).thenReturn(emptyList())
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsViewModel(
        prefs,
        repository,
        RuntimeEnvironment.getApplication(),
        CoroutineDispatchers(dispatcher, dispatcher, dispatcher),
        FakeLanguagePackManager(),
    )

    @Test
    fun permissionResultsPreserveBothAlertChoices() = runTest(dispatcher) {
        for (enabled in listOf(false, true)) {
            prefs.edit().putBoolean(PreferenceConstants.CONNECTION_ALERTS, enabled).apply()
            val model = viewModel()
            for (granted in listOf(false, true)) {
                model.onNotificationPermissionResult(granted)
                advanceUntilIdle()
                assertEquals(enabled, model.uiState.value.connectionAlerts)
                assertEquals(enabled, prefs.getBoolean(PreferenceConstants.CONNECTION_ALERTS, !enabled))
                assertEquals(!granted, prefs.getBoolean(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED, granted))
            }
        }
    }

    @Test
    fun enablingAlertsRequestsPermissionWithoutRevertingAfterDenial() = runTest(dispatcher) {
        prefs.edit().putBoolean(PreferenceConstants.CONNECTION_ALERTS, false).apply()
        val model = viewModel()
        var requests = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.requestNotificationPermission.collect { requests++ } }
        model.updateConnectionAlerts(true)
        advanceUntilIdle()
        assertEquals(1, requests)
        assertTrue(prefs.getBoolean(PreferenceConstants.NOTIFICATION_PERMISSION_REQUESTED, false))
        model.onNotificationPermissionResult(false)
        advanceUntilIdle()
        assertTrue(model.uiState.value.connectionAlerts)
        assertFalse(model.uiState.value.notificationsAvailable)
    }

    @Test
    fun previouslyDeniedPermissionOffersSettingsAndPreservesAlertChoice() = runTest(dispatcher) {
        prefs.edit().putBoolean(PreferenceConstants.CONNECTION_ALERTS, false)
            .putBoolean(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED, true).apply()
        val model = viewModel()
        var dialogs = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.showPermissionDeniedDialog.collect { dialogs++ } }
        model.updateConnectionAlerts(true)
        advanceUntilIdle()
        assertEquals(1, dialogs)
        assertTrue(model.uiState.value.connectionAlerts)
    }

    @Test
    fun resumeRefreshesAvailabilityWithoutChangingPreferencesOrRecordingARequest() = runTest(dispatcher) {
        prefs.edit().putBoolean(PreferenceConstants.CONNECTION_ALERTS, false).apply()
        val model = viewModel()
        model.onNotificationPermissionChanged(false)
        advanceUntilIdle()
        assertFalse(model.uiState.value.notificationsAvailable)
        assertFalse(prefs.contains(PreferenceConstants.NOTIFICATION_PERMISSION_REQUESTED))
        assertFalse(prefs.contains(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED))
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        model.onNotificationPermissionChanged(true)
        advanceUntilIdle()
        assertTrue(model.uiState.value.notificationsAvailable)
        assertFalse(model.uiState.value.connectionAlerts)
    }
}
