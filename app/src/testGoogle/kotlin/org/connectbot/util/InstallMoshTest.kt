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

package org.connectbot.util

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallManager
import org.connectbot.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class InstallMoshTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var nativeDir: File
    private var originalNativeDir: String? = null
    private lateinit var terminfoDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        nativeDir = temporaryFolder.newFolder("mosh-native")
        originalNativeDir = context.applicationInfo.nativeLibraryDir
        terminfoDir = File(context.filesDir, "terminfo")
        terminfoDir.deleteRecursively()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        val client = File(nativeDir, "libmoshexec.so")
        client.writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))
        client.setExecutable(true)
        context.applicationInfo.nativeLibraryDir = nativeDir.absolutePath
        InstallMosh.assetOpener = { _, name ->
            File("src/testGoogle/assets", name).takeIf { it.isFile }?.inputStream()
                ?: File("app/src/testGoogle/assets", name).inputStream()
        }
    }

    @After
    fun tearDown() {
        context.applicationInfo.nativeLibraryDir = originalNativeDir
        nativeDir.deleteRecursively()
        terminfoDir.deleteRecursively()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        InstallMosh.resetForTest()
    }

    @Test
    fun installClient_whenModuleAlreadyInstalled_usesPlayPayloadAndRecordsReleaseTag() {
        val splitInstallManager = mock(SplitInstallManager::class.java)
        `when`(splitInstallManager.installedModules).thenReturn(setOf(InstallMosh.MODULE_NAME))
        InstallMosh.splitInstallManagerProvider = { splitInstallManager }

        val result = InstallMosh.installClient(context)

        assertTrue(result.errorMessage.orEmpty(), result.success)
        assertEquals(BuildConfig.MOSH_RELEASE_TAG, result.releaseTag)
        assertEquals(
            BuildConfig.MOSH_RELEASE_TAG,
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PreferenceConstants.MOSH_RELEASE_TAG, null),
        )
        assertTrue(File(InstallMosh.getTerminfoPath(), "x/xterm-256color").isFile)
        assertEquals(File(nativeDir, "libmoshexec.so").absolutePath, InstallMosh.getMoshClientPath(context))
    }

    @Test
    fun installClient_whenModuleNeedsDownloadAndSucceeds_completesSuccessfully() {
        val splitInstallManager = mock(SplitInstallManager::class.java)
        `when`(splitInstallManager.installedModules).thenReturn(emptySet())
        InstallMosh.splitInstallManagerProvider = { splitInstallManager }
        InstallMosh.downloadHandler = { _, _ -> true }

        val result = InstallMosh.installClient(context)

        assertTrue(result.errorMessage.orEmpty(), result.success)
        assertEquals(BuildConfig.MOSH_RELEASE_TAG, result.releaseTag)
        assertTrue(InstallMosh.isInstallDone())
    }

    @Test
    fun installClient_whenDownloadFails_returnsFailure() {
        val splitInstallManager = mock(SplitInstallManager::class.java)
        `when`(splitInstallManager.installedModules).thenReturn(emptySet())
        InstallMosh.splitInstallManagerProvider = { splitInstallManager }
        InstallMosh.downloadHandler = { _, _ -> false }

        val result = InstallMosh.installClient(context)

        assertFalse(result.success)
        assertEquals("Failed to download Mosh feature from Google Play", result.errorMessage)
    }

    @Test
    fun isInstalled_whenModuleNotInstalled_returnsFalse() {
        val splitInstallManager = mock(SplitInstallManager::class.java)
        `when`(splitInstallManager.installedModules).thenReturn(emptySet())
        InstallMosh.splitInstallManagerProvider = { splitInstallManager }

        assertFalse(InstallMosh.isInstalled(context))
    }

    @Test
    fun isInstalled_whenModuleInstalledAndFilesPresent_returnsTrue() {
        val splitInstallManager = mock(SplitInstallManager::class.java)
        `when`(splitInstallManager.installedModules).thenReturn(setOf(InstallMosh.MODULE_NAME))
        InstallMosh.splitInstallManagerProvider = { splitInstallManager }

        InstallMosh.installClient(context)
        assertTrue(InstallMosh.isInstalled(context))
    }
}
