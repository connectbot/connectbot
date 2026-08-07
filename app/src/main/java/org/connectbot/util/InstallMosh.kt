/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2012-2026 Kenny Root
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
import android.os.Build
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipInputStream
import kotlin.concurrent.withLock

/**
 * Utility class to install the external mosh-client executable and terminfo
 * database from a mosh4android release archive.
 * This is thread-safe with a wait/notify mechanism for callers who need
 * to wait for installation to complete.
 */
object InstallMosh {
    private const val MOSH_CLIENT_FILE = "mosh-client"
    private const val MOSH_CLIENT_SO_FILE = "libmosh-client.so"
    private const val MOSH_DOWNLOAD_DIR = "mosh"
    private const val MOSH_RELEASE_REPO = "connectbot/mosh4android"
    private const val TERMINFO_ZIP = "terminfo.zip"
    private const val TERMINFO_DIR = "terminfo"
    private const val TERMINFO_ZIP_ROOT = "share/terminfo"
    private const val INSTALL_MARKER = ".mosh_installed"
    private const val EMBEDDED_CLIENT_MESSAGE =
        "Release asset contains only embedded $MOSH_CLIENT_SO_FILE; executable $MOSH_CLIENT_FILE is required"

    private val lock = ReentrantLock()
    private val installComplete = lock.newCondition()

    @Volatile
    private var installDone = false

    @Volatile
    private var installThread: Thread? = null

    @Volatile
    private var terminfoPath: String? = null

    @Volatile
    internal var releaseInstaller: (Context) -> InstallResult = ::downloadLatestRelease

    /**
     * Start the installation process in the background.
     * This method returns immediately. Use waitForInstall() if you need
     * to wait for installation to complete.
     *
     * @param context The application context
     */
    fun startInstall(context: Context) {
        val appContext = context.applicationContext
        if (!isMoshSupportEnabled(appContext)) {
            return
        }

        lock.withLock {
            if (installThread != null) {
                return
            }
            if (installDone && refreshInstalledResources(appContext)) {
                return
            }
            installDone = false

            installThread = Thread {
                performInstall(appContext)
            }.apply {
                name = "MoshInstaller"
                isDaemon = true
                start()
            }
        }
    }

