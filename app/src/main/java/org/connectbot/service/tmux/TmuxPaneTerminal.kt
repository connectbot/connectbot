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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import timber.log.Timber

/** Colors for pane emulators, taken from the host's profile. */
data class TmuxPaneColors(
    val defaultForeground: Color,
    val defaultBackground: Color,
    /** 16 ANSI palette entries, or null to keep termlib defaults. */
    val ansiColors: IntArray? = null,
) {
    override fun equals(other: Any?): Boolean = other is TmuxPaneColors &&
        other.defaultForeground == defaultForeground &&
        other.defaultBackground == defaultBackground &&
        (other.ansiColors?.contentEquals(ansiColors) ?: (ansiColors == null))

    override fun hashCode(): Int =
        31 * (31 * defaultForeground.hashCode() + defaultBackground.hashCode()) +
            (ansiColors?.contentHashCode() ?: 0)

    companion object {
        /** Classic light-gray-on-black, used until the bridge provides profile colors. */
        val DEFAULT = TmuxPaneColors(
            defaultForeground = Color(0xFFCCCCCC),
            defaultBackground = Color(0xFF000000),
        )
    }
}

/**
 * The subset of emulator operations a pane terminal needs. termlib's
 * [TerminalEmulator] is a sealed, JNI-backed interface that can neither be
 * faked nor run in local unit tests, so panes talk to it through this handle;
 * tests substitute their own recording implementation.
 */
interface TmuxPaneEmulatorHandle {
    /** The real termlib emulator for the Terminal composable; null in fakes. */
    val terminalEmulator: TerminalEmulator?

    fun writeInput(bytes: ByteArray)

    fun resize(rows: Int, cols: Int)
}

/** Creates the emulator handle behind a pane. */
fun interface TmuxPaneEmulatorFactory {
    fun create(
        initialRows: Int,
        initialCols: Int,
        colors: TmuxPaneColors,
        onKeyboardInput: (ByteArray) -> Unit,
        onBell: () -> Unit,
    ): TmuxPaneEmulatorHandle

    companion object {
        /** The real termlib emulator (requires Android). */
        val REAL = TmuxPaneEmulatorFactory { rows, cols, colors, onKeyboardInput, onBell ->
            val emulator = TerminalEmulatorFactory.create(
                initialRows = rows,
                initialCols = cols,
                defaultForeground = colors.defaultForeground,
                defaultBackground = colors.defaultBackground,
                onKeyboardInput = onKeyboardInput,
                onBell = onBell,
                onResize = { /* pane size is dictated by the server, not the view */ },
                onClipboardCopy = { /* OSC 52 handled at the console layer */ },
                onProgressChange = { _, _ -> },
            ).also { created ->
                colors.ansiColors?.let { ansi ->
                    created.applyColorScheme(
                        ansi,
                        colors.defaultForeground.toArgb(),
                        colors.defaultBackground.toArgb(),
                    )
                }
            }
            object : TmuxPaneEmulatorHandle {
                override val terminalEmulator: TerminalEmulator = emulator

                override fun writeInput(bytes: ByteArray) {
                    emulator.writeInput(bytes)
                }

                override fun resize(rows: Int, cols: Int) {
                    emulator.resize(rows, cols)
                }
            }
        }
    }
}

/**
 * One tmux pane rendered locally: a termlib [TerminalEmulator] fed by
 * `%output` bytes, with scrollback backfilled from `capture-pane` on
 * creation.
 *
 * Output ordering: `%output` events and command replies race between the
 * notification flow and reply deferreds, so [handleOutput] gates on the
 * stream [TmuxReply.seq] of the backfill capture — bytes at or before it are
 * already part of the captured content and are dropped; later bytes are
 * queued until the backfill is written, then applied in order.
 *
 * Keyboard bytes from the emulator are coalesced briefly and shipped as
 * `send-keys -H` commands.
 */
