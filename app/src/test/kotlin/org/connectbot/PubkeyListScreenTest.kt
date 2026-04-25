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

package org.connectbot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.ui.screens.pubkeylist.PubkeyListScreen
import org.connectbot.ui.screens.pubkeylist.PubkeyListScreenContent
import org.connectbot.ui.screens.pubkeylist.PubkeyListTestTags
import org.connectbot.ui.screens.pubkeylist.PubkeyListUiState
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PubkeyListScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun pubkeyListScreen_displaysTitle() {
        composeTestRule.setContent {
            ConnectBotTheme {
                PubkeyListScreen(
                    onNavigateBack = {},
                    onNavigateToGenerate = {},
                    onNavigateToImportFido2 = {},
                    onNavigateToEdit = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText("Pubkeys")
            .assertIsDisplayed()
    }

    @Test
    fun pubkeyListScreen_hasBackButton() {
        var backCalled = false

        composeTestRule.setContent {
            ConnectBotTheme {
                PubkeyListScreen(
                    onNavigateBack = { backCalled = true },
                    onNavigateToGenerate = {},
                    onNavigateToImportFido2 = {},
                    onNavigateToEdit = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Navigate up")
            .performClick()

        assertTrue(backCalled)
    }

    @Test
    fun pubkeyListScreen_emptyState_displaysMessage() {
        composeTestRule.setContent {
            ConnectBotTheme {
                PubkeyListScreen(
                    onNavigateBack = {},
                    onNavigateToGenerate = {},
                    onNavigateToImportFido2 = {},
                    onNavigateToEdit = {},
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("No keys found.", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule
            .onNodeWithText("No keys found.", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun pubkeyListScreen_fabNavigatesToGenerate() {
        var generateCalled = false

        composeTestRule.setContent {
            ConnectBotTheme {
                PubkeyListScreen(
                    onNavigateBack = {},
                    onNavigateToGenerate = { generateCalled = true },
                    onNavigateToImportFido2 = {},
                    onNavigateToEdit = {},
                )
            }
        }

        // Expand the FAB menu first, then click the Generate menu item
        composeTestRule
            .onNodeWithContentDescription("Generate")
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Generate", useUnmergedTree = true)
            .performClick()

        assertTrue(generateCalled)
    }

    @Test
    fun pubkeyListScreenContent_loadingStateDisplaysProgress() {
        setPubkeyListContent(
            uiState = PubkeyListUiState(isLoading = true),
        )

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.empty_pubkeys_message))
            .assertDoesNotExist()
    }

    @Test
    fun pubkeyListScreenContent_displaysKeyStatesAndUnlocksWithPassword() {
        var toggledPubkey: Pubkey? = null
        var providedPassword: String? = null
        val key = testPubkey(nickname = "work-key", encrypted = true)

        setPubkeyListContent(
            uiState = PubkeyListUiState(
                pubkeys = listOf(key),
                loadedKeyNicknames = setOf("work-key"),
            ),
            onToggleKeyLoad = { pubkey, onPasswordRequired ->
                toggledPubkey = pubkey
                onPasswordRequired(pubkey) { password -> providedPassword = password }
            },
        )

        composeTestRule
            .onNodeWithTag(PubkeyListTestTags.itemRow(key.id))
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onAllNodesWithText(composeTestRule.activity.getString(R.string.pubkey_unlock))[0]
            .assertExists()
        composeTestRule
            .onNodeWithTag(PubkeyListTestTags.PASSWORD_FIELD)
            .performTextInput("secret")
        composeTestRule
            .onNodeWithTag(PubkeyListTestTags.PASSWORD_CONFIRM_BUTTON)
            .performClick()

        assertTrue(toggledPubkey == key)
        assertTrue(providedPassword == "secret")
    }

    @Test
    fun pubkeyListScreenContent_menuActionsInvokeCallbacks() {
        var editedPubkey: Pubkey? = null
        var copiedPublicPubkey: Pubkey? = null
        var exportedPublicPubkey: Pubkey? = null
        val key = testPubkey(nickname = "deploy-key", encrypted = false)

        setPubkeyListContent(
            uiState = PubkeyListUiState(pubkeys = listOf(key)),
            onNavigateToEdit = { editedPubkey = it },
            onCopyPublicKey = { copiedPublicPubkey = it },
            onExportPublicKey = { exportedPublicPubkey = it },
        )

        openPubkeyMenu(key)
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.list_pubkey_edit))
            .performClick()
        assertTrue(editedPubkey == key)

        openPubkeyMenu(key)
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.pubkey_copy_public))
            .performClick()
        assertTrue(copiedPublicPubkey == key)

        openPubkeyMenu(key)
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.pubkey_export_public))
            .performClick()
        assertTrue(exportedPublicPubkey == key)
    }

    @Test
    fun pubkeyListScreenContent_importedAndBiometricKeysUseSpecialMenuStates() {
        val imported = testPubkey(nickname = "imported-key", type = "IMPORTED")
        val biometric = testPubkey(nickname = "biometric-key", isBiometric = true)

        setPubkeyListContent(
            uiState = PubkeyListUiState(pubkeys = listOf(imported, biometric)),
        )

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.pubkey_biometric_description_icon))
            .assertIsDisplayed()
        openPubkeyMenu(imported)
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.pubkey_copy_public))
            .assertIsNotEnabled()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.pubkey_export_public))
            .assertIsNotEnabled()
    }

    @Test
    fun pubkeyListScreenContent_deleteConfirmationInvokesDelete() {
        var deletedPubkey: Pubkey? = null
        val key = testPubkey(nickname = "old-key")

        setPubkeyListContent(
            uiState = PubkeyListUiState(pubkeys = listOf(key)),
            onDeletePubkey = { deletedPubkey = it },
        )

        openPubkeyMenu(key)
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.pubkey_delete))
            .performClick()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.delete_pos))
            .performClick()

        assertTrue(deletedPubkey == key)
    }

    @Test
    fun pubkeyListScreenContent_fabMenuInvokesAllAddActions() {
        var generateCalled = false
        var importCalled = false
        var clipboardImportCalled = false

        setPubkeyListContent(
            onNavigateToGenerate = { generateCalled = true },
            onImportKey = { importCalled = true },
            onImportKeyFromClipboard = { clipboardImportCalled = true },
        )

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.pubkey_generate))
            .performClick()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.pubkey_generate), useUnmergedTree = true)
            .performClick()
        assertTrue(generateCalled)

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.pubkey_generate))
            .performClick()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.pubkey_import_existing), useUnmergedTree = true)
            .performClick()
        assertTrue(importCalled)

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.pubkey_generate))
            .performClick()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.pubkey_import_from_clipboard), useUnmergedTree = true)
            .performClick()
        assertTrue(clipboardImportCalled)
    }

    private fun openPubkeyMenu(pubkey: Pubkey) {
        composeTestRule
            .onNodeWithTag(PubkeyListTestTags.itemMenuButton(pubkey.id))
            .performClick()
    }

    private fun setPubkeyListContent(
        uiState: PubkeyListUiState = PubkeyListUiState(),
        onNavigateBack: () -> Unit = {},
        onNavigateToGenerate: () -> Unit = {},
        onNavigateToEdit: (Pubkey) -> Unit = {},
        onDeletePubkey: (Pubkey) -> Unit = {},
        onToggleKeyLoad: (Pubkey, (Pubkey, (String) -> Unit) -> Unit) -> Unit = { _, _ -> },
        onCopyPublicKey: (Pubkey) -> Unit = {},
        onExportPublicKey: (Pubkey) -> Unit = {},
        onImportKey: () -> Unit = {},
        onImportKeyFromClipboard: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ConnectBotTheme {
                PubkeyListScreenContent(
                    uiState = uiState,
                    snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                    onNavigateBack = onNavigateBack,
                    onNavigateToGenerate = onNavigateToGenerate,
                    onNavigateToEdit = onNavigateToEdit,
                    onDeletePubkey = onDeletePubkey,
                    onToggleKeyLoad = onToggleKeyLoad,
                    onCopyPublicKey = onCopyPublicKey,
                    onCopyPrivateKeyOpenSSH = { _, _ -> },
                    onCopyPrivateKeyPem = { _, _ -> },
                    onCopyPrivateKeyEncrypt = { _, _, _ -> },
                    onExportPublicKey = onExportPublicKey,
                    onExportPrivateKeyOpenSSH = { _, _ -> },
                    onExportPrivateKeyPem = { _, _ -> },
                    onExportPrivateKeyEncrypt = { _, _, _ -> },
                    onImportKey = onImportKey,
                    onImportKeyFromClipboard = onImportKeyFromClipboard,
                )
            }
        }
    }

    private fun testPubkey(
        nickname: String,
        type: String = "RSA",
        encrypted: Boolean = true,
        isBiometric: Boolean = false,
    ): Pubkey = Pubkey(
        id = nickname.hashCode().toLong(),
        nickname = nickname,
        type = type,
        encrypted = encrypted,
        startup = false,
        confirmation = false,
        createdDate = 0L,
        privateKey = ByteArray(0),
        publicKey = ByteArray(0),
        storageType = if (isBiometric) {
            KeyStorageType.ANDROID_KEYSTORE
        } else {
            KeyStorageType.EXPORTABLE
        },
    )
}
