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

package org.connectbot.service

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.transport.AbsTransport
import org.connectbot.util.PreferenceConstants
import org.connectbot.zmodemlib.Detector
import org.connectbot.zmodemlib.Download
import org.connectbot.zmodemlib.Framing
import org.connectbot.zmodemlib.Session
import org.connectbot.zmodemlib.Upload
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID

/** Service-owned transfer lifetime; activity results carry a unique request identity. */
class ZmodemController(
    private val bridge: TerminalBridge,
    private val scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) {
    private class TransferCancelled : IOException()
    private class TransportWriteFailure(cause: Exception) : IOException("Transfer transport write failed", cause)

    data class State(
        val active: Boolean = false,
        val picker: UUID? = null,
        val cancelling: Boolean = false,
        val upload: Boolean = false,
        val name: String = "",
        val bytes: Long = 0,
        val total: Long? = null,
        val error: String? = null,
    )

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    val active: Boolean get() = mutableState.value.active
    private val detector = Detector()
    private val mutex = Mutex()

    @Volatile private var cancellationRequested = false

    @Volatile private var suppressDetectionUntil = 0L

    @Volatile private var selection: CompletableDeferred<List<Uri>>? = null
    private var session: Session? = null
    private var source: AbsTransport? = null
    private var timer: Job? = null

    @Volatile private var lastInput = 0L
    private val temporary = mutableListOf<File>()
    private var incomplete: Uri? = null
    private var destination: Uri? = null
    private val resolver get() = bridge.manager.contentResolver
    private val preferenceListener = OnSharedPreferenceChangeListener { _, key ->
        if (key == PreferenceConstants.ZMODEM && !enabled && active) cancel()
    }
    private val enabled get() = bridge.manager.prefs.getBoolean(PreferenceConstants.ZMODEM, true)

    fun selected(id: UUID, uris: List<Uri>) {
        if (state.value.picker == id) selection?.complete(uris)
    }

    private suspend fun pick(upload: Boolean): List<Uri> {
        val pending = CompletableDeferred<List<Uri>>()
        selection = pending
        mutableState.value = state.value.copy(picker = UUID.randomUUID(), upload = upload)

        return try {
            withTimeout(120_000) { pending.await() }.also { if (it.isEmpty()) throw TransferCancelled() }
        } finally {
            selection = null
            mutableState.value = state.value.copy(picker = null)
        }
    }

    suspend fun accept(transport: AbsTransport, bytes: ByteArray): ByteArray = mutex.withLock {
        if (bridge.transport !== transport || !transport.supportsZmodem) return@withLock bytes

        if (!active && (!enabled || SystemClock.elapsedRealtime() < suppressDetectionUntil)) {
            return@withLock detector.finish() + bytes
        }

        lastInput = SystemClock.elapsedRealtime()

        try {
            if (active) {
                if (cancellationRequested) throw TransferCancelled()

                transport.verifyTransfer()

                val trailing = checkNotNull(session).accept(bytes)

                if (session!!.done) {
                    val error = session!!.failure
                    if (error != null) suppressDetectionUntil = SystemClock.elapsedRealtime() + 1000
                    cleanup(if (error == "Remote cancelled") null else error)
                }

                return@withLock trailing
            }

            val detection = detector.accept(bytes)
            val request = detection.request ?: return@withLock detection.terminal
            bridge.manager.prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
            source = transport
            cancellationRequested = false
            mutableState.value = State(active = true)
            bridge.beginTransfer()
            transport.prepareTransfer()
            val sending = request.type == Framing.ZRINIT
            val uris = pick(sending)
            val uploads = if (sending) uris.map { prepareUpload(it) } else emptyList()

            if (!sending) destination = uris.single()

            val engine = Session(
                output = { data -> writeProtocol(data) },
                receive = { metadata ->
                    mutableState.value = state.value.copy(name = metadata.name, bytes = 0, total = metadata.size)

                    val name = metadata.name.substringAfterLast('/').substringAfterLast('\\')
                        .filter { it.code >= 32 && it.code != 127 }.take(240)

                    require(name.isNotBlank() && name != "." && name != "..") { "Unsafe remote filename" }

                    val tree = checkNotNull(destination)
                    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                    val uri = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
                        ?: throw IOException("Cannot create download")

                    incomplete = uri

                    val stream = resolver.openOutputStream(uri, "w") ?: throw IOException("Cannot open download")

                    object : Download {
                        override suspend fun write(bytes: ByteArray) {
                            stream.write(bytes)
                        }

                        override suspend fun complete() {
                            stream.close()
                            incomplete = null
                        }

                        override suspend fun abort() {
                            stream.close()
                            DocumentsContract.deleteDocument(resolver, uri)
                            incomplete = null
                        }
                    }
                },
                progress = { mutableState.value = state.value.copy(name = it.name, bytes = it.bytes, total = it.total) },
            )

            session = engine
            engine.start(request, uploads)
            lastInput = SystemClock.elapsedRealtime()

            timer = scope.launch(dispatchers.io) {
                while (active) {
                    delay(1000)

                    if (!enabled) {
                        cancel()
                        break
                    }

                    if (SystemClock.elapsedRealtime() - lastInput >= 10_000) {
                        mutex.withLock {
                            try {
                                session?.timeout()
                                lastInput = SystemClock.elapsedRealtime()
                                if (session?.done == true) {
                                    suppressDetectionUntil = SystemClock.elapsedRealtime() + 1000
                                    cleanup(session?.failure)
                                }
                            } catch (_: TransferCancelled) {
                                abortTransfer(null)
                            } catch (e: TransportWriteFailure) {
                                cleanup(e.message)
                                if (bridge.transport === transport) bridge.dispatchDisconnect(DisconnectReason.IO_ERROR)
                            }
                        }
                    }
                }
            }

            detection.terminal + engine.accept(detection.remainder)
        } catch (_: TransferCancelled) {
            abortTransfer(null)
            byteArrayOf()
        } catch (_: TimeoutCancellationException) {
            abortTransfer("Transfer timed out")
            byteArrayOf()
        } catch (e: CancellationException) {
            runCatching { session?.cancel("Cancelled", notify = false) }
            cleanup(null)
            throw e
        } catch (e: TransportWriteFailure) {
            runCatching { session?.cancel("Transport failed", notify = false) }
            cleanup(e.message)
            if (bridge.transport === transport) bridge.dispatchDisconnect(DisconnectReason.IO_ERROR)
            byteArrayOf()
        } catch (e: Exception) {
            Timber.w(e, "Z-Modem transfer failed")
            abortTransfer(e.message ?: "Transfer failed")
            byteArrayOf()
        }
    }

    /** Bounded writes let a cancel request interrupt a large window between transport operations. */
    private suspend fun writeProtocol(data: ByteArray, abort: Boolean = false) {
        val target = source

        if (target == null || bridge.transport !== target) throw TransportWriteFailure(IOException("Transport replaced"))

        for (offset in data.indices step 4096) {
            if (bridge.transport !== target) throw TransportWriteFailure(IOException("Transport replaced"))
            if (cancellationRequested && !abort) throw TransferCancelled()

            try {
                withTimeout(10_000) {
                    bridge.writeTransfer(data.copyOfRange(offset, minOf(offset + 4096, data.size)), abort, target)
                }
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                throw TransportWriteFailure(e)
            } catch (e: IOException) {
                throw TransportWriteFailure(e)
            }
        }
    }

    /** The peer may leave residual output; preserving the shell is preferable to disconnecting. */
    private suspend fun abortTransfer(error: String?) {
        val target = source

        runCatching { session?.cancel(error ?: "Cancelled", notify = false) }
            .onFailure { Timber.w(it, "Could not close incomplete Z-Modem download") }

        var transportFailed = false

        try {
            writeProtocol(Framing.cancelSequence(), abort = true)
        } catch (e: TransportWriteFailure) {
            Timber.w(e, "Could not send Z-Modem cancellation")
            transportFailed = true
        } finally {
            // Do not reopen a picker for a queued initialization header from the cancelled peer.
            suppressDetectionUntil = SystemClock.elapsedRealtime() + 1000
            cleanup(error)
        }

        if (transportFailed && bridge.transport === target) bridge.dispatchDisconnect(DisconnectReason.IO_ERROR)
    }

    private suspend fun prepareUpload(uri: Uri): Upload {
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "upload"

        val file = File.createTempFile("zmodem-", ".upload", bridge.manager.cacheDir)

        temporary.add(file)

        resolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                val bytes = ByteArray(64 * 1024)

                while (true) {
                    if (cancellationRequested) throw TransferCancelled()

                    val count = input.read(bytes)

                    if (count < 0) break

                    output.write(bytes, 0, count)
                }
            }
        }
            ?: throw IOException("Cannot read upload")

        require(file.length() <= 0xffffffffL) { "File exceeds classic Z-Modem limit" }

        return object : Upload {
            override val name = name.substringAfterLast('/').substringAfterLast('\\')
            override val size = file.length()
            override suspend fun read(offset: Long, length: Int): ByteArray = RandomAccessFile(file, "r").use {
                it.seek(offset)
                ByteArray(length).also(it::readFully)
            }
        }
    }

    fun dismissError() {
        mutableState.value = state.value.copy(error = null)
    }

    fun cancel() {
        if (!active || cancellationRequested) return

        cancellationRequested = true
        mutableState.value = state.value.copy(cancelling = true)

        // Wake file selection without cancelling the relay coroutine or its transport.
        selection?.complete(emptyList())

        scope.launch(dispatchers.io) {
            mutex.withLock { if (active && cancellationRequested) abortTransfer(null) }
        }
    }

    fun disconnected() {
        selection?.cancel()
        timer?.cancel()

        scope.launch(dispatchers.io) {
            mutex.withLock {
                runCatching { session?.cancel("Disconnected", notify = false) }
                cleanup(state.value.error)
            }
        }
    }

    suspend fun finish(transport: AbsTransport): ByteArray = mutex.withLock {
        if (bridge.transport !== transport) return@withLock byteArrayOf()

        if (active) {
            runCatching { session?.cancel("Remote EOF", notify = false) }
            cleanup("Remote EOF")
            throw IOException("Remote EOF during Z-Modem transfer")
        }

        detector.finish()
    }

    private fun cleanup(error: String?) {
        bridge.manager.prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        timer?.cancel()
        timer = null
        incomplete?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        incomplete = null
        temporary.forEach { file ->
            if (!file.delete()) Timber.w("Could not delete temporary Z-Modem upload %s", file)
        }
        temporary.clear()
        destination = null
        source = null
        session = null
        cancellationRequested = false
        mutableState.value = State(error = error)
    }
}
