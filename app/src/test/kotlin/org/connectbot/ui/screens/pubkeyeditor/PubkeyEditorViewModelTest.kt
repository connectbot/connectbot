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

package org.connectbot.ui.screens.pubkeyeditor

import androidx.lifecycle.SavedStateHandle
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
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import timber.log.Timber

/**
 * Regression tests for issue #2081: pubkey password prompt never appears for encrypted
 * keys on startup because the editor silently strips the "unlock at startup" flag when
 * the key is encrypted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PubkeyEditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )
    private lateinit var repository: PubkeyRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                // No-op for tests
            }
        })
        repository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Timber.uprootAll()
    }

    private fun createViewModel(pubkeyId: Long): PubkeyEditorViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("pubkeyId" to pubkeyId))
        return PubkeyEditorViewModel(savedStateHandle, repository, dispatchers)
    }

    /**
     * Issue #2081: for an existing encrypted key, toggling "unlock at startup" on
     * and saving (without changing the password) must persist startup=true.
     *
     * Previously the save path contained `startup = unlockAtStartup && !encrypted`,
     * which silently cleared the flag whenever the key was password-protected. As a
     * result the key was never auto-loaded and no passphrase prompt was offered on
     * service start, so hosts configured to use "any in-memory pubkey" immediately
     * fell back to password authentication.
     */
    @Test
    fun save_encryptedKey_preservesUnlockAtStartup() = runTest {
        val existingKey = Pubkey(
            id = 42L,
            nickname = "encrypted-key",
            type = "RSA",
            privateKey = byteArrayOf(1, 2, 3),
            publicKey = byteArrayOf(4, 5, 6),
            encrypted = true,
            startup = false,
            confirmation = false,
            createdDate = 1_000L,
        )
        whenever(repository.getById(42L)).thenReturn(existingKey)

        val viewModel = createViewModel(42L)
        advanceUntilIdle()

        viewModel.updateUnlockAtStartup(true)
        viewModel.save()
        advanceUntilIdle()

        val captor = argumentCaptor<Pubkey>()
        verify(repository).save(captor.capture())
        val saved = captor.firstValue
        assertTrue(
            "Encrypted key must keep encrypted=true after save",
            saved.encrypted,
        )
        assertTrue(
            "Encrypted key must persist unlockAtStartup=true when user enables it",
            saved.startup,
        )
    }
}
