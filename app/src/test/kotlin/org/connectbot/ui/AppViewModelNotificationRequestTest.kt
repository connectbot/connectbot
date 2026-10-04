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

package org.connectbot.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.migration.DatabaseMigrator
import org.connectbot.di.CoroutineDispatchers
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 34])
class AppViewModelNotificationRequestTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var prefs: SharedPreferences
    private lateinit var migrator: DatabaseMigrator
    private lateinit var permission: NotificationPermissionHelper

    @Before
    fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("request", Context.MODE_PRIVATE)
        migrator = mock(DatabaseMigrator::class.java)
        `when`(migrator.isMigrationNeeded()).thenReturn(false)
        permission = mock(NotificationPermissionHelper::class.java)
        `when`(permission.isGranted()).thenReturn(false)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AppViewModel(migrator, prefs, CoroutineDispatchers(dispatcher, dispatcher, dispatcher), permission)

    @Test
    fun firstUriConnectionRequestsOnceAndDenialPreservesAlertsAndNavigation() = runTest(dispatcher) {
        val model = viewModel()
        val uri = "ssh://user@host".toUri()
        var requests = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.requestPermission.collect { requests++ } }
        assertTrue(model.shouldRequestNotificationPermission())
        assertFalse(model.checkAndRequestNotificationPermission(uri, false))
        advanceUntilIdle()
        assertEquals(1, requests)
        assertFalse(model.shouldRequestNotificationPermission())
        assertEquals(uri, model.onNotificationPermissionResult(false))
        assertNull(model.pendingConnectionUri.value)
        assertTrue(prefs.getBoolean(PreferenceConstants.CONNECTION_ALERTS, true))
        assertTrue(model.checkAndRequestNotificationPermission(uri, true))
        assertFalse(viewModel().shouldRequestNotificationPermission())
    }

    @Test
    fun hostRequestRecordsAttemptBeforeRationaleAndSurvivesCancellationAndRestart() = runTest(dispatcher) {
        val model = viewModel()
        var rationales = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.showPermissionRationale.collect { rationales++ } }
        model.requestNotificationPermission(true)
        advanceUntilIdle()
        assertEquals(1, rationales)
        assertFalse(model.shouldRequestNotificationPermission())
        assertFalse(viewModel().shouldRequestNotificationPermission())
    }

    @Test
    fun oldPermissionDecisionsSuppressAutomaticRequests() {
        for (denied in listOf(false, true)) {
            prefs.edit().putBoolean(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED, denied).apply()
            assertFalse(viewModel().shouldRequestNotificationPermission())
        }
    }

    @Test
    fun alertOptOutAndExistingPermissionSkipAutomaticRequests() {
        prefs.edit().putBoolean(PreferenceConstants.CONNECTION_ALERTS, false).apply()
        assertFalse(viewModel().shouldRequestNotificationPermission())
        prefs.edit().putBoolean(PreferenceConstants.CONNECTION_ALERTS, true).apply()
        `when`(permission.isGranted()).thenReturn(true)
        assertFalse(viewModel().shouldRequestNotificationPermission())
    }

    @Test
    @Config(sdk = [24, 26, 32])
    fun legacyAndroidConnectsWithoutPermissionRequests() {
        val model = viewModel()
        assertTrue(model.checkAndRequestNotificationPermission("ssh://user@host".toUri(), false))
        assertFalse(prefs.contains(PreferenceConstants.NOTIFICATION_PERMISSION_REQUESTED))
    }
}
