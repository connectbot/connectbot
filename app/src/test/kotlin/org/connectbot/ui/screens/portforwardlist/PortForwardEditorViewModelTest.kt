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

package org.connectbot.ui.screens.portforwardlist

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.transport.AbsTransport
import org.connectbot.util.HostConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class PortForwardEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
    private lateinit var repository: HostRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mock(HostRepository::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sourceAddressOptionsMapSavedValues() {
        assertEquals(SourceAddressOption.LOCALHOST, SourceAddressOption.fromSshValue("localhost"))
        assertEquals(SourceAddressOption.ALL, SourceAddressOption.fromSshValue(""))
        assertEquals(SourceAddressOption.SPECIFIC, SourceAddressOption.fromSshValue("192.168.1.10"))
    }

    @Test
    fun newForwardSavesUntouchedDefaults() = runTest {
        val forward = PortForward(
            hostId = 5L,
            nickname = "",
            type = HostConstants.PORTFORWARD_LOCAL,
            sourcePort = 8080,
            destAddr = "localhost",
            destPort = 80,
        )
        `when`(repository.savePortForward(forward)).thenReturn(forward.copy(id = 7L))
        val viewModel = PortForwardEditorViewModel(
            SavedStateHandle(mapOf("hostId" to 5L, "forwardId" to 0L)),
            repository,
            dispatchers,
        )

        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertFalse(viewModel.uiState.value.hasEdited)
        assertTrue(viewModel.uiState.value.canSave)
        var navigated = false
        viewModel.save { navigated = true }

        verify(repository).savePortForward(forward)
        assertTrue(navigated)
    }

    @Test
    fun existingForwardStartsCleanAndRejectsInvalidPort() = runTest {
        val forward = PortForward(
            id = 9L,
            hostId = 5L,
            nickname = "web",
            type = HostConstants.PORTFORWARD_LOCAL,
            sourcePort = 8080,
            destAddr = "localhost",
            destPort = 80,
        )
        `when`(repository.getPortForwardById(9L)).thenReturn(forward)
        val viewModel = PortForwardEditorViewModel(
            SavedStateHandle(mapOf("hostId" to 5L, "forwardId" to 9L)),
            repository,
            dispatchers,
        )

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals("web", viewModel.uiState.value.nickname)
        viewModel.updateSourcePort("0")
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertTrue(viewModel.uiState.value.hasEdited)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun updatingLiveForwardPreservesEnabledState() = runTest {
        val original = PortForward(
            id = 9L,
            hostId = 5L,
            nickname = "web",
            type = HostConstants.PORTFORWARD_LOCAL,
            sourcePort = 8080,
            destAddr = "localhost",
            destPort = 80,
        )
        val updated = original.copy(nickname = "web2")
        `when`(repository.getPortForwardById(9L)).thenReturn(original)
        `when`(repository.savePortForward(updated)).thenReturn(updated)

        val bridgeForward = original.copy()
        bridgeForward.setEnabled(true)
        val host = Host(id = 5L, nickname = "host", protocol = "ssh", username = "user", hostname = "example.com", port = 22)
        val transport = mock(AbsTransport::class.java)
        `when`(transport.isConnected()).thenReturn(true)
        val bridge = mock(TerminalBridge::class.java)
        `when`(bridge.host).thenReturn(host)
        `when`(bridge.transport).thenReturn(transport)
        `when`(bridge.portForwards).thenReturn(listOf(bridgeForward))
        val manager = mock(TerminalManager::class.java)
        `when`(manager.bridgesFlow).thenReturn(MutableStateFlow(listOf(bridge)))

        val viewModel = PortForwardEditorViewModel(
            SavedStateHandle(mapOf("hostId" to 5L, "forwardId" to 9L)),
            repository,
            dispatchers,
        )
        viewModel.setTerminalManager(manager)
        viewModel.updateNickname("web2")
        viewModel.save {}

        verify(transport).removePortForward(bridgeForward)
        verify(transport).addPortForward(updated)
        verify(transport).enablePortForward(updated)
    }

    @Test
    fun failedSaveKeepsDraftAndDoesNotNavigate() = runTest {
        val forward = PortForward(
            hostId = 5L,
            nickname = "",
            type = HostConstants.PORTFORWARD_LOCAL,
            sourcePort = 8080,
            destAddr = "localhost",
            destPort = 80,
        )
        `when`(repository.savePortForward(forward)).thenThrow(IllegalStateException("disk failed"))
        val viewModel = PortForwardEditorViewModel(
            SavedStateHandle(mapOf("hostId" to 5L, "forwardId" to 0L)),
            repository,
            dispatchers,
        )

        var navigated = false
        viewModel.save { navigated = true }

        assertFalse(navigated)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals("disk failed", viewModel.uiState.value.error)
    }

    @Test
    fun remoteForwardSavesSpecificSourceAddress() = runTest {
        val saved = PortForward(
            hostId = 5L,
            nickname = "admin",
            type = HostConstants.PORTFORWARD_REMOTE,
            sourceAddr = "192.168.1.10",
            sourcePort = 2200,
            destAddr = "server.example",
            destPort = 22,
        )
        `when`(repository.savePortForward(saved)).thenReturn(saved.copy(id = 12L))
        val viewModel = PortForwardEditorViewModel(
            SavedStateHandle(mapOf("hostId" to 5L, "forwardId" to 0L)),
            repository,
            dispatchers,
        )
        viewModel.updateNickname("admin")
        viewModel.updateType(HostConstants.PORTFORWARD_REMOTE)
        viewModel.updateSourceAddressOption(SourceAddressOption.SPECIFIC)
        viewModel.updateSpecificAddress("192.168.1.10")
        viewModel.updateSourcePort("2200")
        viewModel.updateDestination("server.example:22")

        assertTrue(viewModel.uiState.value.canSave)
        viewModel.save {}
        verify(repository).savePortForward(saved)
    }
}
