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

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.HostRepository
import org.connectbot.data.ProfileRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.SecurePasswordStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class HostEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)

    private lateinit var context: Context
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var repository: HostRepository
    private lateinit var pubkeyRepository: PubkeyRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var securePasswordStorage: SecurePasswordStorage

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()

        savedStateHandle = SavedStateHandle()
        repository = mock(HostRepository::class.java)
        pubkeyRepository = mock(PubkeyRepository::class.java)
        profileRepository = mock(ProfileRepository::class.java)
        prefs = mock(SharedPreferences::class.java)
        securePasswordStorage = mock(SecurePasswordStorage::class.java)

        // Mock default behavior for observe calls
        `when`(pubkeyRepository.observeAll()).thenReturn(flowOf(emptyList()))
        `when`(repository.observeSshHosts()).thenReturn(flowOf(emptyList()))
        `when`(repository.observeAutomation(org.mockito.ArgumentMatchers.anyLong())).thenReturn(flowOf(emptyList()))
        `when`(profileRepository.observeAll()).thenReturn(flowOf(emptyList()))
        `when`(prefs.getLong("defaultProfileId", 0L)).thenReturn(0L)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(hostId: Long = -1L): HostEditorViewModel {
        savedStateHandle["hostId"] = hostId
        return HostEditorViewModel(
            context = context,
            savedStateHandle = savedStateHandle,
            repository = repository,
            pubkeyRepository = pubkeyRepository,
            profileRepository = profileRepository,
            prefs = prefs,
            securePasswordStorage = securePasswordStorage,
            dispatchers = dispatchers,
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
    }

    @Test
    fun saveFailureKeepsUnsavedChanges() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateHostname("example.com")
        `when`(repository.saveHost(any(Host::class.java) ?: Host())).thenThrow(IllegalStateException("disk failed"))

        assertFalse(viewModel.saveHost())
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals("disk failed", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun testLoadExistingHost_populatesFields() = runTest {
        val hostId = 42L
        val existingHost = Host(
            id = hostId,
            nickname = "test-nick",
            protocol = "telnet",
            username = "test-user",
            hostname = "10.0.0.1",
            port = 23,
        )
        `when`(repository.findHostById(hostId)).thenReturn(existingHost)
        `when`(securePasswordStorage.hasPassword(hostId)).thenReturn(true)

        val viewModel = createViewModel(hostId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(hostId, state.hostId)
        assertEquals("test-nick", state.nickname)
        assertEquals("telnet", state.protocol)
        assertEquals("test-user", state.username)
        assertEquals("10.0.0.1", state.hostname)
        assertEquals("23", state.port)
        assertTrue(state.hasExistingPassword)
    }

    @Test
    fun testLoadExistingHost_matchingNickname_doesNotPopulateFieldsOnEdit() = runTest {
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

        viewModel.onNicknameFocusChanged(true)
        viewModel.updateNickname("other@new.example:2222")
        assertEquals("test-user", viewModel.uiState.value.username)
        assertEquals("10.0.0.1", viewModel.uiState.value.hostname)
        assertEquals("22", viewModel.uiState.value.port)
    }

    @Test
    fun testUpdateNickname_withConnectionSyntax_syncsFields() = runTest {
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
    fun testUpdateNickname_whenCustom_doesNotSyncFields() = runTest {
        val host = Host(
            id = 42L,
            nickname = "Production",
            username = "user",
            hostname = "192.168.1.1",
            port = 22,
        )
        `when`(repository.findHostById(host.id)).thenReturn(host)
        val viewModel = createViewModel(host.id)
        advanceUntilIdle()

        viewModel.onNicknameFocusChanged(true)
        viewModel.updateNickname("john@myhost:2222")
        viewModel.onNicknameFocusChanged(false)

        assertEquals("john@myhost:2222", viewModel.uiState.value.nickname)
        assertEquals("192.168.1.1", viewModel.uiState.value.hostname)
        assertEquals("user", viewModel.uiState.value.username)
        assertEquals("22", viewModel.uiState.value.port)
    }

    @Test
    fun testUpdateNickname_firstCreationEntry_recoversAfterIncompleteInput() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateNickname("user@example.com")
        viewModel.onNicknameFocusChanged(true)

        viewModel.updateNickname("admin@")
        assertEquals("user", viewModel.uiState.value.username)
        assertEquals("example.com", viewModel.uiState.value.hostname)

        viewModel.updateNickname("admin@other.example:")
        assertEquals("example.com", viewModel.uiState.value.hostname)
        viewModel.updateNickname("admin@other.example:2222")
        assertEquals("admin", viewModel.uiState.value.username)
        assertEquals("other.example", viewModel.uiState.value.hostname)
        assertEquals("2222", viewModel.uiState.value.port)
        viewModel.onNicknameFocusChanged(false)
    }

    @Test
    fun testUpdateNickname_afterFirstCreationEntry_neverPopulatesFieldsAgain() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onNicknameFocusChanged(true)
        viewModel.updateNickname("user@example.com:2222")
        viewModel.onNicknameFocusChanged(false)

        viewModel.onNicknameFocusChanged(true)
        viewModel.updateNickname("admin@other.example:8022")
        assertEquals("user", viewModel.uiState.value.username)
        assertEquals("example.com", viewModel.uiState.value.hostname)
        assertEquals("2222", viewModel.uiState.value.port)
        viewModel.updateNickname("")
        viewModel.onNicknameFocusChanged(false)
        viewModel.onNicknameFocusChanged(true)
        viewModel.updateNickname("root@third.example:22")
        assertEquals("user", viewModel.uiState.value.username)
        assertEquals("example.com", viewModel.uiState.value.hostname)
        assertEquals("2222", viewModel.uiState.value.port)
    }

    @Test
    fun testUpdateNickname_clearingFirstEntryDoesNotRestartAutofill() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onNicknameFocusChanged(true)
        viewModel.updateNickname("user@example.com:2222")
        viewModel.updateNickname("")
        viewModel.onNicknameFocusChanged(false)

        viewModel.onNicknameFocusChanged(true)
        viewModel.updateNickname("admin@other.example:8022")
        assertEquals("user", viewModel.uiState.value.username)
        assertEquals("example.com", viewModel.uiState.value.hostname)
        assertEquals("2222", viewModel.uiState.value.port)
    }

    @Test
    fun testUpdateNickname_afterManualConnectionEntry_doesNotPopulateFields() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateUsername("user")
        viewModel.updateHostname("example.com")
        viewModel.updatePort("2222")

        viewModel.onNicknameFocusChanged(true)
        viewModel.updateNickname("admin@other.example:8022")
        assertEquals("user", viewModel.uiState.value.username)
        assertEquals("example.com", viewModel.uiState.value.hostname)
        assertEquals("2222", viewModel.uiState.value.port)
    }

    @Test
    fun testMoshNickname_existingHostEditsAreIndependent() = runTest {
        val host = Host(
            id = 42L,
            nickname = "user@example.com:22",
            protocol = "mosh",
            username = "user",
            hostname = "example.com",
            port = 22,
        )
        `when`(repository.findHostById(host.id)).thenReturn(host)
        val viewModel = createViewModel(host.id)
        advanceUntilIdle()

        viewModel.updateUsername("admin")
        viewModel.updateHostname("other.example")
        viewModel.updatePort("2222")
        assertEquals(host.nickname, viewModel.uiState.value.nickname)
        viewModel.onNicknameFocusChanged(true)
        viewModel.updateNickname("root@[2001:db8::1]:8022")
        assertEquals("admin", viewModel.uiState.value.username)
        assertEquals("other.example", viewModel.uiState.value.hostname)
        assertEquals("2222", viewModel.uiState.value.port)
    }

    @Test
    fun testUpdateConnectionFieldsAndProtocol_neverRewriteNickname() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateNickname("user@192.168.1.1:22")

        viewModel.updateHostname("192.168.1.2")
        viewModel.updateUsername("admin")
        viewModel.updatePort("2222")
        viewModel.updateProtocol("telnet")
        assertEquals("user@192.168.1.1:22", viewModel.uiState.value.nickname)
    }

    @Test
    fun testUpdateHostFields_doesNotSyncNickname_whenNicknameWasCustom() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Start with nickname in sync
        viewModel.updateNickname("user@192.168.1.1")
        advanceUntilIdle()

        // Change nickname to a custom friendly name
        viewModel.updateNickname("My Custom Server")
        advanceUntilIdle()

        // Change hostname directly
        viewModel.updateHostname("192.168.1.2")
        advanceUntilIdle()

        // Custom nicknames should not change
        assertEquals("My Custom Server", viewModel.uiState.value.nickname)
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

        // Modify the explicit connection fields
        viewModel.updateHostname("10.0.0.2")
        viewModel.updatePort("2222")
        viewModel.updateUsername("new-user")
        advanceUntilIdle()

        viewModel.saveHost()
        advanceUntilIdle()

        // Capture saved host and verify it was updated
        val hostCaptor = ArgumentCaptor.forClass(Host::class.java)
        verify(repository).saveHost(hostCaptor.capture() ?: Host())
        val savedHost = hostCaptor.value

        assertEquals(hostId, savedHost.id)
        assertEquals("10.0.0.2", savedHost.hostname)
        assertEquals(2222, savedHost.port)
        assertEquals("new-user", savedHost.username)
        assertEquals(existingHost.nickname, savedHost.nickname) // Connection edits preserve the nickname
    }

    @Test
    fun testSaveHost_completesBeforeReturning() = runTest {
        val hostId = 42L
        val existingHost = Host(
            id = hostId,
            nickname = "test-user@10.0.0.1:555",
            protocol = "ssh",
            username = "test-user",
            hostname = "10.0.0.1",
            port = 555,
        )
        `when`(repository.findHostById(hostId)).thenReturn(existingHost)
        `when`(securePasswordStorage.hasPassword(hostId)).thenReturn(false)
        `when`(repository.saveHost(any(Host::class.java) ?: Host())).thenAnswer { invocation ->
            invocation.arguments[0] as Host
        }

        val viewModel = createViewModel(hostId)
        advanceUntilIdle()

        viewModel.updatePort("4022")
        viewModel.saveHost()

        val hostCaptor = ArgumentCaptor.forClass(Host::class.java)
        verify(repository).saveHost(hostCaptor.capture() ?: Host())
        assertEquals(4022, hostCaptor.value.port)
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

        viewModel.saveHost()
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
    }

    @Test
    fun testUpdatePort_blankPort_preservesNickname() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Syncs hostname/port. Set port to blank.
        viewModel.updateNickname("john@myhost")
        advanceUntilIdle()
        viewModel.updatePort("")
        advanceUntilIdle()

        assertEquals("john@myhost", viewModel.uiState.value.nickname)
    }

    @Test
    fun testSaveHost_blankPort_usesDefaultPort() = runTest {
        val hostId = 42L
        val existingHost = Host(
            id = hostId,
            nickname = "test-user@10.0.0.1",
            protocol = "telnet",
            username = "test-user",
            hostname = "10.0.0.1",
            port = 23,
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

        viewModel.saveHost()
        advanceUntilIdle()

        val hostCaptor = ArgumentCaptor.forClass(Host::class.java)
        verify(repository).saveHost(hostCaptor.capture() ?: Host())
        val savedHost = hostCaptor.value

        assertEquals(23, savedHost.port) // Falls back to telnet default port 23
    }

    @Test
    fun testLocalProtocol_doesNotSyncNickname() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateProtocol("local")
        viewModel.updateNickname("my-local-shell")
        advanceUntilIdle()

        // Changing hostname or username should not change a local nickname
        viewModel.updateHostname("somehost")
        viewModel.updateUsername("someuser")
        advanceUntilIdle()

        assertEquals("my-local-shell", viewModel.uiState.value.nickname)
    }

    @Test
    fun testLoadExistingHost_preservesExplicitDefaultPortInNickname() = runTest {
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

        assertEquals("test-user@10.0.0.1:22", viewModel.uiState.value.nickname)
    }

    @Test
    fun testSaveHost_persistsNicknameAndAdvancedValuesDuringFirstEntry() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        `when`(repository.saveHost(any(Host::class.java) ?: Host())).thenAnswer { invocation ->
            invocation.arguments[0] as Host
        }

        viewModel.onNicknameFocusChanged(true)
        viewModel.updateNickname("admin@")
        viewModel.updateNickname("admin@example.com:22")
        viewModel.updateIpVersion("IPV4_ONLY")
        viewModel.updateMoshPort("60001")
        viewModel.updateMoshServer("custom-mosh-server")
        viewModel.updateLocale("de_DE.UTF-8")

        assertTrue(viewModel.saveHost())
        val hostCaptor = ArgumentCaptor.forClass(Host::class.java)
        verify(repository).saveHost(hostCaptor.capture() ?: Host())
        val savedHost = hostCaptor.value
        assertEquals("admin@example.com:22", savedHost.nickname)
        assertEquals("admin", savedHost.username)
        assertEquals("example.com", savedHost.hostname)
        assertEquals(22, savedHost.port)
        assertEquals("IPV4_ONLY", savedHost.ipVersion)
        assertEquals(60001, savedHost.moshPort)
        assertEquals("custom-mosh-server", savedHost.moshServer)
        assertEquals("de_DE.UTF-8", savedHost.locale)
    }

    @Test
    fun testUpdateProtocol_toMosh_updatesProtocol() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateProtocol("mosh")
        assertEquals("mosh", viewModel.uiState.value.protocol)
    }

    @Test
    fun testCancelMoshInstall_revertsProtocolAndStopsInstalling() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateProtocol("telnet")
        advanceUntilIdle()
        assertEquals("telnet", viewModel.uiState.value.protocol)

        viewModel.updateProtocol("mosh")
        viewModel.cancelMoshInstall()

        assertEquals("telnet", viewModel.uiState.value.protocol)
        assertFalse(viewModel.uiState.value.isMoshInstalling)
    }
}
