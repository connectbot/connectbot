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

package org.connectbot.service.tmux

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.connectbot.transport.ExecChannel
import timber.log.Timber

/**
 * A tmux control-mode client bound to one [ExecChannel] running
 * `tmux -C attach-session`.
 *
 * Commands are answered strictly in order, so replies are correlated with a
 * FIFO of pending [CompletableDeferred]s; the unsolicited empty reply tmux
 * emits on attach is absorbed by a pre-seeded sentinel. Notifications
 * (including `%output` pane bytes) are exposed on [notifications].
 *
 * When the channel ends — graceful `%exit`, remote kill, or transport loss —
 * all pending commands fail with [TmuxChannelClosedException] and [closed]
 * completes with the `%exit` reason (null when the channel just died).
 */
class TmuxControlClient(
    private val channel: ExecChannel,
    scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val parser = TmuxControlParser()

    private val pending = ArrayDeque<CompletableDeferred<TmuxReply>>()
    private val pendingLock = Any()

    private val writeMutex = Mutex()

    private val _notifications = MutableSharedFlow<TmuxNotification>(
        replay = NOTIFICATION_REPLAY,
        extraBufferCapacity = NOTIFICATION_BUFFER,
    )

    /**
     * All control-mode notifications in arrival order. The replay buffer
     * covers the gap between construction and the (single) collector
     * subscribing, so nothing sent right after attach is lost. Collectors
     * must keep up: the reader coroutine suspends when the buffer is full,
     * which backpressures the whole channel (that is the pre-3.2 flood
     * behavior).
     */
    val notifications: SharedFlow<TmuxNotification> = _notifications.asSharedFlow()

    private val _closed = CompletableDeferred<String?>()

    /** Completes when the channel ends, with the `%exit` reason if one was seen. */
    val closed: Deferred<String?> = _closed

    @Volatile
    private var exitReason: String? = null

    private val readerJob: Job

    init {
        // Absorbs the unsolicited %begin/%end pair tmux sends on attach.
        synchronized(pendingLock) { pending.addLast(CompletableDeferred()) }
        readerJob = scope.launch(ioDispatcher) { readLoop() }
    }

    /**
     * Sends one tmux command and suspends until its reply arrives.
     * @throws TmuxChannelClosedException if the channel is or becomes closed
     */
    suspend fun command(command: String): TmuxReply {
        require('\n' !in command && '\r' !in command) { "tmux commands must be single-line" }
        val deferred = CompletableDeferred<TmuxReply>()
        writeMutex.withLock {
            if (_closed.isCompleted) {
                throw TmuxChannelClosedException("tmux control channel is closed")
            }
            synchronized(pendingLock) { pending.addLast(deferred) }
            try {
                runInterruptible(ioDispatcher) {
                    channel.stdin.write((command + "\n").toByteArray(Charsets.UTF_8))
                    channel.stdin.flush()
                }
            } catch (e: IOException) {
                synchronized(pendingLock) { pending.remove(deferred) }
                throw TmuxChannelClosedException("tmux control channel write failed: ${e.message}")
            }
        }
        return deferred.await()
    }

    /** Closes the underlying channel; the reader then drains and completes [closed]. */
    fun close() {
        channel.close()
    }

    private suspend fun readLoop() {
        try {
            val reader = channel.stdout.bufferedReader(Charsets.ISO_8859_1)
            while (true) {
                val line = runInterruptible(ioDispatcher) { reader.readLine() } ?: break
                when (val event = parser.feed(line)) {
                    is TmuxParseEvent.Reply -> completeNextPending(event.value)
                    is TmuxParseEvent.Notification -> {
                        val notification = event.value
                        if (notification is TmuxNotification.Exit) {
                            exitReason = notification.reason ?: ""
                        }
                        _notifications.emit(notification)
                    }
                    TmuxParseEvent.Consumed -> Unit
                }
            }
        } catch (e: IOException) {
            Timber.d("tmux control channel read ended: %s", e.message)
        } finally {
            failAllPending()
            _closed.complete(exitReason?.ifEmpty { null })
        }
    }

    private fun completeNextPending(reply: TmuxReply) {
        val deferred = synchronized(pendingLock) { pending.removeFirstOrNull() }
        if (deferred == null) {
            Timber.w("tmux reply %d arrived with no pending command", reply.number)
            return
        }
        deferred.complete(reply)
    }

    private fun failAllPending() {
        val orphans = synchronized(pendingLock) {
            val copy = pending.toList()
            pending.clear()
            copy
        }
        orphans.forEach {
            it.completeExceptionally(TmuxChannelClosedException("tmux control channel closed"))
        }
    }

    companion object {
        private const val NOTIFICATION_REPLAY = 64
        private const val NOTIFICATION_BUFFER = 192
    }
}
