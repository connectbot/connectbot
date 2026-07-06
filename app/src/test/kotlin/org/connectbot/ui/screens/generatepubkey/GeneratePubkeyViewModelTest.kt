/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

package org.connectbot.ui.screens.generatepubkey

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.BiometricAvailability
import org.connectbot.util.BiometricKeyManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import timber.log.Timber

/**
 * Regression tests for issue #2081 extended to the key-generation path: toggling
 * "unlock at startup" together with a passphrase at key generation must persist
 * startup=true on the newly saved key.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeneratePubkeyViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )
    private lateinit var repository: PubkeyRepository
    private lateinit var biometricKeyManager: BiometricKeyManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                // No-op for tests
            }
        })
        repository = mock()
        biometricKeyManager = mock()
        whenever(biometricKeyManager.isBiometricAvailable())
            .thenReturn(BiometricAvailability.NO_HARDWARE)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Timber.uprootAll()
    }

    private fun createViewModel(): GeneratePubkeyViewModel = GeneratePubkeyViewModel(repository, biometricKeyManager, dispatchers)

    @Test
    fun generateEncryptedKey_preservesUnlockAtStartup() = runTest {
        val viewModel = createViewModel()
        viewModel.updateNickname("my-encrypted-key")
        viewModel.updateKeyType(KeyType.RSA)
        viewModel.updateBits(KeyType.RSA.minBits) // fastest RSA for tests (1024)
        viewModel.updatePassword1("pw")
        viewModel.updatePassword2("pw")
        viewModel.updateUnlockAtStartup(true)
        advanceUntilIdle()

        var succeeded = false
        viewModel.generateKey { succeeded = true }
        viewModel.onEntropyGathered(ByteArray(32) { it.toByte() })
        advanceUntilIdle()

        assertTrue("generateKey onSuccess callback should fire", succeeded)

        val captor = argumentCaptor<Pubkey>()
        verify(repository).save(captor.capture())
        val saved = captor.firstValue
        assertTrue(
            "Passphrase-protected generated key must remain encrypted=true",
            saved.encrypted,
        )
        assertTrue(
            "Generated encrypted key must persist unlockAtStartup=true when user enables it",
            saved.startup,
        )
    }

    @Test
    fun generateUnencryptedKey_preservesUnlockAtStartup() = runTest {
        val viewModel = createViewModel()
        viewModel.updateNickname("my-plain-key")
        viewModel.updateKeyType(KeyType.RSA)
        viewModel.updateBits(KeyType.RSA.minBits)
        viewModel.updateUnlockAtStartup(true)
        advanceUntilIdle()

        viewModel.generateKey { }
        viewModel.onEntropyGathered(ByteArray(32) { it.toByte() })
        advanceUntilIdle()

        val captor = argumentCaptor<Pubkey>()
        verify(repository).save(captor.capture())
        assertTrue(captor.firstValue.startup)
    }

    @Test
    fun biometricSwitch_resetsUnlockAtStartup() {
        val viewModel = createViewModel()
        viewModel.updateKeyType(KeyType.RSA)
        viewModel.updateUnlockAtStartup(true)
        viewModel.updateUseBiometric(true)

        assertTrue(
            "Switching to biometric must clear unlockAtStartup since Keystore keys cannot auto-load",
            !viewModel.uiState.value.unlockAtStartup,
        )
    }

    // Regression tests for upstream issue connectbot#2139: the Android Keystore only
    // supports RSA sizes 2048/3072/4096, and generation failures must be surfaced to
    // the user instead of silently closing the progress dialog.

    @Test
    fun enablingBiometric_constrainsRsaBitsToKeystoreSupportedSizes() {
        val viewModel = createViewModel()
        viewModel.updateKeyType(KeyType.RSA)
        viewModel.updateBits(KeyType.RSA.minBits) // 1024, unsupported by the Keystore
        viewModel.updateUseBiometric(true)

        val state = viewModel.uiState.value
        assertEquals(2048, state.bits)
        assertEquals(2048, state.minBits)
        assertEquals(4096, state.maxBits)
    }

    @Test
    fun biometricRsaBits_snapToClosestKeystoreSupportedSize() {
        val viewModel = createViewModel()
        viewModel.updateKeyType(KeyType.RSA)
        viewModel.updateUseBiometric(true)

        viewModel.updateBits(3000)
        assertEquals(3072, viewModel.uiState.value.bits)

        viewModel.updateBits(16384) // Clamped to the biometric max, then snapped
        assertEquals(4096, viewModel.uiState.value.bits)
    }

    @Test
    fun disablingBiometric_restoresFullRsaBitsRange() {
        val viewModel = createViewModel()
        viewModel.updateKeyType(KeyType.RSA)
        viewModel.updateUseBiometric(true)
        viewModel.updateUseBiometric(false)

        val state = viewModel.uiState.value
        assertEquals(KeyType.RSA.minBits, state.minBits)
        assertEquals(KeyType.RSA.maxBits, state.maxBits)
    }

    @Test
    fun biometricGenerationFailure_surfacesErrorInsteadOfSilentlyClosing() = runTest {
        whenever(biometricKeyManager.generateKeyAlias()).thenReturn("connectbot_bio_test")
        whenever(biometricKeyManager.generateKey(any(), any(), any()))
            .thenThrow(IllegalArgumentException("RSA key size 1024 is not supported by the Android Keystore"))

        val viewModel = createViewModel()
        viewModel.updateNickname("bio-key")
        viewModel.updateKeyType(KeyType.RSA)
        viewModel.updateUseBiometric(true)

        var succeeded = false
        viewModel.generateKey { succeeded = true }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("onSuccess must not fire when generation fails", !succeeded)
        assertTrue("Progress dialog must be dismissed", !state.isGenerating)
        assertEquals(
            "RSA key size 1024 is not supported by the Android Keystore",
            state.generationError,
        )
    }

    @Test
    fun softwareGenerationFailure_surfacesErrorInsteadOfSilentlyClosing() = runTest {
        whenever(repository.save(any())).thenThrow(RuntimeException("database unavailable"))

        val viewModel = createViewModel()
        viewModel.updateNickname("plain-key")
        viewModel.updateKeyType(KeyType.RSA)
        viewModel.updateBits(KeyType.RSA.minBits)
        advanceUntilIdle()

        var succeeded = false
        viewModel.generateKey { succeeded = true }
        viewModel.onEntropyGathered(ByteArray(32) { it.toByte() })
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("onSuccess must not fire when saving fails", !succeeded)
        assertTrue("Progress dialog must be dismissed", !state.isGenerating)
        assertEquals("database unavailable", state.generationError)
    }

    @Test
    fun dismissGenerationError_clearsError() = runTest {
        whenever(repository.save(any())).thenThrow(RuntimeException("database unavailable"))

        val viewModel = createViewModel()
        viewModel.updateNickname("plain-key")
        viewModel.updateKeyType(KeyType.RSA)
        viewModel.updateBits(KeyType.RSA.minBits)
        viewModel.generateKey { }
        viewModel.onEntropyGathered(ByteArray(32) { it.toByte() })
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.generationError != null)

        viewModel.dismissGenerationError()

        assertTrue(viewModel.uiState.value.generationError == null)
    }
}
