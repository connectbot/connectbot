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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

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
        doReturn(true).`when`(manager).isUiVisible
        doNothing().`when`(manager).playBeep()
        bridgeA = bridge(hostA)
        bridgeB = bridge(hostB)
        doNothing().`when`(manager).sendActivityNotification(hostA)
        doNothing().`when`(manager).sendActivityNotification(hostB)
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
        verify(manager, never()).sendActivityNotification(hostB)
    }

    @Test
    fun otherHostNotifiesWithOriginatingHost() = runTest(dispatcher) {
        manager.setVisibleConsole(Any(), bridgeB)

        manager.onBell(bridgeA)
        advanceUntilIdle()

        verify(manager).sendActivityNotification(hostA)
        verify(manager, never()).sendActivityNotification(hostB)
        verify(manager, never()).playBeep()
    }

    @Test
    fun leavingConsoleNotifiesInsteadOfPlayingBell() = runTest(dispatcher) {
        val owner = Any()
        manager.setVisibleConsole(owner, bridgeA)
        manager.clearVisibleConsole(owner)

        manager.onBell(bridgeA)
        advanceUntilIdle()

        verify(manager).sendActivityNotification(hostA)
        verify(manager, never()).playBeep()
    }

    @Test
    fun backgroundBellNotifiesEvenBeforeConsoleCleanup() = runTest(dispatcher) {
        manager.setVisibleConsole(Any(), bridgeA)
        doReturn(false).`when`(manager).isUiVisible

        manager.onBell(bridgeA)
        advanceUntilIdle()

        verify(manager).sendActivityNotification(hostA)
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

        verify(manager).sendActivityNotification(hostA)
        verify(manager, never()).sendActivityNotification(hostB)
        verify(manager).playBeep()
    }

    private fun bridge(host: Host): TerminalBridge {
        val bridge = mock(TerminalBridge::class.java)
        `when`(bridge.host).thenReturn(host)
        return bridge
    }
}
