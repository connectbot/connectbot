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

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.yubico.yubikit.piv.Slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Fido2Transport
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.ui.screens.pubkeyeditor.PubkeyEditorViewModel
import org.connectbot.util.BiometricKeyManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PubkeyCapabilitiesTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(default = dispatcher, io = dispatcher, main = dispatcher)
    private val software = Pubkey(
        nickname = "test",
        type = "EC",
        privateKey = byteArrayOf(1),
        publicKey = byteArrayOf(2),
        encrypted = false,
        startup = false,
        confirmation = false,
        createdDate = 1,
    )
    private val biometric = software.copy(storageType = KeyStorageType.ANDROID_KEYSTORE, privateKey = null)
    private val fido = software.copy(storageType = KeyStorageType.FIDO2_RESIDENT_KEY, privateKey = null)
    private val piv = software.copy(storageType = KeyStorageType.PIV, pivSlot = 0x9A, privateKey = null)
    private val hardware = listOf(biometric, fido, piv)
    private val noPrompt: (Pubkey, (String) -> Unit) -> Unit = { _, _ -> fail("Unexpected password prompt") }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun capabilitiesPreserveSoftwareAndBiometricLoading() {
        for (key in listOf(software, software.copy(encrypted = true), software.copy(type = "IMPORTED"))) {
            assertTrue(key.canExportPrivateKey)
            assertTrue(key.canLoadIntoMemory)
        }
        hardware.forEach { assertFalse(it.canExportPrivateKey) }
        assertTrue(biometric.canLoadIntoMemory)
        assertFalse(fido.canLoadIntoMemory)
        assertFalse(piv.canLoadIntoMemory)
    }

    @Test
    fun listItemsIgnoreStaleLoadedStateForExternalTokens() {
        val keys = listOf(software, biometric, fido, piv)
        val state = PubkeyListUiState(pubkeys = keys, loadedKeyNicknames = setOf("test"))
        assertEquals(listOf(true, true, false, false), state.items.map { it.isLoaded })
        assertEquals(listOf(true, true, false, false), state.items.map { it.canToggleLoaded })
        assertEquals(listOf(true, false, false, false), state.items.map { it.canExportPrivateKey })
        assertTrue(state.copy(loadedKeyNicknames = emptySet()).items.none { it.isLoaded })
    }

    @Test
    fun hardwarePrivateExportActionsAreRejectedBeforePromptOrClipboardAccess() = runTest {
        val context = mock(Context::class.java)
        val repository = mock(PubkeyRepository::class.java)
        `when`(repository.observeAll()).thenReturn(MutableStateFlow(emptyList()))
        val model = PubkeyListViewModel(context, repository, dispatchers, mock(BiometricKeyManager::class.java))
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.uiState.value.items.isEmpty())
        for (key in hardware.flatMap { listOf(it, it.copy(encrypted = true)) }) {
            model.copyPrivateKeyOpenSSH(key, noPrompt)
            model.copyPrivateKeyPem(key, noPrompt)
            model.copyPrivateKeyEncrypted(key, noPrompt, noPrompt)
            model.requestExportPrivateKeyOpenSSH(key, noPrompt)
            model.requestExportPrivateKeyPem(key, noPrompt)
            model.requestExportPrivateKeyEncrypted(key, noPrompt, noPrompt)
            assertNull(model.uiState.value.pendingExport)
            assertNull(model.uiState.value.error)
            model.requestExportPublicKey(key)
            assertNotNull(model.uiState.value.pendingPublicKeyExport)
        }
        verifyNoInteractions(context)
    }

    @Test
    fun loadingExternalTokensIsIgnoredButBiometricUnlockIsRequested() = runTest {
        val repository = mock(PubkeyRepository::class.java)
        `when`(repository.observeAll()).thenReturn(MutableStateFlow(emptyList()))
        val model = PubkeyListViewModel(mock(Context::class.java), repository, dispatchers, mock(BiometricKeyManager::class.java))
        for (key in listOf(fido, piv)) {
            model.toggleKeyLoaded(key, noPrompt)
            assertNull(model.uiState.value.biometricKeyToUnlock)
            assertNull(model.uiState.value.error)
        }
        model.toggleKeyLoaded(biometric, noPrompt)
        assertEquals(biometric, model.uiState.value.biometricKeyToUnlock)
    }

    @Test
    fun registeringPivStoresAlgorithmBackendAndSlotSeparately() = runTest {
        val repository = mock(PubkeyRepository::class.java)
        `when`(repository.observeAll()).thenReturn(MutableStateFlow(emptyList()))
        val model = PubkeyListViewModel(
            ApplicationProvider.getApplicationContext(),
            repository,
            dispatchers,
            mock(BiometricKeyManager::class.java),
        )
        val pair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        for (slot in listOf(Slot.AUTHENTICATION, Slot.SIGNATURE, Slot.RETIRED1)) {
            model.registerYubiKey(slot, pair.public)
            val key = requireNotNull(model.uiState.value.pendingNicknameConfirmation)
            assertEquals("EC", key.type)
            assertEquals(KeyStorageType.PIV, key.storageType)
            assertEquals(slot.value, key.pivSlot)
            assertTrue(key.isPiv)
            assertNull(key.privateKey)
            assertFalse(key.allowBackup)
            assertTrue(key.publicKey.contentEquals(pair.public.encoded))
            model.confirmImportNickname("PIV-" + slot.stringAlias)
            verify(repository).save(key.copy(nickname = "PIV-" + slot.stringAlias))
        }
    }

    @Test
    fun pivSlotParticipatesInIdentityEquality() {
        assertEquals(piv, piv.copy())
        assertEquals(piv.hashCode(), piv.copy().hashCode())
        assertFalse(piv == piv.copy(pivSlot = 0x9C))
        assertFalse(piv.hashCode() == piv.copy(pivSlot = 0x9C).hashCode())
    }

    @Test
    fun hardwarePasswordChangesAreIgnoredAndRenamePreservesKeyMaterial() = runTest {
        for (key in hardware) {
            val repository = mock(PubkeyRepository::class.java)
            `when`(repository.getById(key.id)).thenReturn(key)
            val model = PubkeyEditorViewModel(SavedStateHandle(mapOf("pubkeyId" to key.id.toString())), repository, dispatchers)
            assertFalse(model.uiState.value.canChangePassword)
            assertEquals(key.pivSlot, model.uiState.value.pivSlot)
            assertEquals(key.isPiv, model.uiState.value.isPiv)
            model.updateOldPassword("old")
            model.updateNewPassword1("new")
            model.updateNewPassword2("different")
            assertEquals("", model.uiState.value.oldPassword)
            assertEquals("", model.uiState.value.newPassword1)
            assertEquals("", model.uiState.value.newPassword2)
            assertFalse(model.uiState.value.hasUnsavedChanges)
            model.updateNickname("renamed")
            model.save()
            verify(repository).save(
                key.copy(
                    nickname = "renamed",
                    fido2Transport = if (key.isFido2) Fido2Transport.USB else key.fido2Transport,
                ),
            )
            assertTrue(model.uiState.value.saveSuccess)
        }
    }
}