    /**
     * Wait for the installation to complete.
     * This will block until installation is done.
     *
     * @param timeoutMs Maximum time to wait in milliseconds, or 0 for no timeout
     * @return true if installation completed successfully, false if timed out
     */
    fun waitForInstall(timeoutMs: Long = 0): Boolean {
        lock.withLock {
            if (installDone) {
                return true
            }

            return try {
                if (timeoutMs > 0) {
                    val deadline = System.currentTimeMillis() + timeoutMs
                    while (!installDone) {
                        val remaining = deadline - System.currentTimeMillis()
                        if (remaining <= 0) {
                            return false
                        }
                        installComplete.await(remaining, java.util.concurrent.TimeUnit.MILLISECONDS)
                    }
                    true
                } else {
                    while (!installDone) {
                        installComplete.await()
                    }
                    true
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }
    }

    /**
     * Check if installation is complete.
     *
     * @return true if installation has completed
     */
    fun isInstallDone(): Boolean = installDone

    /**
     * Get the path to the terminfo directory.
     * Returns null if installation is not complete.
     *
     * @return Path to terminfo directory, or null
     */
    fun getTerminfoPath(): String? = terminfoPath

    fun isMoshSupportEnabled(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(PreferenceConstants.MOSH_SUPPORT, false)

    fun setMoshSupportEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(PreferenceConstants.MOSH_SUPPORT, enabled)
        }
        if (!enabled) {
            lock.withLock {
                installDone = false
                installThread = null
                terminfoPath = null
                installComplete.signalAll()
            }
        }
    }

    fun installLatestRelease(context: Context): InstallResult {
        val appContext = context.applicationContext
        markInstallStarted()
        val result = try {
            releaseInstaller(appContext)
        } catch (e: Exception) {
            Timber.e(e, "Failed to install latest mosh4android release")
            InstallResult(false, null, e.message ?: "Failed to install mosh4android release")
        }
        if (result.success && !refreshInstalledResources(appContext)) {
            markInstallComplete()
            return result.copy(success = false, errorMessage = "Mosh release installed without usable payload")
        }
        markInstallComplete()
        return result
    }

    private fun downloadLatestRelease(appContext: Context): InstallResult {
        return try {
            val releaseJson = httpGet(releaseApiUrl())
            val tagName = JSONObject(releaseJson).optString("tag_name", "")
            val abi = Build.SUPPORTED_ABIS.firstOrNull()
                ?: return InstallResult(false, null, "No supported Android ABI found")
            val assetUrl = findReleaseAssetDownloadUrl(releaseJson, abi)
                ?: return InstallResult(false, tagName.ifBlank { null }, "No mosh4android release asset found for $abi")

            val filesDir = appContext.filesDir
            val downloadDir = File(filesDir, MOSH_DOWNLOAD_DIR)
            val terminfoDir = File(filesDir, TERMINFO_DIR)
            downloadDir.deleteRecursively()
            terminfoDir.deleteRecursively()
            downloadDir.mkdirs()

            downloadZip(assetUrl).use { zipStream ->
                installReleaseZip(zipStream, downloadDir, terminfoDir)
            }

            val installedClient = File(downloadDir, MOSH_CLIENT_FILE)
            if (!installedClient.isUsableExecutable()) {
                if (File(downloadDir, MOSH_CLIENT_SO_FILE).isFile) {
                    return InstallResult(false, tagName.ifBlank { null }, EMBEDDED_CLIENT_MESSAGE)
                }
                return InstallResult(
                    false,
                    tagName.ifBlank { null },
                    "Release asset did not contain usable $MOSH_CLIENT_FILE",
                )
            }

            val resolvedTerminfoPath = resolveTerminfoPath(terminfoDir)
                ?: return InstallResult(false, tagName.ifBlank { null }, "Release asset did not contain xterm-256color terminfo")

            File(filesDir, INSTALL_MARKER).createNewFile()
            terminfoPath = resolvedTerminfoPath

            PreferenceManager.getDefaultSharedPreferences(appContext).edit {
                putBoolean(PreferenceConstants.MOSH_SUPPORT, true)
                putString(PreferenceConstants.MOSH_RELEASE_TAG, tagName)
            }
            InstallResult(true, tagName.ifBlank { null }, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to install latest mosh4android release")
            InstallResult(false, null, e.message ?: "Failed to install mosh4android release")
        }
    }

    /**
     * Get the downloaded mosh-client executable path.
     */
    fun getMoshClientPath(context: Context): String? {
        val downloadedClient = File(File(context.filesDir, MOSH_DOWNLOAD_DIR), MOSH_CLIENT_FILE)
        if (downloadedClient.isUsableExecutable()) {
            return downloadedClient.absolutePath
        }

        return null
    }

    private fun performInstall(context: Context) {
        try {
            if (refreshInstalledResources(context)) {
                Timber.d("Mosh resources already installed")
                markInstallComplete()
                return
            }

            Timber.i("Mosh resources are missing; downloading latest mosh4android release")
            val result = installLatestRelease(context)
            if (!result.success) {
                Timber.w("Mosh install failed: ${result.errorMessage}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to install mosh resources")
            // Mark as complete anyway to avoid blocking forever
            markInstallComplete()
        }
    }

    private fun File.isUsableExecutable(): Boolean {
        if (!isFile) {
            return false
        }
        return canExecute() || setExecutable(true, true)
    }

    internal fun installReleaseZip(zipStream: ZipInputStream, downloadDir: File, terminfoDir: File) {
        var entry = zipStream.nextEntry
        while (entry != null) {
            val name = entry.name
            if (!entry.isDirectory) {
                when {
                    name.endsWith("/$MOSH_CLIENT_FILE") || name == MOSH_CLIENT_FILE -> {
                        val outputFile = File(downloadDir, MOSH_CLIENT_FILE)
                        FileOutputStream(outputFile).use { output -> zipStream.copyTo(output) }
                        outputFile.setReadable(true, true)
                        outputFile.setExecutable(true, true)
                        outputFile.setWritable(false, true)
                    }

                    name.endsWith("/$MOSH_CLIENT_SO_FILE") || name == MOSH_CLIENT_SO_FILE -> {
                        val outputFile = File(downloadDir, MOSH_CLIENT_SO_FILE)
                        FileOutputStream(outputFile).use { output -> zipStream.copyTo(output) }
                        outputFile.setReadable(true, true)
                        outputFile.setWritable(false, true)
                    }

                    name.endsWith("/$TERMINFO_ZIP") || name == TERMINFO_ZIP -> {
                        ZipInputStream(NonClosingInputStream(zipStream)).use { terminfoZip ->
                            extractZip(terminfoZip, terminfoDir)
                        }
                    }

                    name.contains("terminfo/") -> {
                        val terminfoEntry = name.substringAfter("terminfo/")
                        if (terminfoEntry.isNotBlank()) {
                            val destFile = File(terminfoDir, terminfoEntry)
                            writeZipEntry(zipStream, terminfoDir, destFile)
                        }
                    }
                }
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
    }

    private fun extractZip(zipStream: ZipInputStream, destDir: File) {
        destDir.mkdirs()

        var entry = zipStream.nextEntry
        while (entry != null) {
            val destFile = File(destDir, entry.name)

            // Security check: ensure the file is within destDir
            if (!destFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                throw SecurityException("Zip entry outside target directory: ${entry.name}")
            }

            if (entry.isDirectory) {
                destFile.mkdirs()
            } else {
                writeZipEntry(zipStream, destDir, destFile)
            }

            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
    }

    private fun writeZipEntry(zipStream: ZipInputStream, destDir: File, destFile: File) {
        if (!destFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
            throw SecurityException("Zip entry outside target directory: ${destFile.path}")
        }
        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { output ->
            zipStream.copyTo(output)
        }
    }

    private fun resolveTerminfoPath(installDir: File): String? {
        val directTerminfo = File(installDir, "x/xterm-256color")
        if (directTerminfo.isFile) {
            return installDir.absolutePath
        }

        val zippedTerminfoRoot = File(installDir, TERMINFO_ZIP_ROOT)
        val zippedTerminfo = File(zippedTerminfoRoot, "x/xterm-256color")
        if (zippedTerminfo.isFile) {
            return zippedTerminfoRoot.absolutePath
        }

        return null
    }

    private fun refreshInstalledResources(context: Context): Boolean {
        val filesDir = context.filesDir
        val downloadDir = File(filesDir, MOSH_DOWNLOAD_DIR)
        val downloadedClient = File(downloadDir, MOSH_CLIENT_FILE)
        val terminfoDir = File(filesDir, TERMINFO_DIR)
        val installMarker = File(filesDir, INSTALL_MARKER)

        if (!installMarker.exists() || !downloadedClient.isUsableExecutable()) {
            terminfoPath = null
            return false
        }

        val resolvedTerminfoPath = resolveTerminfoPath(terminfoDir)
        if (resolvedTerminfoPath == null) {
            Timber.w("Installed terminfo directory is missing xterm-256color; reinstalling")
            terminfoDir.deleteRecursively()
            installMarker.delete()
            terminfoPath = null
            return false
        }

        terminfoPath = resolvedTerminfoPath
        return true
    }

    private fun markInstallStarted() {
        lock.withLock {
            installDone = false
        }
    }

    private fun markInstallComplete() {
        lock.withLock {
            installDone = true
            installThread = null
            installComplete.signalAll()
        }
    }

    internal fun resetForTest() {
        lock.withLock {
            installDone = false
            installThread = null
            terminfoPath = null
            releaseInstaller = ::downloadLatestRelease
            installComplete.signalAll()
        }
    }

    internal fun releaseApiUrl(repo: String = MOSH_RELEASE_REPO): String = "https://api.github.com/repos/$repo/releases/latest"

    internal fun findReleaseAssetDownloadUrl(releaseJson: String, abi: String): String? {
        val assets = JSONObject(releaseJson).optJSONArray("assets") ?: return null
        val expectedNames = setOf(
            "mosh-android-$abi.zip",
            "mosh4android-$abi.zip",
            "mosh4android-android-$abi.zip",
        )
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name in expectedNames || (name.contains(abi) && name.endsWith(".zip"))) {
                return asset.optString("browser_download_url").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun downloadZip(url: String): ZipInputStream {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        return ZipInputStream(connection.inputStream.buffered())
    }

    data class InstallResult(
        val success: Boolean,
        val releaseTag: String?,
        val errorMessage: String?,
    )

    private class NonClosingInputStream(
        private val delegate: java.io.InputStream,
    ) : java.io.FilterInputStream(delegate) {
        override fun close() {
            // Keep the outer release archive open while extracting nested terminfo.zip.
        }
    }
}
