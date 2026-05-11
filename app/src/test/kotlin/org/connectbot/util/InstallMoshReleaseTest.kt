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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class InstallMoshReleaseTest {
    @Test
    fun releaseApiUrl_usesMosh4AndroidLatestRelease() {
        assertEquals(
            "https://api.github.com/repos/connectbot/mosh4android/releases/latest",
            InstallMosh.releaseApiUrl(),
        )
    }

    @Test
    fun findReleaseAssetDownloadUrl_selectsMatchingAbiZip() {
        val releaseJson = """
            {
              "tag_name": "android-2026.05.01",
              "assets": [
                {
                  "name": "mosh-android-x86.zip",
                  "browser_download_url": "https://example.invalid/x86.zip"
                },
                {
                  "name": "mosh-android-arm64-v8a.zip",
                  "browser_download_url": "https://example.invalid/arm64.zip"
                }
              ]
            }
        """.trimIndent()

        assertEquals(
            "https://example.invalid/arm64.zip",
            InstallMosh.findReleaseAssetDownloadUrl(releaseJson, "arm64-v8a"),
        )
    }

    @Test
    fun findReleaseAssetDownloadUrl_returnsNullWhenAbiMissing() {
        val releaseJson = """
            {
              "assets": [
                {
                  "name": "mosh-android-x86.zip",
                  "browser_download_url": "https://example.invalid/x86.zip"
                }
              ]
            }
        """.trimIndent()

        assertNull(InstallMosh.findReleaseAssetDownloadUrl(releaseJson, "armeabi-v7a"))
    }

    @Test
    fun installReleaseZip_extractsExecutableClient() {
        val tempDir = Files.createTempDirectory("mosh-release-test").toFile()
        try {
            val downloadDir = File(tempDir, "mosh").apply { mkdirs() }
            val terminfoDir = File(tempDir, "terminfo")

            ZipInputStream(ByteArrayInputStream(createReleaseZip())).use { zipStream ->
                InstallMosh.installReleaseZip(zipStream, downloadDir, terminfoDir)
            }

            assertTrue(File(downloadDir, "mosh-client").isFile)
            assertTrue(File(downloadDir, "mosh-client").canExecute())
            assertTrue(File(terminfoDir, "share/terminfo/x/xterm-256color").isFile)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun installReleaseZip_doesNotTreatSharedObjectAsExecutableClient() {
        val tempDir = Files.createTempDirectory("mosh-release-test").toFile()
        try {
            val downloadDir = File(tempDir, "mosh").apply { mkdirs() }
            val terminfoDir = File(tempDir, "terminfo")

            ZipInputStream(ByteArrayInputStream(createReleaseZip(clientName = "libmosh-client.so"))).use { zipStream ->
                InstallMosh.installReleaseZip(zipStream, downloadDir, terminfoDir)
            }

            assertFalse(File(downloadDir, "mosh-client").exists())
            assertTrue(File(downloadDir, "libmosh-client.so").isFile)
            assertFalse(File(downloadDir, "libmosh-client.so").canExecute())
            assertTrue(File(terminfoDir, "share/terminfo/x/xterm-256color").isFile)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun startInstall_downloadsReleaseWhenEnabledButPayloadMissing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val calls = AtomicInteger()
        resetMoshState(context)

        try {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(PreferenceConstants.MOSH_SUPPORT, true)
                .commit()

            InstallMosh.releaseInstaller = { appContext ->
                calls.incrementAndGet()
                installFakeRelease(appContext)
                InstallMosh.InstallResult(true, "android-test", null)
            }

            InstallMosh.startInstall(context)

            assertTrue(InstallMosh.waitForInstall(5000))
            assertEquals(1, calls.get())
            assertNotNull(InstallMosh.getMoshClientPath(context))
            assertNotNull(InstallMosh.getTerminfoPath())
        } finally {
            InstallMosh.resetForTest()
            resetMoshState(context)
        }
    }

    private fun resetMoshState(context: Context) {
        InstallMosh.resetForTest()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        File(context.filesDir, "mosh").deleteRecursively()
        File(context.filesDir, "terminfo").deleteRecursively()
        File(context.filesDir, ".mosh_installed").delete()
    }

    private fun installFakeRelease(context: Context) {
        val moshDir = File(context.filesDir, "mosh").apply { mkdirs() }
        val client = File(moshDir, "mosh-client")
        client.writeBytes(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))
        client.setReadable(true, true)
        client.setExecutable(true, true)
        client.setWritable(false, true)

        val terminfoFile = File(context.filesDir, "terminfo/share/terminfo/x/xterm-256color")
        terminfoFile.parentFile?.mkdirs()
        terminfoFile.writeBytes(byteArrayOf(1))

        File(context.filesDir, ".mosh_installed").createNewFile()
    }

    private fun createReleaseZip(clientName: String = "mosh-client"): ByteArray {
        val terminfoZipBytes = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("share/terminfo/x/xterm-256color"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
            bytes.toByteArray()
        }

        return ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry(clientName))
                zip.write(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("terminfo.zip"))
                zip.write(terminfoZipBytes)
                zip.closeEntry()
            }
            bytes.toByteArray()
        }
    }
}
