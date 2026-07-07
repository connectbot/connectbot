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

/** Records writes/resizes; termlib itself is JNI-only and untestable here. */
internal class FakeEmulator : TmuxPaneEmulatorHandle {
    val writes = mutableListOf<ByteArray>()
    var rows = 0
    var cols = 0
    var keyboardInput: ((ByteArray) -> Unit)? = null
    var bell: (() -> Unit)? = null
    var commandFinished: ((Long) -> Unit)? = null

    override val terminalEmulator: TerminalEmulator? = null

    val text: String
        get() = synchronized(writes) {
            writes.joinToString("") { it.toString(Charsets.UTF_8) }
        }

    override fun writeInput(bytes: ByteArray) {
        synchronized(writes) { writes.add(bytes.copyOf()) }
    }

    override fun resize(rows: Int, cols: Int) {
        this.rows = rows
        this.cols = cols
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
        onBell: (String, String) -> Unit = { _, _ -> },
        onCommandCompletion: (String, String, Long, String?) -> Unit = { _, _, _, _ -> },
    ): TmuxPaneTerminal =
        TmuxPaneTerminal(
            sessionId = "\$0",
            paneId = "%1",
            initialRows = 24,
            initialCols = 80,
            colors = TmuxPaneColors.DEFAULT,
            scope = scope,
            sendCommand = { command ->
                synchronized(commandLog) { commandLog.add(command) }
                replies.entries.firstOrNull { command.startsWith(it.key) }?.value
                    ?: TmuxReply(0, true, emptyList())
            },
            onBell = onBell,
            onCommandCompletion = onCommandCompletion,
            emulatorFactory = { rows, cols, _, onKeyboardInput, onBellCallback, onCommandFinished ->
                fakeEmulator.also {
                    it.rows = rows
                    it.cols = cols
                    it.keyboardInput = onKeyboardInput
                    it.bell = onBellCallback
                    it.commandFinished = onCommandFinished
                }
            },
        )

    private fun output(seq: Long, text: String) =
        TmuxNotification.Output("%1", text.toByteArray(), seq)

    @Test
    fun `backfill writes capture then cursor then later output only`() = runBlocking<Unit> {
        replies["capture-pane"] = TmuxReply(1, true, listOf("history line", "prompt \$"), seq = 10)
        replies["display-message"] = TmuxReply(2, true, listOf("8\t1"), seq = 11)

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
    fun `resize forwards to emulator and destroy stops everything`() = runBlocking<Unit> {
        replies["capture-pane"] = TmuxReply(1, true, emptyList(), seq = 1)
        val terminal = terminal()
        terminal.backfill()

        terminal.resize(rows = 50, cols = 120)
        assertThat(fakeEmulator.rows).isEqualTo(50)
        assertThat(fakeEmulator.cols).isEqualTo(120)

        terminal.destroy()
        val writesBefore = synchronized(fakeEmulator.writes) { fakeEmulator.writes.size }
        terminal.handleOutput(output(99, "ignored"))
        terminal.resize(10, 10)
        assertThat(synchronized(fakeEmulator.writes) { fakeEmulator.writes.size }).isEqualTo(writesBefore)
        assertThat(fakeEmulator.rows).isEqualTo(50)
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
