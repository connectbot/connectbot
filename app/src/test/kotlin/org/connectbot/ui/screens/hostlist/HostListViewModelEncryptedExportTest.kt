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
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.EncryptedExportBundle
import org.connectbot.data.ExportCounts
import org.connectbot.data.HostRepository
import org.connectbot.data.ImportCounts
import org.connectbot.data.WrongPassphraseException
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.PreferenceConstants
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

/**
 * Tests for the encrypted export/import flows in HostListViewModel.
 *
 * Runs with Robolectric so org.json is available for the encrypted bundle
 * detection in importHosts().
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HostListViewModelEncryptedExportTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )
    private lateinit var context: Context
    private lateinit var repository: HostRepository
    private lateinit var sharedPreferences: SharedPreferences

    private val encryptedBundle = JSONObject()
        .put("format", EncryptedExportBundle.FORMAT)
        .put("version", EncryptedExportBundle.VERSION)
        .toString()

    private val importCounts = ImportCounts(
        hostsImported = 2,
        hostsSkipped = 1,
        profilesImported = 1,
        profilesSkipped = 0,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mock()
        repository = mock()
        sharedPreferences = mock()

        whenever(repository.observeHosts()).thenReturn(MutableStateFlow(emptyList<Host>()))
        whenever(sharedPreferences.getBoolean(PreferenceConstants.SORT_BY_COLOR, false))
            .thenReturn(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HostListViewModel = HostListViewModel(context, repository, dispatchers, sharedPreferences)

    @Test
    fun exportHostsEncrypted_marksExportAsEncrypted() = runTest {
        wheneverBlocking { repository.exportHostsToEncryptedBundle(any()) }
            .thenReturn(Pair(encryptedBundle, ExportCounts(hostCount = 3, profileCount = 2)))
        val viewModel = createViewModel()

        viewModel.exportHostsEncrypted("hunter2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.exportedJson).isEqualTo(encryptedBundle)
        assertThat(state.exportedEncrypted).isTrue()
        assertThat(state.exportResult).isEqualTo(ExportResult(hostCount = 3, profileCount = 2))
    }

    @Test
    fun exportHosts_plainExport_isNotMarkedEncrypted() = runTest {
        wheneverBlocking { repository.exportHostsToJson() }
            .thenReturn(Pair("{}", ExportCounts(hostCount = 1, profileCount = 1)))
        val viewModel = createViewModel()

        viewModel.exportHosts()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.exportedEncrypted).isFalse()
    }

    @Test
    fun importHosts_plainJson_importsDirectly() = runTest {
        wheneverBlocking { repository.importHostsFromJson(any()) }.thenReturn(importCounts)
        val viewModel = createViewModel()

        viewModel.importHosts("""{"version":1,"hosts":[]}""")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.pendingEncryptedImport).isNull()
        assertThat(state.importResult).isEqualTo(
            ImportResult(hostsImported = 2, hostsSkipped = 1, profilesImported = 1, profilesSkipped = 0),
        )
    }

    @Test
    fun importHosts_encryptedBundle_promptsForPassphraseInsteadOfImporting() = runTest {
        val viewModel = createViewModel()

        viewModel.importHosts(encryptedBundle)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.pendingEncryptedImport).isEqualTo(encryptedBundle)
        assertThat(state.importResult).isNull()
        verifyBlocking(repository, never()) { importHostsFromJson(any()) }
    }

    @Test
    fun submitImportPassphrase_correctPassphrase_importsAndClearsPrompt() = runTest {
        wheneverBlocking { repository.importHostsFromEncryptedBundle(any(), any()) }
            .thenReturn(importCounts)
        val viewModel = createViewModel()
        viewModel.importHosts(encryptedBundle)

        viewModel.submitImportPassphrase("hunter2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.pendingEncryptedImport).isNull()
        assertThat(state.importWrongPassphrase).isFalse()
        assertThat(state.importResult).isEqualTo(
            ImportResult(hostsImported = 2, hostsSkipped = 1, profilesImported = 1, profilesSkipped = 0),
        )
    }

    @Test
    fun submitImportPassphrase_wrongPassphrase_keepsPromptOpenWithError() = runTest {
        wheneverBlocking { repository.importHostsFromEncryptedBundle(any(), any()) }
            .thenAnswer { throw WrongPassphraseException() }
        val viewModel = createViewModel()
        viewModel.importHosts(encryptedBundle)

        viewModel.submitImportPassphrase("wrong")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.pendingEncryptedImport).isEqualTo(encryptedBundle)
        assertThat(state.importWrongPassphrase).isTrue()
        assertThat(state.importResult).isNull()
    }

    @Test
    fun cancelEncryptedImport_clearsPrompt() = runTest {
        val viewModel = createViewModel()
        viewModel.importHosts(encryptedBundle)

        viewModel.cancelEncryptedImport()

        val state = viewModel.uiState.value
        assertThat(state.pendingEncryptedImport).isNull()
        assertThat(state.importWrongPassphrase).isFalse()
        verifyBlocking(repository, never()) { importHostsFromEncryptedBundle(any(), any()) }
    }
}
