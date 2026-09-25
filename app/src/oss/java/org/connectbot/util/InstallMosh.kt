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
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Utility class to configure the external mosh-client executable and terminfo database bundled inside the OSS APK.
 */
object InstallMosh {
    private const val CLIENT_NAME = "libmoshexec.so"
    private const val TERMINFO_DIR = "terminfo"
    private const val TERMINFO_FILE = "share/terminfo/x/xterm-256color"

    private val lock = ReentrantLock()
    private val installComplete = lock.newCondition()

    @Volatile
    private var installDone = false

    @Volatile
    private var installSucceeded = false

    @Volatile
    private var installThread: Thread? = null

    @Volatile
    private var terminfoPath: String? = null

    @Volatile
    internal var assetOpener: (Context, String) -> InputStream = { ctx, name -> ctx.assets.open(name) }

    fun isInstalled(context: Context): Boolean {
        if (getMoshClientPath(context) == null) return false
        val terminfoFile = File(context.filesDir, "$TERMINFO_DIR/$TERMINFO_FILE")
        return terminfoFile.isFile
    }

    fun startInstall(context: Context) {
        val appContext = context.applicationContext
        lock.withLock {
            if (installThread != null || (installDone && installSucceeded)) return
            installDone = false
            installThread = Thread {
                installBundledClient(appContext)
            }.apply {
                name = "MoshInstaller"
                isDaemon = true
                start()
            }
        }
    }

    fun waitForInstall(timeoutMs: Long = 0): Boolean = lock.withLock {
        try {
            if (timeoutMs > 0) {
                var remaining = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
                while (!installDone && remaining > 0) {
                    remaining = installComplete.awaitNanos(remaining)
                }
                installDone
            } else {
                while (!installDone) installComplete.await()
                true
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    fun isInstallDone(): Boolean = installDone

    fun getTerminfoPath(): String? = terminfoPath

    fun getMoshClientPath(context: Context): String? {
        val direct = File(context.applicationInfo.nativeLibraryDir, CLIENT_NAME)
        if (direct.isFile && (direct.canExecute() || direct.setExecutable(true, true))) {
            return direct.absolutePath
        }
        return null
    }

    fun installClient(context: Context): InstallResult = installBundledClient(context.applicationContext)

    private fun installBundledClient(context: Context): InstallResult {
        val result = try {
            val clientPath = getMoshClientPath(context)
            check(clientPath != null) { "Bundled Mosh executable is unavailable in native library directory" }

            val releaseTag = assetOpener(context, "mosh-release.txt").bufferedReader().use { it.readText().trim() }
            val destination = File(context.filesDir, TERMINFO_DIR)
            val terminfoTarget = File(destination, TERMINFO_FILE)

            if (!terminfoTarget.isFile ||
                PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(PreferenceConstants.MOSH_RELEASE_TAG, null) != releaseTag
            ) {
                terminfoTarget.parentFile?.mkdirs()
                assetOpener(context, TERMINFO_FILE).use { input ->
                    terminfoTarget.outputStream().use { output -> input.copyTo(output) }
                }
            }

            check(terminfoTarget.isFile) { "Bundled terminfo is incomplete" }
            terminfoPath = File(destination, "share/terminfo").absolutePath

            PreferenceManager.getDefaultSharedPreferences(context).edit {
                putString(PreferenceConstants.MOSH_RELEASE_TAG, releaseTag)
            }
            InstallResult(true, releaseTag, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize bundled Mosh")
            InstallResult(false, null, e.message ?: "Failed to initialize bundled Mosh")
        }

        lock.withLock {
            installDone = true
            installSucceeded = result.success
            installThread = null
            installComplete.signalAll()
        }
        return result
    }

    internal fun resetForTest() {
        lock.withLock {
            installDone = false
            installSucceeded = false
            installThread = null
            terminfoPath = null
            assetOpener = { ctx, name -> ctx.assets.open(name) }
            installComplete.signalAll()
        }
    }

    data class InstallResult(
        val success: Boolean,
        val releaseTag: String?,
        val errorMessage: String?,
    )
}
