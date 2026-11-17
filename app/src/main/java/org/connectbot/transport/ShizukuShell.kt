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

package org.connectbot.transport

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.BuildConfig
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.DisconnectReason
import org.connectbot.transport.shizuku.IShizukuShellService
import org.connectbot.transport.shizuku.ShizukuShellService
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resume

/**
 * Local shell running with the privileges of the Shizuku server (the adb shell
 * user, or root), hosted by [ShizukuShellService].
 */
class ShizukuShell : AbsTransport() {
    private var service: IShizukuShellService? = null
    private var connection: ServiceConnection? = null
    private var serviceArgs: Shizuku.UserServiceArgs? = null
    private var session = 0

    /** Last size reported by the terminal, which usually arrives before the shell exists. */
    @Volatile
    private var windowSize: WindowSize? = null

    private var input: InputStream? = null
    private var output: OutputStream? = null

    override suspend fun connect(scope: CoroutineScope, dispatchers: CoroutineDispatchers, encoding: String) {
        val context = manager ?: throw IOException("Terminal service unavailable")

        if (!awaitShizuku()) fail(context, R.string.shizuku_not_running)
        if (!requestPermission(context)) fail(context, R.string.shizuku_permission_denied)

        try {
            val shell = bindService(context) ?: throw IOException("Timed out starting the Shizuku user service")
            // The app owns both pipes; the service only relays, so the session
            // ends cleanly whichever process goes away first.
            val toShell = ParcelFileDescriptor.createPipe()
            val fromShell = ParcelFileDescriptor.createPipe()
            try {
                session = shell.openShell(toShell[0], fromShell[1])
            } catch (e: Exception) {
                toShell[1].close()
                fromShell[0].close()
                throw e
            } finally {
                toShell[0].close()
                fromShell[1].close()
            }
            service = shell
            windowSize?.let { applyWindowSize(shell, session, it) }
            output = ParcelFileDescriptor.AutoCloseOutputStream(toShell[1])
            input = ParcelFileDescriptor.AutoCloseInputStream(fromShell[0])
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Cannot start Shizuku shell")
            unbind()
            bridge?.outputLine(context.getString(R.string.shizuku_shell_failed, e.message ?: e.javaClass.simpleName))
            throw IOException("Cannot start Shizuku shell", e)
        }

        bridge?.onConnected()
    }

    private fun fail(context: Context, message: Int): Nothing {
        val text = context.getString(message)
        bridge?.outputLine(text)
        throw IOException(text)
    }

