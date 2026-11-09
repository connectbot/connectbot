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

package org.connectbot.passkey

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.R
import org.connectbot.data.PasskeyEntriesChanged
import org.connectbot.data.PasskeyNicknameExists
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.ui.screens.pubkeylist.PubkeyListViewModel
import org.connectbot.util.BiometricKeyManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class PasskeyImportViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
    private val rows = MutableStateFlow<List<Pubkey>>(emptyList())
    private val repository = Mockito.mock(PubkeyRepository::class.java)
    private val source = Mockito.mock(PasskeyImportSource::class.java)
    private val store = ViewModelStore()
    private lateinit var context: Context

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = RuntimeEnvironment.getApplication()
        Mockito.`when`(repository.observeAll()).thenReturn(rows)
    }

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel() = PubkeyListViewModel(
        context,
        repository,
        dispatchers,
        Mockito.mock(BiometricKeyManager::class.java),
        source,
    ).also { store.put("keys", it) }

    private fun candidate(): Pubkey {
        val key = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public as ECPublicKey
        fun bytes(value: BigInteger): ByteArray {
            val raw = value.toByteArray().takeLast(32).toByteArray()
            return ByteArray(32 - raw.size) + raw
        }
        return PasskeySupport.fromPublicKey(byteArrayOf(1), byteArrayOf(4) + bytes(key.w.affineX) + bytes(key.w.affineY), "")
    }

    private fun writes() = Mockito.mockingDetails(repository).invocations.count { it.method.name == "importPasskey" }

    @Test
    @Config(sdk = [24])
    fun unsupportedDeviceDoesNotContactProvider() = runTest(dispatcher) {
        val model = viewModel()
        model.importPasskey(context)
        advanceUntilIdle()
        Mockito.verifyNoInteractions(source)
        assertEquals(context.getString(R.string.passkey_unsupported), model.uiState.value.error)
        assertEquals(0, writes())
    }

    @Test fun noDiscoveryOnStartupOrDatabaseChangesAndSingleMatchIsReconnected() = runTest(dispatcher) {
        val imported = candidate()
        val existing = imported.copy(id = UUID.randomUUID(), nickname = "server", credentialId = null, fido2RpId = null)
        val model = viewModel()
        advanceUntilIdle()
        rows.value = listOf(existing)
        advanceUntilIdle()
        Mockito.verifyNoInteractions(source)

        Mockito.`when`(source.discover(context)).thenReturn(imported)
        Mockito.`when`(repository.getAll()).thenReturn(listOf(existing))
        Mockito.`when`(repository.importPasskey(imported, existing.id, "")).thenReturn(existing)
        model.importPasskey(context)
        advanceUntilIdle()
        Mockito.verify(repository).importPasskey(imported, existing.id, "")
        assertNull(model.uiState.value.pendingPasskeyImport)
        assertNotNull(model.uiState.value.passkeyImportMessage)
        rows.value = listOf(existing.copy(confirmation = true))
        advanceUntilIdle()
        Mockito.verify(source, Mockito.times(1)).discover(context)
    }

    @Test fun duplicateMatchesWaitForSelectionWithoutMoreProviderQueries() = runTest(dispatcher) {
        val imported = candidate()
        val first = imported.copy(nickname = "first")
        val second = imported.copy(id = UUID.randomUUID(), nickname = "second", credentialId = null)
        Mockito.`when`(source.discover(context)).thenReturn(imported)
        Mockito.`when`(repository.getAll()).thenReturn(listOf(first, second))
        val model = viewModel()
        model.importPasskey(context)
        advanceUntilIdle()
        assertEquals(2, model.uiState.value.pendingPasskeyImport?.matches?.size)
        assertEquals(0, writes())
        // A second click while awaiting a decision cannot start another provider operation.
        model.importPasskey(context)
        advanceUntilIdle()
        Mockito.`when`(repository.importPasskey(imported, second.id, "")).thenReturn(second)
        model.savePasskeyImport(targetId = second.id)
        advanceUntilIdle()
        Mockito.verify(repository).importPasskey(imported, second.id, "")
        Mockito.verify(source, Mockito.times(1)).discover(context)
        assertNull(model.uiState.value.pendingPasskeyImport)
    }

    @Test fun nicknameConflictCanBeCorrectedWithoutRepeatingDiscovery() = runTest(dispatcher) {
        val imported = candidate()
        Mockito.`when`(source.discover(context)).thenReturn(imported)
        Mockito.`when`(repository.getAll()).thenReturn(emptyList())
        val model = viewModel()
        model.importPasskey(context)
        advanceUntilIdle()
        assertTrue(model.uiState.value.pendingPasskeyImport!!.matches.isEmpty())
        assertEquals(0, writes())
        Mockito.`when`(repository.importPasskey(imported, null, "used")).thenThrow(PasskeyNicknameExists())
        model.savePasskeyImport(nickname = "used")
        advanceUntilIdle()
        assertNotNull(model.uiState.value.pendingPasskeyImport)
        assertNotNull(model.uiState.value.error)
        Mockito.`when`(repository.importPasskey(imported, null, "new")).thenReturn(imported.copy(nickname = "new"))
        model.savePasskeyImport(nickname = " new ")
        advanceUntilIdle()
        assertNull(model.uiState.value.pendingPasskeyImport)
        Mockito.verify(source, Mockito.times(1)).discover(context)
    }

    @Test fun changedEntriesAreRefreshedWithoutQueryingProviderAgain() = runTest(dispatcher) {
        val imported = candidate()
        val appeared = imported.copy(nickname = "appeared")
        Mockito.`when`(source.discover(context)).thenReturn(imported)
        Mockito.`when`(repository.getAll()).thenReturn(emptyList(), listOf(appeared))
        Mockito.`when`(repository.importPasskey(imported, null, "new")).thenThrow(PasskeyEntriesChanged())
        val model = viewModel()
        model.importPasskey(context)
        advanceUntilIdle()
        model.savePasskeyImport(nickname = "new")
        advanceUntilIdle()
        assertEquals(listOf(appeared), model.uiState.value.pendingPasskeyImport?.matches)
        Mockito.verify(source, Mockito.times(1)).discover(context)
    }

    @Test fun failedRefreshKeepsPendingImportWithoutAnUnhandledException() = runTest(dispatcher) {
        val imported = candidate()
        Mockito.`when`(source.discover(context)).thenReturn(imported)
        Mockito.`when`(repository.getAll()).thenReturn(emptyList()).thenThrow(IllegalStateException("database unavailable"))
        Mockito.`when`(repository.importPasskey(imported, null, "new")).thenThrow(PasskeyEntriesChanged())
        val model = viewModel()
        model.importPasskey(context)
        advanceUntilIdle()
        model.savePasskeyImport(nickname = "new")
        advanceUntilIdle()
        assertNotNull(model.uiState.value.pendingPasskeyImport)
        assertNotNull(model.uiState.value.error)
        assertFalse(model.uiState.value.isSavingPasskeyImport)
        Mockito.verify(source, Mockito.times(1)).discover(context)
    }

    @Test fun cancellationAndConflictingIdentitiesNeverWrite() = runTest(dispatcher) {
        val imported = candidate()
        val conflict = candidate().copy(nickname = "conflict")
        Mockito.`when`(source.discover(context)).thenReturn(imported).thenAnswer { throw GetCredentialCancellationException() }
        Mockito.`when`(repository.getAll()).thenReturn(listOf(conflict))
        val model = viewModel()
        model.importPasskey(context)
        advanceUntilIdle()
        assertEquals(0, writes())
        assertNotNull(model.uiState.value.error)
        model.clearError()
        model.importPasskey(context)
        advanceUntilIdle()
        assertEquals(0, writes())
        assertNull(model.uiState.value.pendingPasskeyImport)
        assertNull(model.uiState.value.error)
        assertFalse(model.uiState.value.isImportingPasskey)
    }
}
