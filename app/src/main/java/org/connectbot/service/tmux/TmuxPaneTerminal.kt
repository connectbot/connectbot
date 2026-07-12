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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.service.StickyModifierSetting
import org.connectbot.service.TerminalEmulatorKeyDispatcher
import org.connectbot.service.TerminalKeyListener
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import org.connectbot.util.commandOutputSnippet
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

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

    override fun hashCode(): Int = 31 * (31 * defaultForeground.hashCode() + defaultBackground.hashCode()) +
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
interface TmuxPaneEmulatorHandle : AutoCloseable {
    /** The real termlib emulator for the Terminal composable; null in fakes. */
    val terminalEmulator: TerminalEmulator?

    /** Snapshot-published alternate-screen state. */
    val isAltScreenActive: Boolean

    fun writeInput(bytes: ByteArray)

    fun resize(rows: Int, cols: Int)

    override fun close()
}

/** Creates the emulator handle behind a pane. */
fun interface TmuxPaneEmulatorFactory {
    fun create(
        initialRows: Int,
        initialCols: Int,
        colors: TmuxPaneColors,
        minUpdateIntervalMs: Long,
        maxScrollbackLines: Int,
        onKeyboardInput: (ByteArray) -> Unit,
        onBell: () -> Unit,
        onCommandFinished: (durationMs: Long) -> Unit,
    ): TmuxPaneEmulatorHandle

    companion object {
        /** The real termlib emulator (requires Android). */
        val REAL = TmuxPaneEmulatorFactory { rows, cols, colors, minUpdateIntervalMs, maxScrollbackLines, onKeyboardInput, onBell, onCommandFinished ->
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
                onCommandFinished = onCommandFinished,
                minUpdateIntervalMs = minUpdateIntervalMs,
                maxScrollbackLines = maxScrollbackLines,
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
                override val isAltScreenActive: Boolean
                    get() = emulator.isAltScreenActive()

                override fun writeInput(bytes: ByteArray) {
                    emulator.writeInput(bytes)
                }

                override fun resize(rows: Int, cols: Int) {
                    emulator.resize(rows, cols)
                }

                override fun close() {
                    emulator.close()
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
    minUpdateIntervalMs: Long = 0L,
    private val scope: CoroutineScope,
    /** Sends one command on this session's control client. */
    private val sendCommand: suspend (String) -> TmuxReply,
    private val onBell: (sessionId: String, paneId: String) -> Unit = { _, _ -> },
    private val onCommandCompletion: (
        sessionId: String,
        paneId: String,
        durationMs: Long,
        snippet: String?,
    ) -> Unit = { _, _, _, _ -> },
    emulatorFactory: TmuxPaneEmulatorFactory = TmuxPaneEmulatorFactory.REAL,
) {
    private val handle: TmuxPaneEmulatorHandle = emulatorFactory.create(
        initialRows = initialRows.coerceAtLeast(1),
        initialCols = initialCols.coerceAtLeast(1),
        colors = colors,
        minUpdateIntervalMs = minUpdateIntervalMs,
        maxScrollbackLines = MAX_BACKFILL_LINES,
        onKeyboardInput = { data -> keyboardBytes.trySend(data) },
        onBell = { onBell(sessionId, paneId) },
        onCommandFinished = { durationMs ->
            // Snippet captured now, while this pane's emulator is
            // guaranteed alive (LRU eviction could destroy it later).
            onCommandCompletion(sessionId, paneId, durationMs, commandOutputSnippet(handle.terminalEmulator))
        },
    )

    /** The termlib emulator for the Terminal composable (real factory only). */
    val emulator: TerminalEmulator?
        get() = handle.terminalEmulator

    /** Whether this pane is currently rendering a full-screen alternate buffer. */
    val isAltScreenActive: Boolean
        get() = handle.isAltScreenActive

    /** Sticky-modifier behavior for [keyHandler]; the bridge sets the pref value. */
    @Volatile
    var stickyModifierSetting: StickyModifierSetting = StickyModifierSetting.NONE

    /**
     * Modifier/key handling bound to THIS pane's emulator, so hardware keys
     * and on-screen modifier buttons land in the pane, not the host shell.
     */
    val keyHandler: TerminalKeyListener by lazy {
        TerminalKeyListener(
            TerminalEmulatorKeyDispatcher(requireNotNull(emulator) { "no emulator for $paneId" }),
            stickyModifierSetting,
        )
    }

    private val keyboardBytes = Channel<ByteArray>(Channel.UNLIMITED)
    private val inputJob: Job

    private val outputLock = Any()
    private var live = false
    private var backfillSeq = Long.MAX_VALUE
    private val pendingOutput = ArrayDeque<TmuxNotification.Output>()

    private val destroyed = AtomicBoolean(false)
    private val emulatorLifecycleLock = Any()

    /** Current backfill depth; grows via [loadEarlier]. */
    @Volatile
    private var backfillDepth = DEFAULT_BACKFILL_LINES

    init {
        inputJob = scope.launch { inputLoop() }
    }

    /**
     * Loads pane history into the emulator, positions the cursor, then goes
     * live. Must be called once, before the pane is shown.
     */
    suspend fun backfill(scrollbackLines: Int = DEFAULT_BACKFILL_LINES) {
        val primaryCapture = runCatching {
            sendCommand("capture-pane -e -p -t $paneId -S -$scrollbackLines")
        }.getOrNull()
        var latestCaptureSeq = 0L

        if (primaryCapture != null && primaryCapture.ok) {
            latestCaptureSeq = primaryCapture.seq
            writeCapture(primaryCapture)
        } else {
            Timber.d("tmux capture-pane backfill failed for %s", paneId)
        }

        val screenState = readScreenState()
        if (screenState?.altScreenActive == true) {
            val alternateCapture = runCatching {
                sendCommand("capture-pane -a -e -p -t $paneId")
            }.getOrNull()
            if (alternateCapture != null && alternateCapture.ok) {
                latestCaptureSeq = maxOf(latestCaptureSeq, alternateCapture.seq)
                screenState.primaryCursor?.let(::positionCursor)
                handle.writeInput(ENTER_ALT_SCREEN_SEQUENCE)
                writeCapture(alternateCapture)
                positionCursor(screenState.cursor)
            } else {
                Timber.d("tmux alternate-screen capture failed for %s", paneId)
            }
        } else {
            screenState?.cursor?.let(::positionCursor)
        }

        synchronized(outputLock) { backfillSeq = latestCaptureSeq }

        val queued: List<TmuxNotification.Output>
        synchronized(outputLock) {
            queued = pendingOutput.filter { it.seq > backfillSeq }
            pendingOutput.clear()
            live = true
        }
        queued.forEach { handle.writeInput(it.bytes) }
    }

    private fun writeCapture(capture: TmuxReply) {
        val text = capture.lines.joinToString("\r\n")
        if (text.isNotEmpty()) {
            handle.writeInput(text.toByteArray(Charsets.UTF_8))
        }
    }

    private suspend fun readScreenState(): TmuxPaneScreenState? {
        val reply = runCatching {
            sendCommand(
                "display-message -p -t $paneId " +
                    "'#{alternate_on}\t#{cursor_x}\t#{cursor_y}\t#{alternate_saved_x}\t#{alternate_saved_y}'",
            )
        }.getOrNull() ?: return null
        if (!reply.ok) return null
        val parts = reply.lines.firstOrNull()?.split('\t') ?: return null
        val cursor = TmuxCursor(
            x = parts.getOrNull(1)?.toIntOrNull() ?: return null,
            y = parts.getOrNull(2)?.toIntOrNull() ?: return null,
        )
        val primaryCursor = parts.getOrNull(3)?.toIntOrNull()?.let { x ->
            parts.getOrNull(4)?.toIntOrNull()?.let { y -> TmuxCursor(x, y) }
        }
        return TmuxPaneScreenState(
            altScreenActive = parts.getOrNull(0) == "1",
            cursor = cursor,
            primaryCursor = primaryCursor,
        )
    }

    private fun positionCursor(cursor: TmuxCursor) {
        handle.writeInput("\u001b[${cursor.y + 1};${cursor.x + 1}H".toByteArray())
    }

    /**
     * Recovers from a tmux flow-control pause: the stream has a gap, so
     * reset the emulator (RIS) and reload the pane from a fresh capture.
     */
    suspend fun resync() {
        rebuildFromCapture(backfillDepth)
    }

    /**
     * Deepens the local scrollback: termlib cannot prepend history, so the
     * emulator is reset and re-fed a deeper capture (rebuild-and-swap).
     * @return false while the alternate screen is active or when the depth cap was reached
     */
    suspend fun loadEarlier(additionalLines: Int = DEFAULT_BACKFILL_LINES): Boolean {
        if (isAltScreenActive) return false
        val newDepth = (backfillDepth + additionalLines).coerceAtMost(MAX_BACKFILL_LINES)
        if (newDepth == backfillDepth || destroyed.get()) return false
        backfillDepth = newDepth
        rebuildFromCapture(newDepth)
        return true
    }

    private suspend fun rebuildFromCapture(depth: Int) {
        if (destroyed.get()) return
        synchronized(outputLock) {
            live = false
            backfillSeq = Long.MAX_VALUE
            pendingOutput.clear()
        }
        handle.writeInput(RESET_SEQUENCE)
        backfill(depth)
    }

    /** Routes one `%output` event (any thread). */
    fun handleOutput(output: TmuxNotification.Output) {
        if (destroyed.get()) return
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
        synchronized(emulatorLifecycleLock) {
            if (destroyed.get()) return
            handle.resize(rows.coerceAtLeast(1), cols.coerceAtLeast(1))
        }
    }

    /** Sends pasted text through a tmux buffer (bracketed paste aware). */
    fun paste(text: String) {
        if (destroyed.get() || text.isEmpty()) return
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

    /** Unhooks everything and releases the native terminal. */
    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        inputJob.cancel()
        keyboardBytes.close()
        synchronized(outputLock) { pendingOutput.clear() }
        synchronized(emulatorLifecycleLock) {
            handle.close()
        }
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
        const val MAX_BACKFILL_LINES = 10_000

        /** RIS — full terminal reset before re-feeding captured content. */
        private val RESET_SEQUENCE = "\u001bc".toByteArray()
        private val ENTER_ALT_SCREEN_SEQUENCE = "\u001b[?1049h".toByteArray()
        private const val MAX_PENDING_OUTPUT = 512
        private const val INPUT_COALESCE_MS = 8L
        private const val INPUT_COALESCE_BYTES = 128
    }
}

private data class TmuxCursor(val x: Int, val y: Int)

private data class TmuxPaneScreenState(
    val altScreenActive: Boolean,
    val cursor: TmuxCursor,
    val primaryCursor: TmuxCursor?,
)
