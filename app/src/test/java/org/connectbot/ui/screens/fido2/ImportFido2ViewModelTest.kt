/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root, Jeffrey Sharkey
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

package org.connectbot.ui.screens.fido2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Fido2Transport
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.fido2.Fido2ConnectionState
import org.connectbot.fido2.Fido2Credential
import org.connectbot.fido2.Fido2Manager
import org.connectbot.fido2.Fido2Result
import org.connectbot.fido2.Fido2TestData
import org.connectbot.util.PubkeyConstants
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import timber.log.Timber

/**
 * Unit tests for ImportFido2ViewModel.
 * Tests credential discovery, PIN authentication, and import flows using mocked Fido2Manager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportFido2ViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher
    )

    private lateinit var fido2Manager: Fido2Manager
    private lateinit var repository: PubkeyRepository
    private lateinit var connectionStateFlow: MutableStateFlow<Fido2ConnectionState>
    private lateinit var viewModel: ImportFido2ViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                // No-op for tests
            }
        })

        fido2Manager = mock()
        repository = mock()
        connectionStateFlow = MutableStateFlow(Fido2ConnectionState.Disconnected)

        whenever(fido2Manager.connectionState).thenReturn(connectionStateFlow)

        viewModel = ImportFido2ViewModel(fido2Manager, repository, dispatchers)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Timber.uprootAll()
    }

    // ==================== Initial State Tests ====================

    @Test
    fun `initial state is correct`() {
        val state = viewModel.uiState.value

        assertThat(state.connectionState).isEqualTo(Fido2ConnectionState.Disconnected)
        assertThat(state.credentials).isEmpty()
        assertThat(state.selectedCredential).isNull()
        assertThat(state.nickname).isEmpty()
        assertThat(state.selectedTransport).isEqualTo(Fido2Transport.USB)
        assertThat(state.transportSelected).isFalse()
        assertThat(state.isScanning).isFalse()
        assertThat(state.needsPin).isFalse()
        assertThat(state.waitingForNfcTap).isFalse()
        assertThat(state.pinError).isNull()
        assertThat(state.error).isNull()
        assertThat(state.importSuccess).isFalse()
    }

    // ==================== Transport Selection Tests ====================

    @Test
    fun `updateTransport changes selected transport`() {
        viewModel.updateTransport(Fido2Transport.NFC)

        assertThat(viewModel.uiState.value.selectedTransport).isEqualTo(Fido2Transport.NFC)
    }

    @Test
    fun `confirmTransportSelection for USB starts discovery`() {
        viewModel.updateTransport(Fido2Transport.USB)
        viewModel.confirmTransportSelection()

        assertThat(viewModel.uiState.value.transportSelected).isTrue()
        verify(fido2Manager).startUsbDiscovery()
    }

    @Test
    fun `confirmTransportSelection for NFC shows PIN prompt`() {
        viewModel.updateTransport(Fido2Transport.NFC)
        viewModel.confirmTransportSelection()

        assertThat(viewModel.uiState.value.transportSelected).isTrue()
        assertThat(viewModel.uiState.value.needsPin).isTrue()
        verify(fido2Manager, never()).startUsbDiscovery()
    }

    // ==================== Connection State Tests ====================

    @Test
    fun `USB connection triggers PIN prompt when no credentials`() = runTest {
        connectionStateFlow.value = Fido2ConnectionState.Connected(
            transport = "USB",
            deviceName = "YubiKey 5"
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.needsPin).isTrue()
    }

    @Test
    fun `connection error updates UI state`() = runTest {
        connectionStateFlow.value = Fido2ConnectionState.Error("Connection failed")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Connection failed")
        assertThat(viewModel.uiState.value.isScanning).isFalse()
    }

    // ==================== PIN Submission Tests ====================

    @Test
    fun `submitPin for USB sets pending PIN and retries`() = runTest {
        // First simulate USB connection
        connectionStateFlow.value = Fido2ConnectionState.Connected(
            transport = "USB",
            deviceName = "YubiKey 5"
        )
        advanceUntilIdle()

        viewModel.submitPin(Fido2TestData.PIN)
        advanceUntilIdle()

        verify(fido2Manager).setPendingPin(Fido2TestData.PIN)
        verify(fido2Manager).setUsbCredentialCallback(any())
        verify(fido2Manager).retryUsbWithPin()
        assertThat(viewModel.uiState.value.needsPin).isFalse()
        assertThat(viewModel.uiState.value.isScanning).isTrue()
    }

    @Test
    fun `submitPin for NFC sets waitingForNfcTap`() = runTest {
        // NFC mode - not connected via USB
        viewModel.updateTransport(Fido2Transport.NFC)
        viewModel.confirmTransportSelection()

        viewModel.submitPin(Fido2TestData.PIN)
        advanceUntilIdle()

        verify(fido2Manager).setPendingPin(Fido2TestData.PIN)
        verify(fido2Manager).setNfcCredentialCallback(any())
        assertThat(viewModel.uiState.value.needsPin).isFalse()
        assertThat(viewModel.uiState.value.waitingForNfcTap).isTrue()
    }

    // ==================== Credential Discovery Tests ====================

    @Test
    fun `successful credential discovery updates state`() = runTest {
        val credentials = listOf(
            Fido2TestData.createEd25519Credential(),
            Fido2TestData.createEcdsaCredential()
        )

        // Capture the callback and invoke it with test data
        val callbackCaptor = argumentCaptor<(Fido2Result<List<Fido2Credential>>) -> Unit>()

        connectionStateFlow.value = Fido2ConnectionState.Connected("USB", "YubiKey 5")
        advanceUntilIdle()

        viewModel.submitPin(Fido2TestData.PIN)
        verify(fido2Manager).setUsbCredentialCallback(callbackCaptor.capture())

        // Simulate callback invocation
        callbackCaptor.firstValue.invoke(Fido2Result.Success(credentials))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.credentials).hasSize(2)
        assertThat(state.isScanning).isFalse()
        assertThat(state.needsPin).isFalse()
        assertThat(state.error).isNull()
    }

    @Test
    fun `empty credential list shows error message`() = runTest {
        val callbackCaptor = argumentCaptor<(Fido2Result<List<Fido2Credential>>) -> Unit>()

        connectionStateFlow.value = Fido2ConnectionState.Connected("USB", "YubiKey 5")
        advanceUntilIdle()

        viewModel.submitPin(Fido2TestData.PIN)
        verify(fido2Manager).setUsbCredentialCallback(callbackCaptor.capture())

        callbackCaptor.firstValue.invoke(Fido2Result.Success(emptyList()))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.credentials).isEmpty()
        assertThat(viewModel.uiState.value.error).contains("No SSH credentials found")
    }

    @Test
    fun `invalid PIN shows error and resets state`() = runTest {
        val callbackCaptor = argumentCaptor<(Fido2Result<List<Fido2Credential>>) -> Unit>()

        connectionStateFlow.value = Fido2ConnectionState.Connected("USB", "YubiKey 5")
        advanceUntilIdle()

        viewModel.submitPin(Fido2TestData.INVALID_PIN)
        verify(fido2Manager).setUsbCredentialCallback(callbackCaptor.capture())

        callbackCaptor.firstValue.invoke(Fido2Result.PinInvalid(attemptsRemaining = 7))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.needsPin).isTrue()
        assertThat(state.pinError).contains("Invalid PIN")
        assertThat(state.pinError).contains("7")
        assertThat(state.isScanning).isFalse()
    }

    @Test
    fun `locked PIN shows error and disables PIN entry`() = runTest {
        val callbackCaptor = argumentCaptor<(Fido2Result<List<Fido2Credential>>) -> Unit>()

        connectionStateFlow.value = Fido2ConnectionState.Connected("USB", "YubiKey 5")
        advanceUntilIdle()

        viewModel.submitPin(Fido2TestData.PIN)
        verify(fido2Manager).setUsbCredentialCallback(callbackCaptor.capture())

        callbackCaptor.firstValue.invoke(Fido2Result.PinLocked("PIN is locked"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.needsPin).isFalse() // Can't enter PIN anymore
        assertThat(state.error).contains("PIN is locked")
    }

    @Test
    fun `discovery error updates state correctly`() = runTest {
        val callbackCaptor = argumentCaptor<(Fido2Result<List<Fido2Credential>>) -> Unit>()

        connectionStateFlow.value = Fido2ConnectionState.Connected("USB", "YubiKey 5")
        advanceUntilIdle()

        viewModel.submitPin(Fido2TestData.PIN)
        verify(fido2Manager).setUsbCredentialCallback(callbackCaptor.capture())

        callbackCaptor.firstValue.invoke(Fido2Result.Error("Communication error"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Communication error")
        assertThat(viewModel.uiState.value.isScanning).isFalse()
    }

    // ==================== Credential Selection Tests ====================

    @Test
    fun `selectCredential updates selected credential and nickname`() {
        val credential = Fido2TestData.createEd25519Credential(userName = "alice@example.com")

        viewModel.selectCredential(credential)

        val state = viewModel.uiState.value
        assertThat(state.selectedCredential).isEqualTo(credential)
        assertThat(state.nickname).isEqualTo("alice@example.com")
    }

    @Test
    fun `selectCredential uses default nickname when userName is null`() {
        val credential = Fido2TestData.createEd25519Credential(userName = null)

        viewModel.selectCredential(credential)

        assertThat(viewModel.uiState.value.nickname).isEqualTo("FIDO2 Key")
    }

    @Test
    fun `updateNickname changes nickname`() {
        val credential = Fido2TestData.createEd25519Credential()
        viewModel.selectCredential(credential)

        viewModel.updateNickname("My Security Key")

        assertThat(viewModel.uiState.value.nickname).isEqualTo("My Security Key")
    }

    @Test
    fun `clearSelection resets selected credential and nickname`() {
        val credential = Fido2TestData.createEd25519Credential()
        viewModel.selectCredential(credential)

        viewModel.clearSelection()

        val state = viewModel.uiState.value
        assertThat(state.selectedCredential).isNull()
        assertThat(state.nickname).isEmpty()
    }

    // ==================== Import Tests ====================

    @Test
    fun `importSelectedCredential saves Ed25519 key to repository`() = runTest {
        val credential = Fido2TestData.createEd25519Credential()
        viewModel.selectCredential(credential)
        viewModel.updateNickname("Test Ed25519 Key")

        val pubkeyCaptor = argumentCaptor<Pubkey>()

        viewModel.importSelectedCredential()
        advanceUntilIdle()

        verify(repository).save(pubkeyCaptor.capture())

        val savedPubkey = pubkeyCaptor.firstValue
        assertThat(savedPubkey.nickname).isEqualTo("Test Ed25519 Key")
        assertThat(savedPubkey.type).isEqualTo(PubkeyConstants.KEY_TYPE_SK_ED25519)
        assertThat(savedPubkey.storageType).isEqualTo(KeyStorageType.FIDO2_RESIDENT_KEY)
        assertThat(savedPubkey.credentialId).isEqualTo(credential.credentialId)
        assertThat(savedPubkey.fido2RpId).isEqualTo(Fido2TestData.SSH_RP_ID)
        assertThat(savedPubkey.fido2Transport).isEqualTo(Fido2Transport.USB)
        assertThat(savedPubkey.privateKey).isNull() // FIDO2 keys have no extractable private key
        assertThat(savedPubkey.publicKey).isNotNull()
    }

    @Test
    fun `importSelectedCredential saves ECDSA key to repository`() = runTest {
        val credential = Fido2TestData.createEcdsaCredential()
        viewModel.selectCredential(credential)
        viewModel.updateNickname("Test ECDSA Key")

        val pubkeyCaptor = argumentCaptor<Pubkey>()

        viewModel.importSelectedCredential()
        advanceUntilIdle()

        verify(repository).save(pubkeyCaptor.capture())

        val savedPubkey = pubkeyCaptor.firstValue
        assertThat(savedPubkey.nickname).isEqualTo("Test ECDSA Key")
        assertThat(savedPubkey.type).isEqualTo(PubkeyConstants.KEY_TYPE_SK_ECDSA)
        assertThat(savedPubkey.storageType).isEqualTo(KeyStorageType.FIDO2_RESIDENT_KEY)
    }

    @Test
    fun `importSelectedCredential uses default nickname when blank`() = runTest {
        val credential = Fido2TestData.createEd25519Credential()
        viewModel.selectCredential(credential)
        viewModel.updateNickname("   ") // Blank nickname

        val pubkeyCaptor = argumentCaptor<Pubkey>()

        viewModel.importSelectedCredential()
        advanceUntilIdle()

        verify(repository).save(pubkeyCaptor.capture())
        assertThat(pubkeyCaptor.firstValue.nickname).isEqualTo("FIDO2 Key")
    }

    @Test
    fun `importSelectedCredential sets importSuccess on success`() = runTest {
        val credential = Fido2TestData.createEd25519Credential()
        viewModel.selectCredential(credential)

        viewModel.importSelectedCredential()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.importSuccess).isTrue()
    }

    @Test
    fun `importSelectedCredential handles repository error`() = runTest {
        val credential = Fido2TestData.createEd25519Credential()
        viewModel.selectCredential(credential)

        whenever(repository.save(any())).thenThrow(RuntimeException("Database error"))

        viewModel.importSelectedCredential()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).contains("Failed to import")
        assertThat(viewModel.uiState.value.importSuccess).isFalse()
    }

    @Test
    fun `importSelectedCredential does nothing when no credential selected`() = runTest {
        // No credential selected
        viewModel.importSelectedCredential()
        advanceUntilIdle()

        verify(repository, never()).save(any())
    }

    @Test
    fun `importSelectedCredential respects selected NFC transport`() = runTest {
        val credential = Fido2TestData.createEd25519Credential()
        viewModel.updateTransport(Fido2Transport.NFC)
        viewModel.selectCredential(credential)

        val pubkeyCaptor = argumentCaptor<Pubkey>()

        viewModel.importSelectedCredential()
        advanceUntilIdle()

        verify(repository).save(pubkeyCaptor.capture())
        assertThat(pubkeyCaptor.firstValue.fido2Transport).isEqualTo(Fido2Transport.NFC)
    }

    // ==================== Discovery Start/Stop Tests ====================

    @Test
    fun `startUsbDiscovery delegates to Fido2Manager`() {
        viewModel.startUsbDiscovery()

        verify(fido2Manager).startUsbDiscovery()
    }

    @Test
    fun `stopUsbDiscovery delegates to Fido2Manager`() {
        viewModel.stopUsbDiscovery()

        verify(fido2Manager).stopUsbDiscovery()
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `clearError resets error state`() {
        // Set an error state first
        connectionStateFlow.value = Fido2ConnectionState.Error("Test error")

        viewModel.clearError()

        assertThat(viewModel.uiState.value.error).isNull()
    }
}
