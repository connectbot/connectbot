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

package org.connectbot.ui.screens.hostlist

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.DisconnectReason
import org.connectbot.service.ServiceError
import org.connectbot.service.SessionAttention
import org.connectbot.service.SessionAttentionState
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HostConnectionStateTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun viewModelUpdatesBadgeAndReasonWhenSessionIsReviewed() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val host = Host(id = 1L, nickname = "dropped")
            val attention = SessionAttention()
            attention.onOutput()
            attention.onEnded(DisconnectReason.NETWORK_LOST)
            val bridge = mock(TerminalBridge::class.java)
            `when`(bridge.host).thenReturn(host)
            `when`(bridge.disconnected).thenReturn(true)
            val manager = mock(TerminalManager::class.java)
            val events = MutableSharedFlow<Unit>()
            `when`(manager.bridgesFlow).thenReturn(MutableStateFlow(listOf(bridge)))
            `when`(manager.hostStatusChangedFlow).thenReturn(events)
            `when`(manager.serviceErrors).thenReturn(MutableSharedFlow<ServiceError>())
            `when`(manager.pendingStartupKeyPrompts).thenReturn(MutableStateFlow(emptyList()))
            `when`(manager.getSessionAttention(host.id)).thenAnswer { attention.snapshot() }
            val repository = mock(HostRepository::class.java)
            `when`(repository.observeHosts()).thenReturn(MutableStateFlow(listOf(host)))
            val viewModel = HostListViewModel(
                mock(Context::class.java),
                repository,
                CoroutineDispatchers(default = dispatcher, io = dispatcher, main = dispatcher),
                mock(SharedPreferences::class.java),
            )
            viewModel.setTerminalManager(manager)
            assertEquals(ConnectionState.UNREAD_OUTPUT, viewModel.uiState.value.connectionStates[host.id])
            assertEquals(DisconnectReason.NETWORK_LOST, viewModel.uiState.value.disconnectReasons[host.id])

            attention.onOpened()
            events.emit(Unit)
            assertEquals(ConnectionState.DISCONNECTED, viewModel.uiState.value.connectionStates[host.id])

            attention.onConsoleClosed()
            events.emit(Unit)
            assertEquals(ConnectionState.UNKNOWN, viewModel.uiState.value.connectionStates[host.id])
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun userDisconnectIsNeutralEvenWithUnreadOutput() {
        assertEquals(
            ConnectionState.DISCONNECTED,
            SessionAttentionState(reason = DisconnectReason.USER_REQUESTED, unreadOutput = true).connectionState(),
        )
    }

    @Test
    fun intentionalDisconnectKeepsDistinctNeutralIconAfterConsoleCloses() {
        val attention = SessionAttention()
        attention.onOpened()
        attention.onEnded(DisconnectReason.USER_REQUESTED)
        attention.onConsoleClosed()
        assertEquals(ConnectionState.DISCONNECTED, attention.snapshot().connectionState())
    }

    @Test
    fun recoverableDisconnectsNeverShowRed() {
        for (reason in DisconnectReason.entries.filter { it != DisconnectReason.AUTH_FAIL }) {
            assertEquals(ConnectionState.DISCONNECTED, SessionAttentionState(reason = reason).connectionState())
        }
    }

    @Test
    fun unreadDropTurnsNeutralOnOpenAndClearsOnClose() {
        val attention = SessionAttention()
        attention.onOutput()
        attention.onEnded(DisconnectReason.NETWORK_LOST)
        assertEquals(ConnectionState.UNREAD_OUTPUT, attention.snapshot().connectionState())
        attention.onOpened()
        assertEquals(ConnectionState.DISCONNECTED, attention.snapshot().connectionState())
        attention.onConsoleClosed()
        assertEquals(ConnectionState.UNKNOWN, attention.snapshot().connectionState())
    }

    @Test
    fun authFailureStaysRedUntilReconnectEvenAfterReading() {
        val attention = SessionAttention()
        attention.onOutput()
        attention.onEnded(DisconnectReason.AUTH_FAIL)
        assertEquals(ConnectionState.ERROR, attention.snapshot().connectionState())
        attention.onOpened()
        attention.onConsoleClosed()
        assertEquals(ConnectionState.ERROR, attention.snapshot().connectionState())
        attention.onReconnected()
        assertEquals(ConnectionState.UNKNOWN, attention.snapshot().connectionState())
    }

    @Test
    fun lateOutputNeedsAttentionAfterAcknowledgement() {
        val attention = SessionAttention()
        attention.onEnded(DisconnectReason.REMOTE_EOF)
        attention.onOpened()
        attention.onConsoleClosed()
        attention.onOutput()
        assertEquals(ConnectionState.UNREAD_OUTPUT, attention.snapshot().connectionState())
    }
}
