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

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.PreferenceConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doCallRealMethod
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TerminalManagerBellTest {
    private val dispatcher = StandardTestDispatcher()
    private val hostA = Host(id = 1L, nickname = "hostA")
    private val hostB = Host(id = 2L, nickname = "hostB")
    private lateinit var manager: TerminalManager
    private lateinit var bridgeA: TerminalBridge
    private lateinit var bridgeB: TerminalBridge

    @Before
    fun setUp() {
        manager = spy(TerminalManager())
        manager.dispatchers = CoroutineDispatchers(default = dispatcher, io = dispatcher, main = dispatcher)
        manager.prefs = RuntimeEnvironment.getApplication().getSharedPreferences("notifications", Context.MODE_PRIVATE)
        manager.connectionNotifier = mock(ConnectionNotifier::class.java)
        doReturn(true).`when`(manager).isUiVisible
        doNothing().`when`(manager).playBeep()
        bridgeA = bridge(hostA)
        bridgeB = bridge(hostB)
        doNothing().`when`(manager).sendActivityNotification(bridgeA)
        doNothing().`when`(manager).sendActivityNotification(bridgeB)
    }

    @Test
    fun visibleHostPlaysOnlyOneBellAfterNavigation() = runTest(dispatcher) {
        val ownerA = Any()
        val ownerB = Any()
        manager.setVisibleConsole(ownerA, bridgeA)
        manager.setVisibleConsole(ownerB, bridgeB)
        manager.clearVisibleConsole(ownerA)

        manager.onBell(bridgeB)
        advanceUntilIdle()

        verify(manager).playBeep()
        verify(manager, never()).sendActivityNotification(bridgeB)
    }

    @Test
    fun otherHostNotifiesWithOriginatingHost() = runTest(dispatcher) {
        manager.setVisibleConsole(Any(), bridgeB)

        manager.onBell(bridgeA)
        advanceUntilIdle()

        verify(manager).sendActivityNotification(bridgeA)
        verify(manager, never()).sendActivityNotification(bridgeB)
        verify(manager, never()).playBeep()
    }

    @Test
    fun leavingConsoleNotifiesInsteadOfPlayingBell() = runTest(dispatcher) {
        val owner = Any()
        manager.setVisibleConsole(owner, bridgeA)
        manager.clearVisibleConsole(owner)

        manager.onBell(bridgeA)
        advanceUntilIdle()

        verify(manager).sendActivityNotification(bridgeA)
        verify(manager, never()).playBeep()
    }

    @Test
    fun backgroundBellNotifiesEvenBeforeConsoleCleanup() = runTest(dispatcher) {
        manager.setVisibleConsole(Any(), bridgeA)
        doReturn(false).`when`(manager).isUiVisible

        manager.onBell(bridgeA)
        advanceUntilIdle()

        verify(manager).sendActivityNotification(bridgeA)
        verify(manager, never()).playBeep()
    }

    @Test
    fun switchingHostsUsesNewSelection() = runTest(dispatcher) {
        val owner = Any()
        manager.setVisibleConsole(owner, bridgeA)
        manager.setVisibleConsole(owner, bridgeB)

        manager.onBell(bridgeA)
        manager.onBell(bridgeB)
        advanceUntilIdle()

        verify(manager).sendActivityNotification(bridgeA)
        verify(manager, never()).sendActivityNotification(bridgeB)
        verify(manager).playBeep()
    }

    @Test
    fun departingConsoleDoesNotClearNewerConsoleAttention() = runTest(dispatcher) {
        val attention = SessionAttention()
        doReturn(attention).`when`(bridgeA).sessionAttention
        attention.onEnded(DisconnectReason.NETWORK_LOST)
        val oldOwner = Any()
        val newOwner = Any()
        manager.setVisibleConsole(oldOwner, bridgeA)
        manager.setVisibleConsole(newOwner, bridgeA)
        manager.clearVisibleConsole(oldOwner)
        manager.onConsoleClosed(oldOwner, bridgeA)

        attention.onOutput()
        assertFalse(attention.snapshot().unreadOutput)
        assertFalse(attention.snapshot().acknowledged)

        manager.clearVisibleConsole(newOwner)
        manager.onConsoleClosed(newOwner, bridgeA)
        assertTrue(attention.snapshot().acknowledged)
    }

    @Test
    fun lostConnectionInVisibleSessionDoesNotCreateSystemAlert() = runTest(dispatcher) {
        manager.setVisibleConsole(Any(), bridgeA)
        manager.onSessionEnded(bridgeA, DisconnectReason.NETWORK_LOST)
        advanceUntilIdle()
        verify(manager.connectionNotifier).sessionEnded(manager, NotificationSession("session-1", hostA, connected = true), DisconnectReason.NETWORK_LOST, false)
    }

    @Test
    fun lostConnectionInOtherSessionCreatesSystemAlert() = runTest(dispatcher) {
        manager.setVisibleConsole(Any(), bridgeB)
        manager.onSessionEnded(bridgeA, DisconnectReason.IO_ERROR)
        advanceUntilIdle()
        verify(manager.connectionNotifier).sessionEnded(manager, NotificationSession("session-1", hostA, connected = true), DisconnectReason.IO_ERROR, true)
    }

    @Test
    fun backgroundLossAlertsEvenBeforeConsoleCleanup() = runTest(dispatcher) {
        manager.setVisibleConsole(Any(), bridgeA)
        doReturn(false).`when`(manager).isUiVisible
        manager.onSessionEnded(bridgeA, DisconnectReason.NETWORK_LOST)
        advanceUntilIdle()
        verify(manager.connectionNotifier).sessionEnded(manager, NotificationSession("session-1", hostA, connected = true), DisconnectReason.NETWORK_LOST, true)
    }

    @Test
    @Config(sdk = [24, 25])
    fun lostConnectionPreferenceIsRespected() = runTest(dispatcher) {
        manager.prefs.edit().putBoolean(PreferenceConstants.CONNECTION_LOST_NOTIFICATION, false).apply()
        manager.onSessionEnded(bridgeA, DisconnectReason.IO_ERROR)
        advanceUntilIdle()
        verify(manager.connectionNotifier).sessionEnded(manager, NotificationSession("session-1", hostA, connected = true), DisconnectReason.IO_ERROR, false)
    }

    @Test
    @Config(sdk = [24, 25, 26, 34])
    fun notificationsAreEnabledByDefault() = runTest(dispatcher) {
        doCallRealMethod().`when`(manager).sendActivityNotification(bridgeA)
        manager.sendActivityNotification(bridgeA)
        manager.onSessionEnded(bridgeA, DisconnectReason.IO_ERROR)
        advanceUntilIdle()

        val session = NotificationSession("session-1", hostA, connected = true)
        verify(manager.connectionNotifier).showActivityNotification(manager, session)
        verify(manager.connectionNotifier).sessionEnded(manager, session, DisconnectReason.IO_ERROR, true)
    }

    @Test
    @Config(sdk = [24, 25, 26, 34])
    fun disablingMainNotificationSettingSuppressesBothAlerts() = runTest(dispatcher) {
        manager.prefs.edit()
            .putBoolean(PreferenceConstants.CONNECTION_PERSIST, false)
            .putBoolean(PreferenceConstants.BELL_NOTIFICATION, true)
            .putBoolean(PreferenceConstants.CONNECTION_LOST_NOTIFICATION, true)
            .apply()
        doCallRealMethod().`when`(manager).sendActivityNotification(bridgeA)
        manager.sendActivityNotification(bridgeA)
        manager.onSessionEnded(bridgeA, DisconnectReason.IO_ERROR)
        advanceUntilIdle()

        val session = NotificationSession("session-1", hostA, connected = true)
        verify(manager.connectionNotifier, never()).showActivityNotification(manager, session)
        verify(manager.connectionNotifier).sessionEnded(manager, session, DisconnectReason.IO_ERROR, false)
    }

    @Test
    @Config(sdk = [24, 25])
    fun legacyBellOptOutIsRespected() {
        manager.prefs.edit().putBoolean(PreferenceConstants.BELL_NOTIFICATION, false).apply()
        doCallRealMethod().`when`(manager).sendActivityNotification(bridgeA)
        manager.sendActivityNotification(bridgeA)

        verify(manager.connectionNotifier, never()).showActivityNotification(manager, NotificationSession("session-1", hostA, connected = true))
    }

    @Test
    @Config(sdk = [26, 34])
    fun systemChannelsAreNotOverriddenByLegacyPreferences() = runTest(dispatcher) {
        manager.prefs.edit()
            .putBoolean(PreferenceConstants.BELL_NOTIFICATION, false)
            .putBoolean(PreferenceConstants.CONNECTION_LOST_NOTIFICATION, false)
            .apply()
        doCallRealMethod().`when`(manager).sendActivityNotification(bridgeA)
        manager.sendActivityNotification(bridgeA)
        manager.onSessionEnded(bridgeA, DisconnectReason.IO_ERROR)
        advanceUntilIdle()

        val session = NotificationSession("session-1", hostA, connected = true)
        verify(manager.connectionNotifier).showActivityNotification(manager, session)
        verify(manager.connectionNotifier).sessionEnded(manager, session, DisconnectReason.IO_ERROR, true)
    }

    @Test
    fun staleReconnectActionDoesNotRestartANewerSession() = runTest(dispatcher) {
        doReturn(true).`when`(bridgeA).isDisconnected
        doNothing().`when`(manager).requestReconnect(bridgeA)
        manager.reconnectFromNotification(bridgeA, "previous-session")
        advanceUntilIdle()
        verify(manager, never()).requestReconnect(bridgeA)
    }

    @Test
    fun reconnectActionRestartsOnlyItsDisconnectedSession() = runTest(dispatcher) {
        doReturn(true).`when`(bridgeA).isDisconnected
        doNothing().`when`(manager).requestReconnect(bridgeA)
        manager.reconnectFromNotification(bridgeA, "session-1")
        advanceUntilIdle()
        verify(manager).requestReconnect(bridgeA)
    }

    private fun bridge(host: Host): TerminalBridge {
        val bridge = mock(TerminalBridge::class.java)
        `when`(bridge.host).thenReturn(host)
        `when`(bridge.notificationSessionId).thenReturn("session-${host.id}")
        return bridge
    }
}
