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

package org.connectbot.ui.screens.hosteditor

import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.HostRepository
import org.connectbot.data.KeyboardLayoutRepository
import org.connectbot.data.ProfileRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.util.SecurePasswordStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class HostEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var repository: HostRepository
    private lateinit var pubkeyRepository: PubkeyRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var keyboardLayoutRepository: KeyboardLayoutRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var securePasswordStorage: SecurePasswordStorage

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        savedStateHandle = SavedStateHandle()
        repository = mock(HostRepository::class.java)
        pubkeyRepository = mock(PubkeyRepository::class.java)
        profileRepository = mock(ProfileRepository::class.java)
        keyboardLayoutRepository = mock(KeyboardLayoutRepository::class.java)
        prefs = mock(SharedPreferences::class.java)
        securePasswordStorage = mock(SecurePasswordStorage::class.java)

        // Mock default behavior for observe calls
        `when`(pubkeyRepository.observeAll()).thenReturn(flowOf(emptyList()))
        `when`(repository.observeSshHosts()).thenReturn(flowOf(emptyList()))
        `when`(profileRepository.observeAll()).thenReturn(flowOf(emptyList()))
        `when`(keyboardLayoutRepository.observeAll()).thenReturn(flowOf(emptyList()))
        `when`(prefs.getLong("defaultProfileId", 0L)).thenReturn(0L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(hostId: Long = -1L): HostEditorViewModel {
        savedStateHandle["hostId"] = hostId
        return HostEditorViewModel(
            savedStateHandle = savedStateHandle,
            repository = repository,
            pubkeyRepository = pubkeyRepository,
            profileRepository = profileRepository,
            keyboardLayoutRepository = keyboardLayoutRepository,
            prefs = prefs,
            securePasswordStorage = securePasswordStorage,
        )
    }

    @Test
    fun testNewHost_initializesWithDefaultState() = runTest {
        val viewModel = createViewModel(-1L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(-1L, state.hostId)
        assertEquals("", state.nickname)
        assertEquals("ssh", state.protocol)
        assertEquals("", state.username)
        assertEquals("", state.hostname)
        assertEquals("22", state.port)
        assertTrue(state.isNicknameMatching)
    }

    @Test
    fun testLoadExistingHost_populatesFields() = runTest {
        val hostId = 42L
        val existingHost = Host(
            id = hostId,
            nickname = "test-nick",
            protocol = "ssh",
            username = "test-user",
            hostname = "10.0.0.1",
            port = 22,
        )
        `when`(repository.findHostById(hostId)).thenReturn(existingHost)
        `when`(securePasswordStorage.hasPassword(hostId)).thenReturn(true)

        val viewModel = createViewModel(hostId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(hostId, state.hostId)
        assertEquals("test-nick", state.nickname)
        assertEquals("ssh", state.protocol)
        assertEquals("test-user", state.username)
        assertEquals("10.0.0.1", state.hostname)
        assertEquals("22", state.port)
        assertEquals("test-user@10.0.0.1", state.quickConnect) // Verify quickConnect is populated
        assertTrue(state.hasExistingPassword)
        assertFalse(state.isNicknameMatching)
    }

    @Test
    fun testLoadExistingHost_matchingNickname_isNicknameMatchingIsTrue() = runTest {
        val hostId = 42L
        val existingHost = Host(
            id = hostId,
            nickname = "test-user@10.0.0.1:22",
            protocol = "ssh",
            username = "test-user",
            hostname = "10.0.0.1",
            port = 22,
        )
        `when`(repository.findHostById(hostId)).thenReturn(existingHost)
        `when`(securePasswordStorage.hasPassword(hostId)).thenReturn(false)

        val viewModel = createViewModel(hostId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isNicknameMatching)
    }

    @Test
    fun testUpdateNickname_withValidQuickConnect_syncsFields() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Syncs hostname
        viewModel.updateNickname("192.168.1.100")
        advanceUntilIdle()
        assertEquals("192.168.1.100", viewModel.uiState.value.hostname)
        assertEquals("", viewModel.uiState.value.username)
        assertEquals("22", viewModel.uiState.value.port)

        // Syncs username, hostname, port
        viewModel.updateNickname("john@myhost:2222")
        advanceUntilIdle()
        assertEquals("myhost", viewModel.uiState.value.hostname)
        assertEquals("john", viewModel.uiState.value.username)
        assertEquals("2222", viewModel.uiState.value.port)
    }

    @Test
    fun testUpdateNickname_withFriendlyName_doesNotOverwriteFields() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Set explicit host settings first
        viewModel.updateHostname("192.168.1.1")
        viewModel.updateUsername("user")
        viewModel.updatePort("22")
        advanceUntilIdle()

        // Change nickname to a friendly label containing spaces
        viewModel.updateNickname("My Home Server")
        advanceUntilIdle()

        // Should keep old hostname, username, port
        assertEquals("My Home Server", viewModel.uiState.value.nickname)
        assertEquals("192.168.1.1", viewModel.uiState.value.hostname)
        assertEquals("user", viewModel.uiState.value.username)
        assertEquals("22", viewModel.uiState.value.port)
    }

    @Test
    fun testUpdateNickname_whenExpanded_doesNotSyncFields() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Set explicit host settings first
        viewModel.updateHostname("192.168.1.1")
        viewModel.updateUsername("user")
        viewModel.updatePort("22")
        advanceUntilIdle()

        // Change nickname with isExpanded = true
        viewModel.updateNickname("john@myhost:2222", isExpanded = true)
        advanceUntilIdle()

        // Should keep old hostname, username, port, but update nickname
        assertEquals("john@myhost:2222", viewModel.uiState.value.nickname)
        assertEquals("192.168.1.1", viewModel.uiState.value.hostname)
        assertEquals("user", viewModel.uiState.value.username)
        assertEquals("22", viewModel.uiState.value.port)
    }

    @Test
    fun testUpdateHostFields_syncsNickname_whenNicknameWasMatching() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Start with nickname in sync with default host/port representation
        viewModel.updateQuickConnect("user@192.168.1.1:22")
        advanceUntilIdle()

        // Change hostname directly
        viewModel.updateHostname("192.168.1.2")
        advanceUntilIdle()

        // Nickname and quickConnect should automatically update
        assertEquals("user@192.168.1.2", viewModel.uiState.value.nickname)
        assertEquals("user@192.168.1.2", viewModel.uiState.value.quickConnect)

        // Change port directly
        viewModel.updatePort("2222")
        advanceUntilIdle()

        assertEquals("user@192.168.1.2:2222", viewModel.uiState.value.nickname)
        assertEquals("user@192.168.1.2:2222", viewModel.uiState.value.quickConnect)
    }

    @Test
    fun testUpdateHostFields_doesNotSyncNickname_whenNicknameWasCustom() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Start with nickname in sync
        viewModel.updateQuickConnect("user@192.168.1.1")
        advanceUntilIdle()

        // Change nickname to a custom friendly name
        viewModel.updateNickname("My Custom Server")
        advanceUntilIdle()

        // Change hostname directly
        viewModel.updateHostname("192.168.1.2")
        advanceUntilIdle()

        // Nickname should NOT change, but quickConnect should change
        assertEquals("My Custom Server", viewModel.uiState.value.nickname)
        assertEquals("user@192.168.1.2", viewModel.uiState.value.quickConnect)
    }

    @Test
    fun testSaveHost_persistsUpdatedValues() = runTest {
        val hostId = 42L
        val existingHost = Host(
            id = hostId,
            nickname = "test-user@10.0.0.1",
            protocol = "ssh",
            username = "test-user",
            hostname = "10.0.0.1",
            port = 22,
        )
        `when`(repository.findHostById(hostId)).thenReturn(existingHost)
        `when`(securePasswordStorage.hasPassword(hostId)).thenReturn(false)
        // Mock saveHost to return the saved host (needed for password handling)
        `when`(repository.saveHost(any(Host::class.java) ?: Host())).thenAnswer { invocation ->
            invocation.arguments[0] as Host
        }

        val viewModel = createViewModel(hostId)
        advanceUntilIdle()

        // Modify host values in Expanded Mode (default for editing)
        viewModel.updateHostname("10.0.0.2")
        viewModel.updatePort("2222")
        viewModel.updateUsername("new-user")
        advanceUntilIdle()

        viewModel.saveHost(useExpandedMode = true)
        advanceUntilIdle()

        // Capture saved host and verify it was updated
        val hostCaptor = ArgumentCaptor.forClass(Host::class.java)
        verify(repository).saveHost(hostCaptor.capture() ?: Host())
        val savedHost = hostCaptor.value

        assertEquals(hostId, savedHost.id)
        assertEquals("10.0.0.2", savedHost.hostname)
        assertEquals(2222, savedHost.port)
        assertEquals("new-user", savedHost.username)
        assertEquals("new-user@10.0.0.2:2222", savedHost.nickname) // Sync updated nickname
    }

    @Test
    fun testSaveHost_customNickname_doesNotSync() = runTest {
        val hostId = 42L
        val existingHost = Host(
            id = hostId,
            nickname = "My Custom Server",
            protocol = "ssh",
            username = "test-user",
            hostname = "10.0.0.1",
            port = 22,
        )
        `when`(repository.findHostById(hostId)).thenReturn(existingHost)
        `when`(securePasswordStorage.hasPassword(hostId)).thenReturn(false)
        `when`(repository.saveHost(any(Host::class.java) ?: Host())).thenAnswer { invocation ->
            invocation.arguments[0] as Host
        }

        val viewModel = createViewModel(hostId)
        advanceUntilIdle()

        viewModel.updateHostname("10.0.0.2")
        advanceUntilIdle()

        viewModel.saveHost(useExpandedMode = true)
        advanceUntilIdle()

        val hostCaptor = ArgumentCaptor.forClass(Host::class.java)
        verify(repository).saveHost(hostCaptor.capture() ?: Host())
        val savedHost = hostCaptor.value

        assertEquals("My Custom Server", savedHost.nickname) // Nickname untouched
        assertEquals("10.0.0.2", savedHost.hostname)
    }

    @Test
    fun testUpdateNickname_withIPv6_syncsFields() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateNickname("[2001:db8::1]")
        advanceUntilIdle()
        assertEquals("[2001:db8::1]", viewModel.uiState.value.hostname)
        assertEquals("", viewModel.uiState.value.username)
        assertEquals("22", viewModel.uiState.value.port)
        assertEquals("[2001:db8::1]", viewModel.uiState.value.quickConnect)
    }

    @Test
    fun testUpdateNickname_withIPv6AndPort_syncsFields() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateNickname("admin@[2001:db8::1]:8022")
        advanceUntilIdle()
        assertEquals("[2001:db8::1]", viewModel.uiState.value.hostname)
        assertEquals("admin", viewModel.uiState.value.username)
        assertEquals("8022", viewModel.uiState.value.port)
        assertEquals("admin@[2001:db8::1]:8022", viewModel.uiState.value.quickConnect)
    }

    @Test
    fun testUpdateQuickConnect_withIPv6_syncsFields() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateQuickConnect("[2001:db8::1]")
        advanceUntilIdle()
        assertEquals("[2001:db8::1]", viewModel.uiState.value.hostname)
        assertEquals("", viewModel.uiState.value.username)
        assertEquals("22", viewModel.uiState.value.port)
        assertEquals("[2001:db8::1]", viewModel.uiState.value.nickname)
    }

    @Test
    fun testUpdateQuickConnect_withIPv6AndPort_syncsFields() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateQuickConnect("admin@[2001:db8::1]:8022")
        advanceUntilIdle()
        assertEquals("[2001:db8::1]", viewModel.uiState.value.hostname)
        assertEquals("admin", viewModel.uiState.value.username)
        assertEquals("8022", viewModel.uiState.value.port)
        assertEquals("admin@[2001:db8::1]:8022", viewModel.uiState.value.nickname)
    }

    @Test
    fun testIsNicknameMatching_blankPort_isMatching() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Syncs hostname/port. Set port to blank.
        viewModel.updateQuickConnect("john@myhost")
        advanceUntilIdle()
        viewModel.updatePort("")
        advanceUntilIdle()

        // Nickname should still be matching
        assertTrue(viewModel.uiState.value.isNicknameMatching)
    }

    @Test
    fun testSaveHost_blankPort_usesDefaultPort() = runTest {
        val hostId = 42L
        val existingHost = Host(
            id = hostId,
            nickname = "test-user@10.0.0.1",
            protocol = "ssh",
            username = "test-user",
            hostname = "10.0.0.1",
            port = 22,
        )
        `when`(repository.findHostById(hostId)).thenReturn(existingHost)
        `when`(securePasswordStorage.hasPassword(hostId)).thenReturn(false)
        `when`(repository.saveHost(any(Host::class.java) ?: Host())).thenAnswer { invocation ->
            invocation.arguments[0] as Host
        }

        val viewModel = createViewModel(hostId)
        advanceUntilIdle()

        viewModel.updatePort("") // Clear the port
        advanceUntilIdle()

        viewModel.saveHost(useExpandedMode = true)
        advanceUntilIdle()

        val hostCaptor = ArgumentCaptor.forClass(Host::class.java)
        verify(repository).saveHost(hostCaptor.capture() ?: Host())
        val savedHost = hostCaptor.value

        assertEquals(22, savedHost.port) // Falls back to ssh default port 22
    }

    @Test
    fun testKeyboardSuggestions_loadsAndSaves() = runTest {
        val hostId = 42L
        val existingHost = Host(
            id = hostId,
            nickname = "test-user@10.0.0.1",
            protocol = "ssh",
            username = "test-user",
            hostname = "10.0.0.1",
            port = 22,
            keyboardSuggestions = true,
        )
        `when`(repository.findHostById(hostId)).thenReturn(existingHost)
        `when`(securePasswordStorage.hasPassword(hostId)).thenReturn(false)
        `when`(repository.saveHost(any(Host::class.java) ?: Host())).thenAnswer { invocation ->
            invocation.arguments[0] as Host
        }

        val viewModel = createViewModel(hostId)
        advanceUntilIdle()

        // Loaded from the host entity
        assertTrue(viewModel.uiState.value.keyboardSuggestions)

        viewModel.updateKeyboardSuggestions(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.keyboardSuggestions)

        viewModel.saveHost(useExpandedMode = true)
        advanceUntilIdle()

        val hostCaptor = ArgumentCaptor.forClass(Host::class.java)
        verify(repository).saveHost(hostCaptor.capture() ?: Host())
        assertFalse(hostCaptor.value.keyboardSuggestions)
    }

    @Test
    fun testLoadExistingHost_matchingNickname_initializesQuickConnectToNickname() = runTest {
        val hostId = 42L
        val existingHost = Host(
            id = hostId,
            nickname = "test-user@10.0.0.1:22",
            protocol = "ssh",
            username = "test-user",
            hostname = "10.0.0.1",
            port = 22,
        )
        `when`(repository.findHostById(hostId)).thenReturn(existingHost)
        `when`(securePasswordStorage.hasPassword(hostId)).thenReturn(false)

        val viewModel = createViewModel(hostId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("test-user@10.0.0.1:22", state.quickConnect)
    }
}
