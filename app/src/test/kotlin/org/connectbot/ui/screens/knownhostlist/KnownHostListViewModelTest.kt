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

package org.connectbot.ui.screens.knownhostlist

import androidx.lifecycle.SavedStateHandle
import com.trilead.ssh2.crypto.fingerprint.KeyFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KnownHost
import org.connectbot.di.CoroutineDispatchers
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class KnownHostListViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )
    private lateinit var repository: HostRepository
    private lateinit var knownHostsFlow: MutableStateFlow<List<KnownHost>>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = Mockito.mock(HostRepository::class.java)
        knownHostsFlow = MutableStateFlow(emptyList())
        Mockito.`when`(repository.observeKnownHostsForHost(HOST_ID)).thenReturn(knownHostsFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun knownHostsAreShownWithSha256Fingerprint() = runTest {
        val knownHost = testKnownHost()
        knownHostsFlow.value = listOf(knownHost)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.knownHosts.size)
        assertEquals("example.com:22", state.knownHosts.single().endpoint)
        assertEquals(
            KeyFingerprint.createSHA256Fingerprint(knownHost.hostKey),
            state.knownHosts.single().fingerprint,
        )
    }

    @Test
    fun deleteKnownHostDelegatesToRepository() = runTest {
        val knownHost = testKnownHost()
        val viewModel = createViewModel()

        viewModel.deleteKnownHost(knownHost)
        advanceUntilIdle()

        Mockito.verify(repository).deleteKnownHost(knownHost)
    }

    @Test
    fun importKnownHostSavesFullOpenSshPublicKey() = runTest {
        val host = Host(
            id = HOST_ID,
            nickname = "example",
            protocol = "ssh",
            hostname = "example.com",
            port = 2222,
        )
        Mockito.`when`(repository.findHostById(HOST_ID)).thenReturn(host)
        val viewModel = createViewModel()

        assertEquals(true, viewModel.importKnownHost(VALID_ED25519_KEY))
        advanceUntilIdle()

        val parsed = checkNotNull(parseOpenSshHostKey(VALID_ED25519_KEY))
        val invocation = Mockito.mockingDetails(repository).invocations.single {
            it.method.name == "saveKnownHost"
        }
        assertEquals(host, invocation.arguments[0])
        assertEquals(host.hostname, invocation.arguments[1])
        assertEquals(host.port, invocation.arguments[2])
        assertEquals(parsed.algorithm, invocation.arguments[3])
        assertArrayEquals(parsed.keyBlob, invocation.arguments[4] as ByteArray)
    }

    @Test
    fun fingerprintAloneCannotBeImported() = runTest {
        val viewModel = createViewModel()

        assertEquals(false, viewModel.importKnownHost("SHA256:abc123"))
        assertEquals(KnownHostImportError.INVALID_KEY, viewModel.uiState.value.importError)
    }

    private fun createViewModel() = KnownHostListViewModel(
        savedStateHandle = SavedStateHandle(mapOf("hostId" to HOST_ID)),
        repository = repository,
        dispatchers = dispatchers,
    )

    private fun testKnownHost() = KnownHost(
        id = 7,
        hostId = 3,
        hostname = "example.com",
        port = 22,
        hostKeyAlgo = "ssh-ed25519",
        hostKey = "test-host-key".toByteArray(),
    )

    private companion object {
        const val HOST_ID = 3L
        const val VALID_ED25519_KEY =
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIEwaKyyFAR6BJpPCKMBKGZtN0SGOpxLWtJg7cz2Wu3+v delivered@example"
    }
}
