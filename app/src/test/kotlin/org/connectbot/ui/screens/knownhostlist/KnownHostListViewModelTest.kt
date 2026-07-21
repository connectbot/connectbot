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
import org.connectbot.data.entity.KnownHost
import org.connectbot.di.CoroutineDispatchers
import org.junit.After
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
        Mockito.`when`(repository.observeKnownHosts()).thenReturn(knownHostsFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun knownHostsAreShownWithSha256Fingerprint() = runTest {
        val knownHost = testKnownHost()
        knownHostsFlow.value = listOf(knownHost)

        val viewModel = KnownHostListViewModel(repository, dispatchers)
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
        val viewModel = KnownHostListViewModel(repository, dispatchers)

        viewModel.deleteKnownHost(knownHost)
        advanceUntilIdle()

        Mockito.verify(repository).deleteKnownHost(knownHost)
    }

    private fun testKnownHost() = KnownHost(
        id = 7,
        hostId = 3,
        hostname = "example.com",
        port = 22,
        hostKeyAlgo = "ssh-ed25519",
        hostKey = "test-host-key".toByteArray(),
    )
}
