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

package org.connectbot.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.PreferenceConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.lang.ref.WeakReference

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TerminalManagerForegroundTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var manager: TerminalManager

    @Before
    fun setUp() {
        // Attach the service without invoking Hilt's production onCreate or starting transports.
        manager = Robolectric.buildService(TerminalManager::class.java).get()
        manager.dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        manager.prefs = RuntimeEnvironment.getApplication().getSharedPreferences("foreground", Context.MODE_PRIVATE)
        manager.prefs.edit().putBoolean(PreferenceConstants.CONNECTION_ALERTS, false).apply()
        manager.hostRepository = mock(HostRepository::class.java)
        manager.connectionNotifier = ConnectionNotifier(manager.dispatchers)
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun mockBridges() = mockConstruction(TerminalBridge::class.java) { bridge, construction ->
        val host = construction.arguments()[1] as Host
        `when`(bridge.host).thenReturn(host)
        `when`(bridge.notificationSessionId).thenReturn("session-${host.id}")
        `when`(bridge.isSessionOpen).thenReturn(true)
        `when`(bridge.sessionAttention).thenReturn(SessionAttention())
    }

    @Test
    fun openingConnectionsAlwaysStartsForegroundAndOnlyFinalDisconnectStopsIt() = runTest(dispatcher) {
        shadowOf(manager.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        val first = Host(id = 1L, nickname = "first")
        val second = Host(id = 2L, nickname = "second")
        `when`(manager.hostRepository.findHostById(first.id)).thenReturn(first)
        `when`(manager.hostRepository.findHostById(second.id)).thenReturn(second)
        mockBridges().use {
            val bridgeA = manager.openConnectionForHostId(first.id)!!
            val bridgeB = manager.openConnectionForHostId(second.id)!!
            advanceUntilIdle()
            val shadow = shadowOf(manager)
            assertTrue(shadow.lastForegroundNotification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
            assertFalse(shadow.isForegroundStopped)
            manager.onDisconnected(bridgeA)
            advanceUntilIdle()
            assertFalse(shadow.isForegroundStopped)
            manager.onDisconnected(bridgeB)
            advanceUntilIdle()
            assertTrue(shadow.isForegroundStopped)
        }
    }

    @Test
    fun acknowledgementAndMutingDoNotStopForegroundWithAlertsEnabled() = runTest(dispatcher) {
        manager.prefs.edit().putBoolean(PreferenceConstants.CONNECTION_ALERTS, true).apply()
        val host = Host(id = 1L, nickname = "first")
        `when`(manager.hostRepository.findHostById(host.id)).thenReturn(host)
        mockBridges().use {
            val bridge = manager.openConnectionForHostId(host.id)!!
            manager.sendActivityNotification(bridge)
            manager.acknowledgeNotification(bridge.notificationSessionId)
            manager.connectionNotifier.handleAction(manager, ConnectionNotifier.ACTION_MUTE, bridge.notificationSessionId)
            advanceUntilIdle()
            assertFalse(shadowOf(manager).isForegroundStopped)
            val notifications = manager.getSystemService(NotificationManager::class.java).activeNotifications
            assertTrue(notifications.any { it.id == ConnectionNotifier.ONLINE_NOTIFICATION })
            assertFalse(notifications.any { it.tag == bridge.notificationSessionId })
        }
    }

    @Test
    fun pendingReconnectKeepsForegroundAfterLastBridgeIsRemoved() = runTest(dispatcher) {
        val host = Host(id = 1L, nickname = "first")
        `when`(manager.hostRepository.findHostById(host.id)).thenReturn(host)
        mockBridges().use {
            val bridge = manager.openConnectionForHostId(host.id)!!
            advanceUntilIdle()
            val field = TerminalManager::class.java.getDeclaredField("pendingReconnect").apply { isAccessible = true }

            @Suppress("UNCHECKED_CAST")
            val pending = field.get(manager) as MutableList<WeakReference<TerminalBridge>>
            pending.add(WeakReference(bridge))
            manager.onDisconnected(bridge)
            advanceUntilIdle()
            assertFalse(shadowOf(manager).isForegroundStopped)
        }
    }
}