    /** The binder arrives asynchronously after process start, so allow it a moment. */
    private suspend fun awaitShizuku(): Boolean {
        if (Shizuku.pingBinder()) return !Shizuku.isPreV11()
        val received = withTimeoutOrNull(BINDER_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Shizuku.OnBinderReceivedListener {
                    override fun onBinderReceived() {
                        Shizuku.removeBinderReceivedListener(this)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
                continuation.invokeOnCancellation { Shizuku.removeBinderReceivedListener(listener) }
                Shizuku.addBinderReceivedListenerSticky(listener)
            }
        }
        return received != null && Shizuku.pingBinder() && !Shizuku.isPreV11()
    }

    private suspend fun requestPermission(context: Context): Boolean {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
        // Shizuku reports this once the user chose "deny and don't ask again".
        if (Shizuku.shouldShowRequestPermissionRationale()) return false

        bridge?.outputLine(context.getString(R.string.shizuku_requesting_permission))
        return suspendCancellableCoroutine { continuation ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != PERMISSION_REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (continuation.isActive) continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            continuation.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
    }

    private suspend fun bindService(context: Context): IShizukuShellService? {
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShizukuShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shizuku_shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
        serviceArgs = args

        return withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val serviceConnection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
                        if (binder == null || !binder.pingBinder()) return
                        if (continuation.isActive) continuation.resume(IShizukuShellService.Stub.asInterface(binder))
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        // The pipes reach end of file when the service dies, which
                        // ends the session through the normal read path.
                        service = null
                    }
                }
                connection = serviceConnection
                Shizuku.bindUserService(args, serviceConnection)
            }
        }
    }

    private fun unbind() {
        val args = serviceArgs
        val serviceConnection = connection
        serviceArgs = null
        connection = null
        service = null
        if (args == null || serviceConnection == null) return
        try {
            // Other sessions share the user service, so leave it running.
            Shizuku.unbindUserService(args, serviceConnection, false)
        } catch (e: Exception) {
            Timber.w(e, "Couldn't unbind Shizuku user service")
        }
    }

    override suspend fun close() {
        val shell = service
        val sessionId = session
        session = 0
        try {
            output?.close()
            input?.close()
        } catch (e: IOException) {
            Timber.e(e, "Couldn't close Shizuku shell streams")
        }
        output = null
        input = null
        if (shell != null && sessionId > 0) {
            try {
                shell.closeShell(sessionId)
            } catch (e: RemoteException) {
                Timber.w(e, "Shizuku user service already gone")
            }
        }
        unbind()
    }

    @Throws(IOException::class)
    override suspend fun flush() {
        output?.flush()
    }

    override fun getDefaultNickname(username: String?, hostname: String?, port: Int): String = DEFAULT_URI

    override fun getDefaultPort(): Int = 0

    override fun isConnected(): Boolean = input != null && output != null

    override fun isSessionOpen(): Boolean = input != null && output != null

    @Throws(IOException::class)
    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val stream = input ?: run {
            bridge?.dispatchDisconnect(DisconnectReason.IO_ERROR)
            throw IOException("session closed")
        }
        return stream.read(buffer, offset, length)
    }

    /** The service closes the output pipe when the shell exits. */
    override fun onReadEof() {
        bridge?.dispatchDisconnect(DisconnectReason.REMOTE_EOF)
    }

    override suspend fun resize(columns: Int, rows: Int, width: Int, height: Int) {
        val size = WindowSize(columns, rows, width, height)
        windowSize = size
        val shell = service ?: return
        val sessionId = session
        if (sessionId <= 0) return
        applyWindowSize(shell, sessionId, size)
    }

    private fun applyWindowSize(shell: IShizukuShellService, sessionId: Int, size: WindowSize) {
        try {
            shell.resize(sessionId, size.columns, size.rows, size.width, size.height)
        } catch (e: RemoteException) {
            Timber.e(e, "Couldn't resize Shizuku shell")
        }
    }

    @Throws(IOException::class)
    override suspend fun write(buffer: ByteArray) {
        output?.write(buffer)
    }

    override fun createHost(uri: Uri): Host {
        var nickname = uri.fragment
        if (nickname.isNullOrEmpty()) {
            nickname = getDefaultNickname("", "", 0)
        }
        return Host.createShizukuHost(nickname)
    }

    override fun getSelectionArgs(uri: Uri, selection: MutableMap<String, String>) {
        selection["protocol"] = PROTOCOL
        selection["nickname"] = uri.fragment ?: ""
    }

    override fun usesNetwork(): Boolean = false

    override fun getLocalIpAddress(): String? = null

    private data class WindowSize(val columns: Int, val rows: Int, val width: Int, val height: Int)

    companion object {
        private const val PROTOCOL = "shizuku"
        private const val DEFAULT_URI = "shizuku:#Shizuku"
        private const val PERMISSION_REQUEST_CODE = 0x5348
        private const val BINDER_TIMEOUT_MS = 3_000L
        private const val BIND_TIMEOUT_MS = 15_000L

        @JvmStatic
        fun getProtocolName(): String = PROTOCOL

        @JvmStatic
        fun getUri(input: String?): Uri {
            var uri = DEFAULT_URI.toUri()

            if (!input.isNullOrEmpty()) {
                uri = uri.buildUpon().fragment(input).build()
            }

            return uri
        }

        @JvmStatic
        fun getFormatHint(context: Context): String = context.getString(R.string.hostpref_nickname_title)
    }
}
