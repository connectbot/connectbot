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

package org.connectbot.transport.shizuku

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.Keep
import com.google.ase.Exec
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Shizuku user service that owns the pseudo-terminals of Shizuku shell sessions.
 *
 * Shizuku starts this class in a separate process that has the identity of the
 * Shizuku server (the adb shell user, or root), so it must not rely on the
 * application, its dependency graph, or its private storage. Terminal bytes are
 * relayed over pipes supplied by the app instead of handing the pseudo-terminal
 * to the app, which keeps the app's access independent of SELinux policy for
 * terminals created in another domain.
 */
@Keep
class ShizukuShellService : IShizukuShellService.Stub() {
    private val sessions = ConcurrentHashMap<Int, Session>()

    override fun destroy() {
        sessions.values.forEach { it.close() }
        exitProcess(0)
    }

    override fun openShell(input: ParcelFileDescriptor, output: ParcelFileDescriptor): Int {
        val pids = IntArray(1)
        val master = try {
            Exec.setenv("TERM", TERM)
            Exec.createSubprocess(SHELL, "-", null, pids)
        } catch (e: LinkageError) {
            closeQuietly(input, output)
            throw IllegalStateException("Native library unavailable: ${e.message}")
        }
        if (master == null || !master.valid() || pids[0] <= 0) {
            closeQuietly(input, output)
            throw IllegalStateException("Could not create a pseudo-terminal")
        }

        val session = Session(pids[0], master, input, output)
        sessions[session.pid] = session
        session.start()
        return session.pid
    }

    override fun resize(session: Int, columns: Int, rows: Int, width: Int, height: Int) {
        sessions[session]?.resize(columns, rows, width, height)
    }

    override fun closeShell(session: Int) {
        sessions[session]?.close()
    }

    private fun closeQuietly(vararg descriptors: ParcelFileDescriptor) {
        for (descriptor in descriptors) {
            try {
                descriptor.close()
            } catch (_: IOException) {
            }
        }
    }

    private inner class Session(
        val pid: Int,
        private val master: FileDescriptor,
        input: ParcelFileDescriptor,
        output: ParcelFileDescriptor,
    ) {
        private val ptyInput = FileInputStream(master)
        private val ptyOutput = FileOutputStream(master)
        private val fromApp = ParcelFileDescriptor.AutoCloseInputStream(input)
        private val toApp = ParcelFileDescriptor.AutoCloseOutputStream(output)

        private val lock = Any()
        private var masterOpen = true
        private var exited = false
        private var closed = false

        fun start() {
            thread(name = "ShizukuShell-$pid-out", isDaemon = true) {
                pump(ptyInput, toApp)
                // Only this thread reads the terminal, so only it may close the
                // descriptor: closing it elsewhere could let a blocked read land
                // on a reused descriptor number.
                synchronized(lock) {
                    masterOpen = false
                    closeQuietly(ptyInput)
                    closeQuietly(ptyOutput)
                }
                close()
            }
            thread(name = "ShizukuShell-$pid-in", isDaemon = true) {
                pump(fromApp, ptyOutput)
                close()
            }
            thread(name = "ShizukuShell-$pid-wait", isDaemon = true) {
                Exec.waitFor(pid)
                synchronized(lock) { exited = true }
                close()
            }
        }

        fun resize(columns: Int, rows: Int, width: Int, height: Int) {
            synchronized(lock) {
                if (masterOpen) Exec.setPtyWindowSize(master, rows, columns, width, height)
            }
        }

        fun close() {
            synchronized(lock) {
                if (closed) return
                closed = true
                // The process is reaped once it exits; signalling after that
                // could reach an unrelated process that reused the id.
                if (!exited) Exec.kill(pid, SIGHUP)
            }
            sessions.remove(pid)
            closeQuietly(toApp)
            closeQuietly(fromApp)
        }

        private fun pump(source: InputStream, sink: OutputStream) {
            val buffer = ByteArray(BUFFER_SIZE)
            try {
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    sink.write(buffer, 0, count)
                    sink.flush()
                }
            } catch (e: IOException) {
                // Expected at teardown: the terminal reports EIO once the shell
                // is gone, and the pipes break when the app disconnects.
                Log.d(TAG, "Session $pid stream ended: ${e.message}")
            }
        }

        private fun closeQuietly(stream: java.io.Closeable) {
            try {
                stream.close()
            } catch (_: IOException) {
            }
        }
    }

    private companion object {
        const val TAG = "CB.ShizukuShell"
        const val SHELL = "/system/bin/sh"
        const val TERM = "xterm-256color"
        const val SIGHUP = 1
        const val BUFFER_SIZE = 8192
    }
}
