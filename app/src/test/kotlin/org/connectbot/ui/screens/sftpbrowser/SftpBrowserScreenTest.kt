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

package org.connectbot.ui.screens.sftpbrowser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.connectbot.HiltComponentActivity
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Shadows.shadowOf

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SftpBrowserScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<HiltComponentActivity>()
    private val vm = mock(SftpBrowserViewModel::class.java)
    private val host = Host(nickname = "test", hostname = "server")
    private val state = MutableStateFlow(SftpBrowserUiState(host = host, isConnected = true, currentPath = "/home/test"))
    private val visible = mutableStateOf(true)
    private var backs = 0

    @Before
    fun setUp() {
        hiltRule.inject()
        `when`(vm.uiState).thenReturn(state)
    }

    private fun text(id: Int, vararg args: Any) = rule.activity.getString(id, *args)
    private fun click(id: Int) {
        rule.onNode(hasText(text(id)) and hasClickAction()).performClick()
    }
    private fun render() {
        rule.setContent { ConnectBotTheme { if (visible.value) SftpBrowserScreen(onNavigateBack = { backs++ }, viewModel = vm) } }
    }
    private fun update(value: SftpBrowserUiState) {
        rule.runOnIdle { state.value = value }
    }
    private fun file(name: String, directory: Boolean = false) = SftpEntry(name, "/home/test/$name", directory, false, 12, 1, "rw-r--r--")
    private fun deliver(uri: Uri) {
        val request = shadowOf(rule.activity).nextStartedActivityForResult
        rule.runOnIdle { rule.activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, Intent().setData(uri)) }
    }

    @Test
    fun rendersConnectingErrorEmptyAndLoadingStatesAndInvokesToolbarActions() {
        update(SftpBrowserUiState(isConnecting = true))
        render()
        rule.onNodeWithText(text(R.string.sftp_connecting, "")).assertIsDisplayed()
        update(SftpBrowserUiState(host = host, error = "offline"))
        click(R.string.sftp_retry)
        verify(vm).connect()
        update(SftpBrowserUiState())
        update(SftpBrowserUiState(host = host, isConnected = true, isLoading = true))
        update(SftpBrowserUiState(host = host, isConnected = true))
        rule.onNodeWithText(text(R.string.sftp_empty_directory)).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.sftp_refresh)).performClick()
        verify(vm).refresh()
        rule.onNodeWithContentDescription(text(R.string.sftp_go_to_path)).performClick()
        verify(vm).showGoToPathDialog()
        rule.onNodeWithContentDescription(text(R.string.sftp_actions)).performClick()
        click(R.string.sftp_create_folder)
        verify(vm).showCreateFolderDialog()
        rule.onNodeWithContentDescription(text(R.string.button_back)).performClick()
        assertEquals(1, backs)
        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        verify(vm).disconnect()
    }

    @Test
    fun tappingCurrentPathOpensKeyboardEditorAndEnterNavigates() {
        render()
        rule.onNodeWithText("/home/test").performClick()
        verify(vm).showGoToPathDialog()
        update(state.value.copy(showGoToPathDialog = true))
        rule.onNode(hasSetTextAction()).performTextReplacement("/var/log")
        rule.onNode(hasSetTextAction()).performImeAction()
        verify(vm).goToPath("/var/log")
    }

    @Test
    fun downloadConflictOffersOverwriteRenameAndCancel() {
        update(state.value.copy(downloadConflict = DownloadConflict("report.pdf", "report(1).pdf", true)))
        render()
        rule.onNode(hasSetTextAction()).assertIsDisplayed()
        rule.onNode(hasSetTextAction() and hasText("report(1).pdf")).assertIsDisplayed()
        click(R.string.sftp_download_overwrite)
        verify(vm).resolveDownloadConflict("report.pdf")
        rule.onNode(hasSetTextAction()).performTextReplacement("renamed.pdf")
        click(R.string.sftp_download_rename)
        verify(vm).resolveDownloadConflict("renamed.pdf")
        click(R.string.button_cancel)
        verify(vm).cancelTransfer()
        update(state.value.copy(downloadConflict = DownloadConflict("folder", "folder(1)", false)))
        rule.onNodeWithText(text(R.string.sftp_download_overwrite)).assertIsNotEnabled()
    }

    @Test
    fun navigatesFoldersDownloadsFilesAndDownloadsMultipleSelectedFiles() {
        val first = file("first")
        val second = file("second")
        update(state.value.copy(entries = listOf(file("..", true), file("folder", true), first, second)))
        render()
        rule.onNodeWithText("folder").performClick()
        verify(vm).navigateTo("/home/test/folder")
        rule.onNodeWithText("..").performClick()
        verify(vm).navigateUp()
        rule.onNodeWithText("first").performClick()
        val target = Uri.parse("content://test/target")
        deliver(target)
        verify(vm).downloadFiles(listOf(first), target)
        rule.onAllNodes(isToggleable())[0].performClick()
        rule.onNodeWithText("second").performClick()
        rule.onNodeWithText(text(R.string.sftp_selected_count, 2)).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.sftp_download_selected)).performClick()
        val tree = Uri.parse("content://test/tree/root")
        deliver(tree)
        verify(vm).downloadFiles(listOf(first, second), tree)
        rule.onAllNodes(isToggleable())[0].performClick()
        rule.onNodeWithText("first").performClick()
        rule.onAllNodes(isToggleable())[0].performClick()
        rule.onNodeWithContentDescription(text(R.string.button_cancel)).performClick()
        assertEquals(0, backs)
    }

    @Test
    fun entryMenusOfferFileDownloadAndDirectoryDeletionAndUploadPicker() {
        val entry = file("first")
        val directory = file("folder", true)
        update(state.value.copy(entries = listOf(directory, entry)))
        render()
        rule.onNodeWithText("first").performTouchInput { longClick() }
        click(R.string.sftp_download_file)
        deliver(Uri.parse("content://test/target"))
        rule.onNodeWithText("folder").performTouchInput { longClick() }
        click(R.string.sftp_delete)
        verify(vm).showDeleteDialog(directory)
        rule.onNodeWithContentDescription(text(R.string.sftp_actions)).performClick()
        click(R.string.sftp_upload_file)
        deliver(Uri.parse("content://test/source"))
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<UploadFile>>
        verify(vm).uploadFiles(captor.capture() ?: emptyList())
        assertEquals("uploaded_file_1", captor.value.single().filename)
    }

    @Test
    fun folderPathAndDeleteDialogsForwardConfirmAndCancelActions() {
        update(state.value.copy(showCreateFolderDialog = true))
        render()
        rule.onNodeWithText(text(R.string.button_create)).assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextReplacement("folder")
        click(R.string.button_create)
        verify(vm).createFolder("folder")
        click(R.string.button_cancel)
        verify(vm).dismissCreateFolderDialog()
        update(state.value.copy(showCreateFolderDialog = false, showGoToPathDialog = true))
        rule.onNode(hasSetTextAction()).performTextReplacement(" ")
        rule.onNodeWithText(text(R.string.sftp_go)).assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextReplacement("/tmp")
        click(R.string.sftp_go)
        verify(vm).goToPath("/tmp")
        click(R.string.button_cancel)
        verify(vm).dismissGoToPathDialog()
        update(state.value.copy(showGoToPathDialog = false, showDeleteDialog = true, entryToDelete = file("folder", true)))
        rule.onNodeWithText(text(R.string.sftp_delete_folder_warning)).assertIsDisplayed()
        click(R.string.sftp_delete)
        verify(vm).deleteEntry()
        click(R.string.button_cancel)
        verify(vm).dismissDeleteDialog()
        update(state.value.copy(entryToDelete = file("file")))
    }

    @Test
    fun hostKeyPasswordAndPassphraseDialogsForwardResponses() {
        update(state.value.copy(showHostKeyDialog = true, hostKeyInfo = HostKeyInfo("server", "RSA", "fingerprint", true)))
        render()
        click(R.string.sftp_host_key_accept)
        verify(vm).acceptHostKey()
        update(state.value.copy(hostKeyInfo = HostKeyInfo("server", "RSA", "fingerprint", false)))
        rule.onNodeWithText(text(R.string.sftp_host_key_warning)).assertIsDisplayed()
        click(R.string.sftp_host_key_reject)
        verify(vm).rejectHostKey()
        update(state.value.copy(showHostKeyDialog = false, showPasswordDialog = true, passwordPrompt = "Password"))
        rule.onNodeWithText(text(R.string.button_ok)).assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextReplacement("value")
        click(R.string.button_ok)
        verify(vm).submitPassword("value")
        click(R.string.button_cancel)
        verify(vm).cancelPassword()
        update(state.value.copy(showPasswordDialog = false, showKeyPassphraseDialog = true, keyPassphrasePrompt = "Passphrase"))
        rule.onNodeWithText(text(R.string.button_ok)).assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextReplacement("phrase")
        click(R.string.button_ok)
        verify(vm).submitKeyPassphrase("phrase")
        click(R.string.button_cancel)
        verify(vm).cancelKeyPassphrase()
    }

    @Test
    fun transferProgressShowsUploadAndDownloadBatchDetailsAndCancels() {
        update(state.value.copy(transferProgress = TransferProgress("file", true, 1, 0)))
        render()
        rule.onNodeWithText(text(R.string.sftp_transfer_uploading, "file")).assertIsDisplayed()
        val sizes = listOf(512L, 4096L, 4L * 1024 * 1024, 4L * 1024 * 1024 * 1024)
        for (size in sizes) {
            update(state.value.copy(transferProgress = TransferProgress("file", false, size / 2, size, 2, 3)))
            rule.onNodeWithText(text(R.string.sftp_transfer_downloading, "file")).assertIsDisplayed()
            rule.onNodeWithText(text(R.string.sftp_transfer_file_count, 2, 3)).assertIsDisplayed()
        }
        click(R.string.button_cancel)
        verify(vm).cancelTransfer()
    }

    @Test
    fun entriesDisplaySizesDatesAndSymlinkMetadata() {
        val entries = listOf(
            file("unknown").copy(size = null, modifiedTime = null),
            file("bytes").copy(size = 100),
            file("kilobytes").copy(size = 4096),
            file("megabytes").copy(size = 4L * 1024 * 1024),
            file("gigabytes").copy(size = 4L * 1024 * 1024 * 1024),
            file("link").copy(isSymlink = true),
        )
        update(state.value.copy(entries = entries))
        render()
        rule.onNodeWithText("unknown").assertIsDisplayed()
        rule.onNodeWithText("100 B", substring = true).assertIsDisplayed()
        rule.onNodeWithText("4.0 KB", substring = true).assertIsDisplayed()
        rule.onNodeWithText("4.0 MB", substring = true).assertIsDisplayed()
        rule.onNodeWithText("4.0 GB", substring = true).assertIsDisplayed()
        assertTrue(entries.last().isSymlink)
    }
}
