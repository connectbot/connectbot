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

package org.connectbot.ui.screens.pubkeylist

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.viewModelScope
import com.trilead.ssh2.crypto.PEMEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.BiometricKeyManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.security.KeyPairGenerator

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PubkeyNicknameImportTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mock(PubkeyRepository::class.java)
    private val placeholder = Pubkey(
        nickname = "",
        type = "RSA",
        privateKey = null,
        publicKey = byteArrayOf(),
        encrypted = false,
        startup = false,
        confirmation = false,
        createdDate = 0,
    )
    private lateinit var viewModel: PubkeyListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        `when`(repository.observeAll()).thenReturn(MutableStateFlow(emptyList()))
        viewModel = PubkeyListViewModel(
            RuntimeEnvironment.getApplication(),
            repository,
            CoroutineDispatchers(dispatcher, dispatcher, dispatcher),
            mock(BiometricKeyManager::class.java),
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun constraintFailureKeepsImportedKeyForRetry() = runTest(dispatcher) {
        `when`(repository.save(any(Pubkey::class.java) ?: placeholder))
            .thenThrow(SQLiteConstraintException("UNIQUE constraint failed: pubkeys.nickname"))
            .thenAnswer { it.arguments[0] as Pubkey }
        viewModel.importKeyFromText(privateKey, "original")
        advanceUntilIdle()

        viewModel.confirmImportNickname("taken")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
        assertNotNull(viewModel.uiState.value.pendingNicknameConfirmation)

        viewModel.confirmImportNickname("available")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.pendingNicknameConfirmation)
        val captor = org.mockito.ArgumentCaptor.forClass(Pubkey::class.java)
        verify(repository, times(2)).save(captor.capture() ?: placeholder)
        assertEquals(listOf("taken", "available"), captor.allValues.map { it.nickname })
    }

    @Test
    fun repeatedConfirmationSavesOnlyOnce() = runTest(dispatcher) {
        `when`(repository.save(any(Pubkey::class.java) ?: placeholder)).thenAnswer { it.arguments[0] as Pubkey }
        viewModel.importKeyFromText(privateKey, "original")
        advanceUntilIdle()

        viewModel.confirmImportNickname("chosen")
        viewModel.confirmImportNickname("chosen")
        advanceUntilIdle()

        verify(repository, times(1)).save(any(Pubkey::class.java) ?: placeholder)
        assertNull(viewModel.uiState.value.pendingNicknameConfirmation)
    }

    companion object {
        private val privateKey = PEMEncoder.encodePrivateKey(
            KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair().private,
            null,
        )
    }
}
