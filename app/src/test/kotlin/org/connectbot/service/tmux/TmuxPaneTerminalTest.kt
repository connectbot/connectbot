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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.terminal.TerminalEmulator
import org.junit.After
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Records writes/resizes; termlib itself is JNI-only and untestable here. */
internal class FakeEmulator : TmuxPaneEmulatorHandle {
    val writes = mutableListOf<ByteArray>()
    var rows = 0
    var cols = 0
    var keyboardInput: ((ByteArray) -> Unit)? = null
    var bell: (() -> Unit)? = null
    var commandFinished: ((Long) -> Unit)? = null
    var minUpdateIntervalMs = 0L
    var maxScrollbackLines = 0
    var closeCount = 0
    var beforeWrite: ((ByteArray) -> Unit)? = null

    override val terminalEmulator: TerminalEmulator? = null
    override var isAltScreenActive: Boolean = false

    val text: String
        get() = synchronized(writes) {
            writes.joinToString("") { it.toString(Charsets.UTF_8) }
        }

    override fun writeInput(bytes: ByteArray) {
        beforeWrite?.invoke(bytes)
        val text = bytes.toString(Charsets.UTF_8)
        if ("\u001bc" in text || "\u001b[?1049l" in text) isAltScreenActive = false
        if ("\u001b[?1049h" in text) isAltScreenActive = true
        synchronized(writes) { writes.add(bytes.copyOf()) }
    }

    override fun resize(rows: Int, cols: Int) {
        this.rows = rows
        this.cols = cols
    }

    override fun close() {
        closeCount++
    }
}

class TmuxPaneTerminalTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fakeEmulator = FakeEmulator()
    private val commandLog = mutableListOf<String>()

    /** command prefix → (reply lines, seq) */
    private val replies = mutableMapOf<String, TmuxReply>()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun terminal(
        initialRows: Int = 24,
        initialCols: Int = 80,
        minUpdateIntervalMs: Long = 0L,
        onBell: (String, String) -> Unit = { _, _ -> },
        onCommandCompletion: (String, String, Long, String?) -> Unit = { _, _, _, _ -> },
    ): TmuxPaneTerminal = TmuxPaneTerminal(
        sessionId = "\$0",
        paneId = "%1",
        initialRows = initialRows,
        initialCols = initialCols,
        colors = TmuxPaneColors.DEFAULT,
        minUpdateIntervalMs = minUpdateIntervalMs,
        scope = scope,
        sendCommand = { command ->
            synchronized(commandLog) { commandLog.add(command) }
            replies.entries
                .filter { command.startsWith(it.key) }
                .maxByOrNull { it.key.length }
                ?.value
                ?: TmuxReply(0, true, emptyList())
        },
        onBell = onBell,
        onCommandCompletion = onCommandCompletion,
        emulatorFactory = { rows, cols, _, intervalMs, scrollbackLines, onKeyboardInput, onBellCallback, onCommandFinished ->
            fakeEmulator.also {
                it.rows = rows
                it.cols = cols
                it.minUpdateIntervalMs = intervalMs
                it.maxScrollbackLines = scrollbackLines
                it.keyboardInput = onKeyboardInput
                it.bell = onBellCallback
                it.commandFinished = onCommandFinished
            }
        },
    )

    private fun output(seq: Long, text: String) = TmuxNotification.Output("%1", text.toByteArray(), seq)

    @Test
    fun `backfill writes capture then cursor then later output only`() = runBlocking<Unit> {
        replies["capture-pane"] = TmuxReply(1, true, listOf("history line", "prompt \$"), seq = 10)
        replies["display-message"] = TmuxReply(2, true, listOf("0\t8\t1\t\t"), seq = 11)

        val terminal = terminal()
        // Events racing ahead of the backfill: seq 9 is inside the capture,
        // seq 12 arrived after it.
        terminal.handleOutput(output(9, "already-captured"))
        terminal.handleOutput(output(12, "new-bytes"))

        terminal.backfill()

        val text = fakeEmulator.text
        assertThat(text).contains("history line\r\nprompt \$")
        assertThat(text).contains("[2;9H") // cursor y=1,x=8 → row 2, col 9
        assertThat(text).doesNotContain("already-captured")
        assertThat(text).endsWith("new-bytes")
    }

    @Test
    fun `alternate-screen backfill restores primary and alternate buffers`() = runBlocking<Unit> {
        replies["capture-pane"] = TmuxReply(1, true, listOf("shell history", "prompt \$"), seq = 10)
        replies["capture-pane -a"] = TmuxReply(2, true, listOf("vim buffer", "~"), seq = 12)
        replies["display-message"] = TmuxReply(3, true, listOf("1\t4\t2\t7\t3"), seq = 11)

        val terminal = terminal()
        terminal.handleOutput(output(11, "already-in-alt-capture"))
        terminal.handleOutput(output(13, "new-alt-output"))

        terminal.backfill()

        val text = fakeEmulator.text
        assertThat(text).contains("shell history\r\nprompt \$")
        assertThat(text).contains("\u001b[4;8H\u001b[?1049hvim buffer\r\n~\u001b[3;5H")
        assertThat(text).doesNotContain("already-in-alt-capture")
        assertThat(text).endsWith("new-alt-output")
        assertThat(terminal.isAltScreenActive).isTrue()
        assertThat(commandLog).anyMatch { it.startsWith("capture-pane -a") }
    }

    @Test
    fun `failed alternate-screen capture restores primary cursor`() = runBlocking<Unit> {
        replies["capture-pane"] = TmuxReply(1, true, listOf("primary"), seq = 10)
        replies["capture-pane -a"] = TmuxReply(2, false, listOf("capture failed"), seq = 12)
        replies["display-message"] = TmuxReply(3, true, listOf("1\t4\t2\t7\t3"), seq = 11)

        val terminal = terminal()

        terminal.backfill()

        assertThat(fakeEmulator.text).isEqualTo("primary\u001b[4;8H")
        assertThat(terminal.isAltScreenActive).isFalse()
    }

    @Test
    fun `load earlier is blocked on alternate screen`() = runBlocking<Unit> {
        fakeEmulator.isAltScreenActive = true
        val terminal = terminal()

        assertThat(terminal.loadEarlier()).isFalse()
        assertThat(commandLog).isEmpty()
    }

    @Test
    fun `load earlier progressively requests history within retained limit`() = runBlocking<Unit> {
        val terminal = terminal()

        assertThat(terminal.loadEarlier()).isTrue()
        assertThat(terminal.loadEarlier()).isTrue()

        assertThat(fakeEmulator.maxScrollbackLines).isEqualTo(TmuxPaneTerminal.MAX_BACKFILL_LINES)
        assertThat(commandLog).anyMatch { it.contains("-S -4000") }
        assertThat(commandLog).anyMatch { it.contains("-S -6000") }
    }

    @Test
    fun `resync resets and rebuilds alternate screen`() = runBlocking<Unit> {
        replies["capture-pane"] = TmuxReply(1, true, listOf("primary"), seq = 10)
        replies["capture-pane -a"] = TmuxReply(2, true, listOf("alternate"), seq = 12)
        replies["display-message"] = TmuxReply(3, true, listOf("1\t0\t0\t0\t0"), seq = 11)
        val terminal = terminal()
        terminal.backfill()
        val writesBeforeResync = fakeEmulator.writes.size

        terminal.resync()

        val resyncText = fakeEmulator.writes.drop(writesBeforeResync)
            .joinToString("") { it.toString(Charsets.UTF_8) }
        assertThat(resyncText).startsWith("\u001bcprimary")
        assertThat(resyncText).contains("\u001b[?1049halternate")
        assertThat(terminal.isAltScreenActive).isTrue()
    }

    @Test
    fun `live output flows straight through and stale seq is dropped`() = runBlocking<Unit> {
        replies["capture-pane"] = TmuxReply(1, true, emptyList(), seq = 5)
        val terminal = terminal()
        terminal.backfill()

        terminal.handleOutput(output(6, "after"))
        terminal.handleOutput(output(4, "before"))

        assertThat(fakeEmulator.text).contains("after")
        assertThat(fakeEmulator.text).doesNotContain("before")
    }

    @Test
    fun `keyboard bytes become hex send-keys commands`() = runBlocking<Unit> {
        replies["capture-pane"] = TmuxReply(1, true, emptyList(), seq = 1)
        val terminal = terminal()
        terminal.backfill()

        fakeEmulator.keyboardInput!!.invoke("ls\r".toByteArray())

        withTimeout(5_000) {
            while (synchronized(commandLog) { commandLog.none { it.startsWith("send-keys") } }) {
                delay(10)
            }
        }
        assertThat(synchronized(commandLog) { commandLog.last() })
            .isEqualTo("send-keys -t %1 -H 6c 73 0d")
    }

    @Test
    fun `paste goes through tmux buffer`() = runBlocking<Unit> {
        val terminal = terminal()
        terminal.paste("hello\nworld")

        withTimeout(5_000) {
            while (synchronized(commandLog) { commandLog.none { it.startsWith("paste-buffer") } }) {
                delay(10)
            }
        }
        val commands = synchronized(commandLog) { commandLog.toList() }
        assertThat(commands).contains(
            "set-buffer -b connectbot-paste -- \"hello\\012world\"",
            "paste-buffer -p -d -b connectbot-paste -t %1",
        )
    }

    @Test
    fun `bell is forwarded with pane identity`() {
        var belled: Pair<String, String>? = null
        terminal(onBell = { s, p -> belled = s to p })
        fakeEmulator.bell!!.invoke()
        assertThat(belled).isEqualTo("\$0" to "%1")
    }

    @Test
    fun `command completion is forwarded with pane identity`() {
        val completions = mutableListOf<Triple<String, String, Long>>()
        terminal(onCommandCompletion = { s, p, durationMs, _ -> completions.add(Triple(s, p, durationMs)) })

        fakeEmulator.commandFinished!!.invoke(45_000)

        assertThat(completions).containsExactly(Triple("\$0", "%1", 45_000L))
    }

    @Test
    fun `creation and resize normalize non-positive dimensions`() {
        val terminal = terminal(initialRows = 0, initialCols = -80, minUpdateIntervalMs = 250L)

        assertThat(fakeEmulator.rows).isEqualTo(1)
        assertThat(fakeEmulator.cols).isEqualTo(1)
        assertThat(fakeEmulator.minUpdateIntervalMs).isEqualTo(250L)
        assertThat(fakeEmulator.maxScrollbackLines).isEqualTo(TmuxPaneTerminal.MAX_BACKFILL_LINES)

        terminal.resize(rows = -1, cols = 0)

        assertThat(fakeEmulator.rows).isEqualTo(1)
        assertThat(fakeEmulator.cols).isEqualTo(1)
    }

    @Test
    fun `resize forwards to emulator and destroy stops everything`() = runBlocking<Unit> {
        replies["capture-pane"] = TmuxReply(1, true, emptyList(), seq = 1)
        val terminal = terminal()
        terminal.backfill()

        terminal.resize(rows = 50, cols = 120)
        assertThat(fakeEmulator.rows).isEqualTo(50)
        assertThat(fakeEmulator.cols).isEqualTo(120)

        terminal.destroy()
        terminal.destroy()
        val writesBefore = synchronized(fakeEmulator.writes) { fakeEmulator.writes.size }
        terminal.handleOutput(output(99, "ignored"))
        terminal.resize(10, 10)
        assertThat(synchronized(fakeEmulator.writes) { fakeEmulator.writes.size }).isEqualTo(writesBefore)
        assertThat(fakeEmulator.rows).isEqualTo(50)
        assertThat(fakeEmulator.closeCount).isEqualTo(1)
    }

    @Test
    fun `destroy waits for an in-flight emulator write`() = runBlocking<Unit> {
        replies["capture-pane"] = TmuxReply(1, true, emptyList(), seq = 1)
        val terminal = terminal()
        terminal.backfill()

        val writeStarted = CountDownLatch(1)
        val allowWriteToFinish = CountDownLatch(1)
        val destroyStarted = CountDownLatch(1)
        val destroyFinished = CountDownLatch(1)
        fakeEmulator.beforeWrite = { bytes ->
            if (bytes.toString(Charsets.UTF_8) == "racing-write") {
                writeStarted.countDown()
                allowWriteToFinish.await(5, TimeUnit.SECONDS)
            }
        }

        val writeThread = thread { terminal.handleOutput(output(2, "racing-write")) }
        assertThat(writeStarted.await(5, TimeUnit.SECONDS)).isTrue()

        val destroyThread = thread {
            destroyStarted.countDown()
            terminal.destroy()
            destroyFinished.countDown()
        }
        assertThat(destroyStarted.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(destroyFinished.await(100, TimeUnit.MILLISECONDS)).isFalse()

        allowWriteToFinish.countDown()
        writeThread.join(5_000)
        destroyThread.join(5_000)

        assertThat(writeThread.isAlive).isFalse()
        assertThat(destroyThread.isAlive).isFalse()
        assertThat(fakeEmulator.text).contains("racing-write")
        assertThat(fakeEmulator.closeCount).isEqualTo(1)
    }

    @Test
    fun `failed capture still goes live with everything new applied`() = runBlocking<Unit> {
        replies["capture-pane"] = TmuxReply(1, false, listOf("some error"), seq = 3)
        val terminal = terminal()
        terminal.handleOutput(output(2, "queued"))

        terminal.backfill()

        assertThat(fakeEmulator.text).contains("queued")
    }
}
