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
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipInputStream
import kotlin.concurrent.withLock

/** Installs the terminfo data accompanying the Mosh executable delivered by Google Play. */
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

    fun isMoshSupportEnabled(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(PreferenceConstants.MOSH_SUPPORT, false)

    fun setMoshSupportEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(PreferenceConstants.MOSH_SUPPORT, enabled)
        }
        if (!enabled) {
            lock.withLock {
                installDone = false
                installSucceeded = false
                terminfoPath = null
                installComplete.signalAll()
            }
        }
    }

    fun startInstall(context: Context) {
        val appContext = context.applicationContext
        if (!isMoshSupportEnabled(appContext)) return
        lock.withLock {
            if (installThread != null || (installDone && installSucceeded)) return
            installDone = false
            installThread = Thread { installBundledClient(appContext) }.apply {
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

    fun getMoshClientPath(context: Context): String? = File(context.applicationInfo.nativeLibraryDir, CLIENT_NAME)
        .takeIf { it.isFile && it.canExecute() }?.absolutePath

    fun installClient(context: Context): InstallResult = installBundledClient(context.applicationContext)

    private fun installBundledClient(context: Context): InstallResult {
        val result = try {
            val releaseTag = context.assets.open("mosh-release.txt").bufferedReader().use { it.readText().trim() }
            check(getMoshClientPath(context) != null) { "Play-delivered Mosh executable is unavailable" }
            val destination = File(context.filesDir, TERMINFO_DIR)
            if (!File(destination, TERMINFO_FILE).isFile ||
                PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(PreferenceConstants.MOSH_RELEASE_TAG, null) != releaseTag
            ) {
                destination.deleteRecursively()
                ZipInputStream(context.assets.open("terminfo.zip").buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val target = File(destination, entry.name)
                        check(target.canonicalPath.startsWith(destination.canonicalPath + File.separator)) {
                            "Invalid terminfo entry: ${entry.name}"
                        }
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            check(File(destination, TERMINFO_FILE).isFile) { "Play-delivered terminfo is incomplete" }
            terminfoPath = File(destination, "share/terminfo").absolutePath
            PreferenceManager.getDefaultSharedPreferences(context).edit {
                putBoolean(PreferenceConstants.MOSH_SUPPORT, true)
                putString(PreferenceConstants.MOSH_RELEASE_TAG, releaseTag)
            }
            InstallResult(true, releaseTag, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to install Play-delivered Mosh")
            InstallResult(false, null, e.message ?: "Failed to install Mosh")
        }
        lock.withLock {
            installDone = true
            installSucceeded = result.success
            installThread = null
            installComplete.signalAll()
        }
        return result
    }

    data class InstallResult(val success: Boolean, val releaseTag: String?, val errorMessage: String?)
}