class TmuxPaneTerminal(
    val sessionId: String,
    val paneId: String,
    initialRows: Int,
    initialCols: Int,
    colors: TmuxPaneColors,
    private val scope: CoroutineScope,
    /** Sends one command on this session's control client. */
    private val sendCommand: suspend (String) -> TmuxReply,
    private val onBell: (sessionId: String, paneId: String) -> Unit = { _, _ -> },
    emulatorFactory: TmuxPaneEmulatorFactory = TmuxPaneEmulatorFactory.REAL,
) {
    private val handle: TmuxPaneEmulatorHandle = emulatorFactory.create(
        initialRows = initialRows,
        initialCols = initialCols,
        colors = colors,
        onKeyboardInput = { data -> keyboardBytes.trySend(data) },
        onBell = { onBell(sessionId, paneId) },
    )

    /** The termlib emulator for the Terminal composable (real factory only). */
    val emulator: TerminalEmulator?
        get() = handle.terminalEmulator

    private val keyboardBytes = Channel<ByteArray>(Channel.UNLIMITED)
    private val inputJob: Job

    private val outputLock = Any()
    private var live = false
    private var backfillSeq = Long.MAX_VALUE
    private val pendingOutput = ArrayDeque<TmuxNotification.Output>()

    @Volatile
    private var destroyed = false

    init {
        inputJob = scope.launch { inputLoop() }
    }

    /**
     * Loads pane history into the emulator, positions the cursor, then goes
     * live. Must be called once, before the pane is shown.
     */
    suspend fun backfill(scrollbackLines: Int = DEFAULT_BACKFILL_LINES) {
        val capture = runCatching {
            sendCommand("capture-pane -e -p -t $paneId -S -$scrollbackLines")
        }.getOrNull()

        if (capture != null && capture.ok) {
            synchronized(outputLock) { backfillSeq = capture.seq }
            val text = capture.lines.joinToString("\r\n")
            if (text.isNotEmpty()) {
                handle.writeInput(text.toByteArray(Charsets.UTF_8))
            }
        } else {
            // No capture: everything that streams in is new.
            synchronized(outputLock) { backfillSeq = 0 }
            Timber.d("tmux capture-pane backfill failed for %s", paneId)
        }

        positionCursor()

        val queued: List<TmuxNotification.Output>
        synchronized(outputLock) {
            queued = pendingOutput.filter { it.seq > backfillSeq }
            pendingOutput.clear()
            live = true
        }
        queued.forEach { handle.writeInput(it.bytes) }
    }

    private suspend fun positionCursor() {
        val cursor = runCatching {
            sendCommand("display-message -p -t $paneId '#{cursor_x}\t#{cursor_y}'")
        }.getOrNull() ?: return
        val parts = cursor.lines.firstOrNull()?.split('\t') ?: return
        val x = parts.getOrNull(0)?.toIntOrNull() ?: return
        val y = parts.getOrNull(1)?.toIntOrNull() ?: return
        handle.writeInput("\u001b[${y + 1};${x + 1}H".toByteArray())
    }

    /** Routes one `%output` event (any thread). */
    fun handleOutput(output: TmuxNotification.Output) {
        if (destroyed) return
        synchronized(outputLock) {
            if (!live) {
                pendingOutput.addLast(output)
                // Cap queue growth pre-backfill; the capture covers history.
                while (pendingOutput.size > MAX_PENDING_OUTPUT) pendingOutput.removeFirst()
                return
            }
            if (output.seq <= backfillSeq) return
        }
        handle.writeInput(output.bytes)
    }

    /** Applies a server-side pane resize (from `%layout-change`). */
    fun resize(rows: Int, cols: Int) {
        if (destroyed) return
        handle.resize(rows, cols)
    }

    /** Sends pasted text through a tmux buffer (bracketed paste aware). */
    fun paste(text: String) {
        if (destroyed || text.isEmpty()) return
        scope.launch {
            TmuxInputEncoder.toPasteCommands(paneId, text).forEach { command ->
                runCatching { sendCommand(command) }
                    .onFailure {
                        Timber.d(it, "tmux paste failed for %s", paneId)
                        return@launch
                    }
            }
        }
    }

    /** Unhooks everything. termlib has no dispose; the GC reclaims the peer. */
    fun destroy() {
        destroyed = true
        inputJob.cancel()
        keyboardBytes.close()
        synchronized(outputLock) { pendingOutput.clear() }
    }

    private suspend fun inputLoop() {
        for (first in keyboardBytes) {
            val batch = ByteArrayOutputStream(INPUT_COALESCE_BYTES)
            batch.write(first)
            // Coalesce briefly so autorepeat/IME bursts share a command.
            withTimeoutOrNull(INPUT_COALESCE_MS) {
                while (batch.size() < INPUT_COALESCE_BYTES) {
                    batch.write(keyboardBytes.receive())
                }
            }
            TmuxInputEncoder.toSendKeysCommands(paneId, batch.toByteArray()).forEach { command ->
                runCatching { sendCommand(command) }
                    .onFailure { Timber.d(it, "tmux send-keys failed for %s", paneId) }
            }
        }
    }

    companion object {
        const val DEFAULT_BACKFILL_LINES = 2000
        private const val MAX_PENDING_OUTPUT = 512
        private const val INPUT_COALESCE_MS = 8L
        private const val INPUT_COALESCE_BYTES = 128
    }
}
