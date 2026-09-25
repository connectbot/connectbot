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
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Installs the terminfo data accompanying the Mosh executable delivered by Google Play Feature Delivery. */
object InstallMosh {
    const val MODULE_NAME = "mosh"
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
    internal var splitInstallManagerProvider: (Context) -> SplitInstallManager = { SplitInstallManagerFactory.create(it) }

    fun isInstalled(context: Context): Boolean = try {
        val splitInstallManager = splitInstallManagerProvider(context)
        if (!splitInstallManager.installedModules.contains(MODULE_NAME)) {
            false
        } else if (getMoshClientPath(context) == null) {
            false
        } else {
            val terminfoFile = File(context.filesDir, "$TERMINFO_DIR/$TERMINFO_FILE")
            terminfoFile.isFile
        }
    } catch (e: Exception) {
        false
    }

    fun startInstall(context: Context) {
        val appContext = context.applicationContext
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

    fun getMoshClientPath(context: Context): String? {
        val direct = File(context.applicationInfo.nativeLibraryDir, CLIENT_NAME)
        if (direct.isFile && direct.canExecute()) return direct.absolutePath

        val splitCompatDir = File(context.filesDir, "splitcompat")
        if (splitCompatDir.isDirectory) {
            val found = splitCompatDir.walkTopDown().firstOrNull { it.name == CLIENT_NAME && it.isFile && it.canExecute() }
            if (found != null) return found.absolutePath
        }

        return null
    }

    fun installClient(context: Context): InstallResult = installBundledClient(context.applicationContext)

    @Volatile
    internal var downloadHandler: (SplitInstallManager, SplitInstallRequest) -> Boolean = ::downloadModule

    @Volatile
    internal var assetOpener: (Context, String) -> InputStream = ::defaultOpenAsset

    internal fun resetForTest() {
        lock.withLock {
            installDone = false
            installSucceeded = false
            installThread = null
            terminfoPath = null
            splitInstallManagerProvider = { SplitInstallManagerFactory.create(it) }
            downloadHandler = ::downloadModule
            assetOpener = ::defaultOpenAsset
            installComplete.signalAll()
        }
    }

    private fun defaultOpenAsset(context: Context, name: String): InputStream = try {
        context.assets.open(name)
    } catch (e: Exception) {
        try {
            context.createPackageContext(context.packageName, 0).assets.open(name)
        } catch (_: Exception) {
            throw e
        }
    }

    internal fun downloadModule(manager: SplitInstallManager, request: SplitInstallRequest): Boolean {
        val latch = CountDownLatch(1)
        var success = false
        var targetSessionId = -1
        var pendingState: SplitInstallSessionState? = null

        val listener = object : SplitInstallStateUpdatedListener {
            override fun onStateUpdate(state: SplitInstallSessionState) {
                if (targetSessionId == -1) {
                    pendingState = state
                    return
                }
                if (state.sessionId() != targetSessionId) return
                when (state.status()) {
                    SplitInstallSessionStatus.INSTALLED -> {
                        success = true
                        manager.unregisterListener(this)
                        latch.countDown()
                    }

                    SplitInstallSessionStatus.FAILED,
                    SplitInstallSessionStatus.CANCELED,
                    -> {
                        Timber.w("SplitInstall failed with error code: %d", state.errorCode())
                        success = false
                        manager.unregisterListener(this)
                        latch.countDown()
                    }
                }
            }
        }

        manager.registerListener(listener)
        manager.startInstall(request)
            .addOnSuccessListener { sessionId ->
                if (sessionId == 0) {
                    success = true
                    manager.unregisterListener(listener)
                    latch.countDown()
                } else {
                    targetSessionId = sessionId
                    pendingState?.let { state ->
                        pendingState = null
                        if (state.sessionId() == targetSessionId) {
                            if (state.status() == SplitInstallSessionStatus.INSTALLED) {
                                success = true
                                manager.unregisterListener(listener)
                                latch.countDown()
                            } else if (state.status() == SplitInstallSessionStatus.FAILED ||
                                state.status() == SplitInstallSessionStatus.CANCELED
                            ) {
                                success = false
                                manager.unregisterListener(listener)
                                latch.countDown()
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Timber.w(e, "SplitInstall startInstall failed")
                manager.unregisterListener(listener)
                latch.countDown()
            }

        try {
            latch.await(30, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            manager.unregisterListener(listener)
            return false
        }
        return success
    }

    private fun installBundledClient(context: Context): InstallResult {
        val result = try {
            SplitCompatLoader.install(context)
            val splitInstallManager = splitInstallManagerProvider(context)
            if (!splitInstallManager.installedModules.contains(MODULE_NAME)) {
                val request = SplitInstallRequest.newBuilder().addModule(MODULE_NAME).build()
                val downloaded = downloadHandler(splitInstallManager, request)
                check(downloaded) { "Failed to download Mosh feature from Google Play" }
                SplitCompatLoader.install(context)
            }

            val releaseTag = assetOpener(context, "mosh-release.txt").bufferedReader().use { it.readText().trim() }
            check(getMoshClientPath(context) != null) { "Play-delivered Mosh executable is unavailable" }
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

            check(terminfoTarget.isFile) { "Play-delivered terminfo is incomplete" }
            terminfoPath = File(destination, "share/terminfo").absolutePath
            PreferenceManager.getDefaultSharedPreferences(context).edit {
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
