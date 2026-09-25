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
import org.connectbot.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class InstallMoshTest {
    private lateinit var context: Context
    private lateinit var nativeDir: File
    private var originalNativeDir: String? = null
    private lateinit var terminfoDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        nativeDir = Files.createTempDirectory("mosh-oss-native").toFile()
        originalNativeDir = context.applicationInfo.nativeLibraryDir
        terminfoDir = File(context.filesDir, "terminfo")
        terminfoDir.deleteRecursively()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        val client = File(nativeDir, "libmoshexec.so")
        client.writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))
        client.setExecutable(true)
        context.applicationInfo.nativeLibraryDir = nativeDir.absolutePath
        InstallMosh.assetOpener = { _, name ->
            File("src/testOss/assets", name).takeIf { it.isFile }?.inputStream()
                ?: File("app/src/testOss/assets", name).inputStream()
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
    fun installClient_extractsBundledTerminfoAndSetsReleaseTag() {
        val result = InstallMosh.installClient(context)

        assertTrue(result.errorMessage.orEmpty(), result.success)
        assertEquals(BuildConfig.MOSH_RELEASE_TAG, result.releaseTag)
        assertEquals(
            BuildConfig.MOSH_RELEASE_TAG,
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PreferenceConstants.MOSH_RELEASE_TAG, null),
        )
        assertNotNull(InstallMosh.getTerminfoPath())
        assertTrue(File(InstallMosh.getTerminfoPath(), "x/xterm-256color").isFile)
        assertEquals(File(nativeDir, "libmoshexec.so").absolutePath, InstallMosh.getMoshClientPath(context))
        assertTrue(InstallMosh.isInstallDone())
    }

    @Test
    fun installClient_whenBinaryMissing_fails() {
        File(nativeDir, "libmoshexec.so").delete()

        val result = InstallMosh.installClient(context)

        assertFalse(result.success)
        assertNull(InstallMosh.getMoshClientPath(context))
    }

    @Test
    fun startInstall_completesSuccessfully() {
        InstallMosh.startInstall(context)

        assertTrue(InstallMosh.waitForInstall(5000))
        assertTrue(InstallMosh.isInstallDone())
        assertNotNull(InstallMosh.getTerminfoPath())
        assertEquals(File(nativeDir, "libmoshexec.so").absolutePath, InstallMosh.getMoshClientPath(context))
    }

    @Test
    fun isInstalled_whenNotInstalled_returnsFalse() {
        assertFalse(InstallMosh.isInstalled(context))
    }

    @Test
    fun isInstalled_whenInstalled_returnsTrue() {
        InstallMosh.installClient(context)
        assertTrue(InstallMosh.isInstalled(context))
    }
}
