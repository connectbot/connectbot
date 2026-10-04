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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
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
import org.connectbot.service.ConnectionNotifier
import org.connectbot.util.PreferenceConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class SettingsViewModelNotificationTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var repository: ProfileRepository

    @Before
    fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("notification-settings", Context.MODE_PRIVATE)
        repository = mock(ProfileRepository::class.java)
        `when`(repository.getAll()).thenReturn(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(
        prefs,
        repository,
        context,
        CoroutineDispatchers(dispatcher, dispatcher, dispatcher),
        FakeLanguagePackManager(),
    )

    @Test
    @Config(sdk = [24, 25, 26, 34])
    fun alertsDefaultToEnabled() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.bellNotification)
        assertTrue(viewModel.uiState.value.connectionLostNotification)
    }

    @Test
    @Config(sdk = [24, 25, 26, 34])
    fun resumingWithPermissionPreservesMainSettingOptOut() = runTest(dispatcher) {
        prefs.edit().putBoolean(PreferenceConstants.CONNECTION_ALERTS, false).apply()
        val viewModel = viewModel()

        viewModel.onNotificationPermissionChanged(true)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.connectionAlerts)
        assertFalse(prefs.getBoolean(PreferenceConstants.CONNECTION_ALERTS, true))
    }

    @Test
    fun resumingAfterPermissionChangesPreservesAlertPreference() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onNotificationPermissionChanged(false)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.connectionAlerts)
        assertFalse(viewModel.uiState.value.notificationsAvailable)

        viewModel.onNotificationPermissionChanged(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.connectionAlerts)
        assertFalse(prefs.contains(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED))
    }

    @Test
    @Config(sdk = [24, 26, 34])
    fun systemNotificationBlockingDoesNotChangeAlertPreference() = runTest(dispatcher) {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.notificationsAvailable)
        assertTrue(viewModel.uiState.value.connectionAlerts)
        assertFalse(prefs.contains(PreferenceConstants.NOTIFICATION_PERMISSION_REQUESTED))
    }

    @Test
    @Config(sdk = [24, 25])
    fun legacyOptOutsAreSavedAndRestored() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.updateBellNotification(false)
        viewModel.updateConnectionLostNotification(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.bellNotification)
        assertFalse(viewModel.uiState.value.connectionLostNotification)
        assertFalse(prefs.getBoolean(PreferenceConstants.BELL_NOTIFICATION, true))
        assertFalse(prefs.getBoolean(PreferenceConstants.CONNECTION_LOST_NOTIFICATION, true))
        val restored = viewModel()
        assertFalse(restored.uiState.value.bellNotification)
        assertFalse(restored.uiState.value.connectionLostNotification)
    }

    @Test
    @Config(sdk = [26, 34])
    fun appNotificationSettingsIntentIsEmittedAfterChannelsExist() = runTest(dispatcher) {
        val viewModel = viewModel()
        val manager = context.getSystemService(NotificationManager::class.java)
        val intents = mutableListOf<Intent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.openNotificationChannelSettings.collect { intent ->
                listOf(ConnectionNotifier.SESSION_CHANNEL, ConnectionNotifier.ATTENTION_CHANNEL, ConnectionNotifier.BELL_CHANNEL, ConnectionNotifier.LOSS_CHANNEL).forEach {
                    assertNotNull(manager.getNotificationChannel(it))
                }
                intents.add(intent)
            }
        }

        viewModel.openNotificationSettings()
        advanceUntilIdle()

        assertEquals(1, intents.size)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intents.single().action)
        assertEquals(context.packageName, intents.single().getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertFalse(intents.single().hasExtra(Settings.EXTRA_CHANNEL_ID))
        assertTrue(manager.activeNotifications.isEmpty())
        collector.cancel()
    }

    @Test
    @Config(sdk = [26, 34])
    fun openingSettingsPreservesBlockedChannels() = runTest(dispatcher) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(ConnectionNotifier.BELL_CHANNEL, "Bells", NotificationManager.IMPORTANCE_NONE))
        val viewModel = viewModel()

        viewModel.openNotificationSettings()
        advanceUntilIdle()

        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(ConnectionNotifier.BELL_CHANNEL).importance)
    }

    @Test
    @Config(sdk = [24, 25])
    fun legacyAndroidOpensApplicationSettings() = runTest(dispatcher) {
        val viewModel = viewModel()
        val intents = mutableListOf<Intent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.openNotificationChannelSettings.collect { intents.add(it) }
        }

        viewModel.openNotificationSettings()
        advanceUntilIdle()

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intents.single().action)
        collector.cancel()
    }
}
