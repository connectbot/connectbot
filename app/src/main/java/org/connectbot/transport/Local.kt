/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
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

import android.content.Context
import android.net.Uri
import timber.log.Timber
import androidx.annotation.VisibleForTesting
import com.google.ase.Exec
import org.connectbot.R
import org.connectbot.data.entity.Host
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * @author Kenny Root
 */
class Local @VisibleForTesting constructor(private val killer: Killer) : AbsTransport() {

    private var shellFd: FileDescriptor? = null
    private var shellPid: Int = 0

    private var `is`: FileInputStream? = null
    private var os: FileOutputStream? = null

    constructor() : this(AndroidKiller())

    override fun close() {
        try {
            os?.close()
            os = null
            `is`?.close()
            `is` = null
            killer.killProcess(shellPid)
        } catch (e: IOException) {
            Timber.e(e, "Couldn't close shell")
        }
    }

    override fun connect() {
        val pids = IntArray(1)

        try {
            shellFd = Exec.createSubprocess("/system/bin/sh", "-", null, pids)
        } catch (e: Exception) {
            bridge?.outputLine(manager?.res?.getString(R.string.local_shell_unavailable))
            Timber.e(e, "Cannot start local shell")
            throw e
        }

        shellPid = pids[0]
        val exitWatcher = Runnable {
            Exec.waitFor(shellPid)
            bridge?.dispatchDisconnect(false)
        }

        val exitWatcherThread = Thread(exitWatcher)
        exitWatcherThread.name = "LocalExitWatcher"
        exitWatcherThread.isDaemon = true
        exitWatcherThread.start()

        `is` = FileInputStream(shellFd)
        os = FileOutputStream(shellFd)

        bridge?.onConnected()
    }

    @Throws(IOException::class)
    override fun flush() {
        os?.flush()
    }

    override fun getDefaultNickname(username: String?, hostname: String?, port: Int): String =
        DEFAULT_URI

    override fun getDefaultPort(): Int = 0

    override fun isConnected(): Boolean = `is` != null && os != null

    override fun isSessionOpen(): Boolean = `is` != null && os != null

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val inputStream = `is` ?: run {
            bridge?.dispatchDisconnect(false)
            throw IOException("session closed")
        }
        return inputStream.read(buffer, offset, length)
    }

    override fun setDimensions(columns: Int, rows: Int, width: Int, height: Int) {
        // We are not connected yet.
        val fd = shellFd ?: return

        try {
            Exec.setPtyWindowSize(fd, rows, columns, width, height)
        } catch (e: Exception) {
            Timber.e(e, "Couldn't resize pty")
        }
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteArray) {
        os?.write(buffer)
    }

    @Throws(IOException::class)
    override fun write(c: Int) {
        os?.write(c)
    }

    override fun createHost(uri: Uri): Host {
        var nickname = uri.fragment
        if (nickname.isNullOrEmpty()) {
            nickname = getDefaultNickname("", "", 0)
        }
        return Host.createLocalHost(nickname)
    }

    override fun getSelectionArgs(uri: Uri, selection: MutableMap<String, String>) {
        selection["protocol"] = PROTOCOL
        selection["nickname"] = uri.fragment ?: ""
    }

    override fun usesNetwork(): Boolean = false

    @VisibleForTesting
    interface Killer {
        fun killProcess(pid: Int)
    }

    private class AndroidKiller : Killer {
        override fun killProcess(pid: Int) {
            android.os.Process.killProcess(pid)
        }
    }

    companion object {
        private const val TAG = "CB.Local"
        private const val PROTOCOL = "local"
        private const val DEFAULT_URI = "local:#Local"

        @JvmStatic
        fun getProtocolName(): String = PROTOCOL

        @JvmStatic
        fun getUri(input: String?): Uri {
            var uri = Uri.parse(DEFAULT_URI)

            if (!input.isNullOrEmpty()) {
                uri = uri.buildUpon().fragment(input).build()
            }

            return uri
        }

        @JvmStatic
        fun getFormatHint(context: Context): String =
            context.getString(R.string.hostpref_nickname_title)
    }
}
