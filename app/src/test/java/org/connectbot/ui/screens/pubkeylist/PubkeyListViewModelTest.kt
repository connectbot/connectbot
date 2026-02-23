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

package org.connectbot.ui.screens.pubkeylist

import android.content.Context
import com.trilead.ssh2.crypto.PEMEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import timber.log.Timber
import java.security.KeyPairGenerator

/**
 * Tests for PubkeyListViewModel, focusing on the encrypted key import functionality.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PubkeyListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher
    )
    private lateinit var context: Context
    private lateinit var repository: PubkeyRepository
    private lateinit var pubkeysFlow: MutableStateFlow<List<Pubkey>>
    private lateinit var viewModel: PubkeyListViewModel

    // Generate a test RSA key pair for testing
    private val testKeyPair by lazy {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        keyGen.generateKeyPair()
    }

    // PEM-encoded unencrypted private key for testing
    private val testUnencryptedPemKey by lazy {
        PEMEncoder.encodePrivateKey(testKeyPair.private, null)
    }

    // PEM-encoded encrypted private key for testing (password: "testpass")
    private val testEncryptedPemKey by lazy {
        PEMEncoder.encodePrivateKey(testKeyPair.private, "testpass")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                // No-op for tests
            }
        })

        context = mock()
        repository = mock()
        pubkeysFlow = MutableStateFlow(emptyList())

        whenever(repository.observeAll()).thenReturn(pubkeysFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Timber.uprootAll()
    }

    private fun createViewModel(): PubkeyListViewModel = PubkeyListViewModel(context, repository, dispatchers)

    // ========== Tests for encrypted key import with re-encryption ==========

    /**
     * Tests importing an encrypted PEM key file without re-encrypting it for storage.
     *
     * Scenario: User imports an encrypted private key and chooses NOT to encrypt it for storage.
     * Expected: The key is decrypted and saved with encrypted=false.
     */
    @Test
    fun completeImportWithPassword_WithEncryptFalse_SavesUnencryptedKey() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Set up a pending import with an encrypted PEM key
        val keyData = testEncryptedPemKey.toByteArray()
        setPendingImport(viewModel, keyData, "test-key", "RSA")

        // Import with encrypt=false (don't re-encrypt for storage)
        viewModel.completeImportWithPassword(
            nickname = "test-key",
            decryptPassword = "testpass",
            encrypt = false,
            encryptPassword = null
        )
        advanceUntilIdle()

        // Verify the saved pubkey has encrypted=false
        val captor = argumentCaptor<Pubkey>()
        verify(repository).save(captor.capture())

        val savedPubkey = captor.firstValue
        assertFalse("Saved key should not be encrypted", savedPubkey.encrypted)
        assertEquals("Nickname should match", "test-key", savedPubkey.nickname)
        assertEquals("Type should be RSA", "RSA", savedPubkey.type)
    }

    /**
     * Tests importing an encrypted PEM key and re-encrypting it with a NEW password for storage.
     *
     * Scenario: User imports an encrypted private key and chooses to encrypt it for storage
     * using a different password than the original.
     * Expected: The key is decrypted with original password, re-encrypted with new password,
     * and saved with encrypted=true.
     */
    @Test
    fun completeImportWithPassword_WithEncryptTrue_SavesEncryptedKey() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Set up a pending import with an encrypted PEM key
        val keyData = testEncryptedPemKey.toByteArray()
        setPendingImport(viewModel, keyData, "test-key", "RSA")

        // Import with encrypt=true and a new password
        viewModel.completeImportWithPassword(
            nickname = "test-key",
            decryptPassword = "testpass",
            encrypt = true,
            encryptPassword = "newpassword"
        )
        advanceUntilIdle()

        // Verify the saved pubkey has encrypted=true
        val captor = argumentCaptor<Pubkey>()
        verify(repository).save(captor.capture())

        val savedPubkey = captor.firstValue
        assertTrue("Saved key should be encrypted", savedPubkey.encrypted)
        assertEquals("Nickname should match", "test-key", savedPubkey.nickname)
    }

    /**
     * Tests importing an encrypted PEM key and re-encrypting it with the SAME password for storage.
     *
     * Scenario: User imports an encrypted private key and chooses to encrypt it for storage
     * using the same password that was used to encrypt the original file (reuse password option).
     * Expected: The key is decrypted and re-encrypted with the same password, saved with encrypted=true.
     */
    @Test
    fun completeImportWithPassword_WithEncryptTrueReusingSamePassword_SavesEncryptedKey() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Set up a pending import with an encrypted PEM key
        val keyData = testEncryptedPemKey.toByteArray()
        setPendingImport(viewModel, keyData, "test-key", "RSA")

        // Import with encrypt=true, reusing the same password for encryption
        viewModel.completeImportWithPassword(
            nickname = "test-key",
            decryptPassword = "testpass",
            encrypt = true,
            encryptPassword = "testpass" // Same as decrypt password
        )
        advanceUntilIdle()

        // Verify the saved pubkey has encrypted=true
        val captor = argumentCaptor<Pubkey>()
        verify(repository).save(captor.capture())

        val savedPubkey = captor.firstValue
        assertTrue("Saved key should be encrypted when reusing password", savedPubkey.encrypted)
    }

    /**
     * Tests that import fails gracefully when the wrong decryption password is provided.
     *
     * Scenario: User tries to import an encrypted private key but enters the wrong password.
     * Expected: Decryption fails, error state is set, pending import is cleared, and no key is saved.
     */
    @Test
    fun completeImportWithPassword_WithWrongDecryptPassword_SetsError() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Set up a pending import with an encrypted PEM key
        val keyData = testEncryptedPemKey.toByteArray()
        setPendingImport(viewModel, keyData, "test-key", "RSA")

        // Try to import with wrong decrypt password
        viewModel.completeImportWithPassword(
            nickname = "test-key",
            decryptPassword = "wrongpassword",
            encrypt = false,
            encryptPassword = null
        )
        advanceUntilIdle()

        // Verify error is set and repository.save was not called
        val state = viewModel.uiState.value
        assertNotNull("Should have an error", state.error)
        assertNull("Pending import should be cleared", state.pendingImport)
        verify(repository, never()).save(any())
    }

    /**
     * Tests importing an unencrypted PEM key file without adding encryption.
     *
     * Scenario: User imports an unencrypted private key file and doesn't want to encrypt it.
     * Expected: The key is imported as-is and saved with encrypted=false.
     */
    @Test
    fun completeImportWithPassword_WithUnencryptedKey_SavesSuccessfully() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Set up a pending import with an unencrypted PEM key
        val keyData = testUnencryptedPemKey.toByteArray()
        setPendingImport(viewModel, keyData, "unencrypted-key", "RSA")

        // Import unencrypted key without re-encrypting
        viewModel.completeImportWithPassword(
            nickname = "unencrypted-key",
            decryptPassword = "", // No password needed for unencrypted key
            encrypt = false,
            encryptPassword = null
        )
        advanceUntilIdle()

        // Verify the key was saved
        val captor = argumentCaptor<Pubkey>()
        verify(repository).save(captor.capture())

        val savedPubkey = captor.firstValue
        assertFalse("Saved key should not be encrypted", savedPubkey.encrypted)
        assertEquals("Nickname should match", "unencrypted-key", savedPubkey.nickname)
    }

    /**
     * Tests importing an unencrypted PEM key file and encrypting it for secure storage.
     *
     * Scenario: User imports an unencrypted private key file but wants to protect it
     * with a password for storage on the device.
     * Expected: The key is encrypted with the provided password and saved with encrypted=true.
     */
    @Test
    fun completeImportWithPassword_WithUnencryptedKeyAndReEncrypt_SavesEncrypted() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Set up a pending import with an unencrypted PEM key
        val keyData = testUnencryptedPemKey.toByteArray()
        setPendingImport(viewModel, keyData, "encrypt-for-storage", "RSA")

        // Import unencrypted key but encrypt it for storage
        viewModel.completeImportWithPassword(
            nickname = "encrypt-for-storage",
            decryptPassword = "",
            encrypt = true,
            encryptPassword = "storagepassword"
        )
        advanceUntilIdle()

        // Verify the key was saved with encryption
        val captor = argumentCaptor<Pubkey>()
        verify(repository).save(captor.capture())

        val savedPubkey = captor.firstValue
        assertTrue("Saved key should be encrypted", savedPubkey.encrypted)
    }

    // ========== Tests for basic state management ==========

    /**
     * Tests that completeImportWithPassword does nothing when there is no pending import.
     *
     * Scenario: completeImportWithPassword is called without setting up a pending import first.
     * Expected: No action is taken and no key is saved.
     */
    @Test
    fun completeImportWithPassword_WithoutPendingImport_DoesNothing() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.completeImportWithPassword("any-key", "password", false, null)
        advanceUntilIdle()

        verify(repository, never()).save(any())
    }

    /**
     * Tests that cancelImport clears the pending import state.
     *
     * Scenario: User starts importing a key but cancels the import dialog.
     * Expected: The pending import state is cleared.
     */
    @Test
    fun cancelImport_ClearsPendingImport() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val keyData = "some key data".toByteArray()
        setPendingImport(viewModel, keyData, "test-key", "RSA")

        assertNotNull("Should have pending import", viewModel.uiState.value.pendingImport)

        viewModel.cancelImport()

        assertNull("pendingImport should be null after cancel", viewModel.uiState.value.pendingImport)
    }

    /**
     * Tests that clearError clears the error state.
     *
     * Scenario: An error occurred during import and user dismisses the error.
     * Expected: The error state is cleared.
     */
    @Test
    fun clearError_ClearsErrorState() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        setErrorState(viewModel, "Test error message")
        assertNotNull("Should have error", viewModel.uiState.value.error)

        viewModel.clearError()

        assertNull("Error should be cleared", viewModel.uiState.value.error)
    }

    // ========== Helper methods ==========

    private fun setErrorState(viewModel: PubkeyListViewModel, error: String) {
        val uiStateField = PubkeyListViewModel::class.java.getDeclaredField("_uiState")
        uiStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val uiStateFlow = uiStateField.get(viewModel) as MutableStateFlow<PubkeyListUiState>
        uiStateFlow.value = uiStateFlow.value.copy(error = error)
    }

    private fun setPendingImport(viewModel: PubkeyListViewModel, keyData: ByteArray, nickname: String, keyType: String) {
        val uiStateField = PubkeyListViewModel::class.java.getDeclaredField("_uiState")
        uiStateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val uiStateFlow = uiStateField.get(viewModel) as MutableStateFlow<PubkeyListUiState>
        uiStateFlow.value = uiStateFlow.value.copy(
            pendingImport = PendingImport(keyData, nickname, keyType)
        )
    }
}
