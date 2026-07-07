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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.connectbot.transport.ExecChannel
import org.junit.After
import org.junit.Test

/**
 * A scripted fake tmux server: command lines written to [stdin] trigger the
 * next scripted reply on [stdout]; notifications can be injected any time.
 */
private class FakeTmuxChannel : ExecChannel {
    private val serverOut = PipedOutputStream()
    override val stdout: InputStream = PipedInputStream(serverOut, 64 * 1024)
    override val stderr: InputStream = ByteArrayInputStream(ByteArray(0))

    private val commandLog = mutableListOf<String>()
    private val scriptedReplies = ArrayDeque<String>()
    private var nextReplyNumber = 100
    @Volatile private var closed = false

    /** When false, commands with no scripted reply hang (no default reply). */
    @Volatile var autoReply = true

    override val stdin: OutputStream = object : OutputStream() {
        private val lineBuffer = ByteArrayOutputStream()

        override fun write(b: Int) {
            if (b == '\n'.code) {
                onCommand(lineBuffer.toString(Charsets.UTF_8.name()))
                lineBuffer.reset()
            } else {
                lineBuffer.write(b)
            }
        }
    }

    init {
        // The unsolicited empty reply tmux emits on attach.
        sendRaw("%begin 1700000000 99 0\n%end 1700000000 99 0\n")
    }

    fun scriptReply(body: String, ok: Boolean = true) {
        val n = nextReplyNumber++
        val terminator = if (ok) "%end" else "%error"
        scriptedReplies.addLast("%begin 1700000000 $n 1\n$body$terminator 1700000000 $n 1\n")
    }

    fun sendNotification(line: String) = sendRaw("$line\n")

    fun commands(): List<String> = synchronized(commandLog) { commandLog.toList() }

    private fun onCommand(command: String) {
        synchronized(commandLog) { commandLog.add(command) }
        val scripted = scriptedReplies.removeFirstOrNull()
        val reply = when {
            scripted != null -> scripted
            autoReply -> "%begin 1700000000 ${nextReplyNumber++} 1\n%end 1700000000 ${nextReplyNumber - 1} 1\n"
            else -> return
        }
        sendRaw(reply)
    }

    private fun sendRaw(text: String) {
        if (closed) return
        serverOut.write(text.toByteArray(Charsets.ISO_8859_1))
        serverOut.flush()
    }

    override fun exitStatus(): Int? = if (closed) 0 else null

    override fun close() {
        closed = true
        // Close only the write end: the reader drains buffered lines (e.g. a
        // final %exit) and then sees EOF, like a real channel teardown.
        serverOut.close()
    }
}

class TmuxControlClientTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = FakeTmuxChannel()

    @After
    fun tearDown() {
        channel.close()
        scope.cancel()
    }

    private fun client() = TmuxControlClient(channel, scope)

    @Test
    fun `command receives its scripted reply`() = runBlocking<Unit> {
        val client = client()
        channel.scriptReply("@0\tmain\t1\n@1\tlogs\t0\n")

        val reply = withTimeout(5_000) { client.command("list-windows") }

        assertThat(reply.ok).isTrue()
        assertThat(reply.lines).containsExactly("@0\tmain\t1", "@1\tlogs\t0")
        assertThat(channel.commands()).containsExactly("list-windows")
    }

    @Test
    fun `error replies are delivered with ok false`() = runBlocking<Unit> {
        val client = client()
        channel.scriptReply("parse error: unknown command: nope\n", ok = false)

        val reply = withTimeout(5_000) { client.command("nope") }

        assertThat(reply.ok).isFalse()
        assertThat(reply.text).isEqualTo("parse error: unknown command: nope")
    }

    @Test
    fun `replies correlate in fifo order across concurrent commands`() = runBlocking<Unit> {
        val client = client()
        channel.scriptReply("first\n")
        channel.scriptReply("second\n")

        val a = async { client.command("cmd-a") }
        val b = async { a.join(); client.command("cmd-b") }

        withTimeout(5_000) {
            assertThat(a.await().text).isEqualTo("first")
            assertThat(b.await().text).isEqualTo("second")
        }
    }

    @Test
    fun `notifications are delivered to collectors`() = runBlocking<Unit> {
        val client = client()
        val collected = mutableListOf<TmuxNotification>()
        val collector = scope.launch {
            client.notifications.collect {
                collected.add(it)
                if (it is TmuxNotification.WindowAdd) cancel()
            }
        }

        channel.sendNotification("%session-changed \$0 main")
        channel.sendNotification("%output %1 hi\\015\\012")
        channel.sendNotification("%window-add @2")
        withTimeout(5_000) { collector.join() }

        assertThat(collected).containsExactly(
            TmuxNotification.SessionChanged("\$0", "main"),
            TmuxNotification.Output("%1", "hi\r\n".toByteArray()),
            TmuxNotification.WindowAdd("@2"),
        )
    }

    @Test
    fun `channel close fails pending commands and completes closed`() = runBlocking<Unit> {
        val client = client()
        channel.autoReply = false
        // No scripted reply: command will hang until the channel dies.
        val hanging = async {
            runCatching { client.command("will-never-complete") }
        }
        // Wait until the command reached the fake server.
        withTimeout(5_000) {
            while (channel.commands().isEmpty()) kotlinx.coroutines.delay(10)
        }

        channel.close()

        val result = withTimeout(5_000) { hanging.await() }
        assertThat(result.exceptionOrNull()).isInstanceOf(TmuxChannelClosedException::class.java)
        assertThat(withTimeout(5_000) { client.closed.await() }).isNull()
    }

    @Test
    fun `exit reason is reported through closed`() = runBlocking<Unit> {
        val client = client()

        channel.sendNotification("%exit detached")
        channel.close()

        assertThat(withTimeout(5_000) { client.closed.await() }).isEqualTo("detached")
    }

    @Test
    fun `commands after close throw immediately`() = runBlocking<Unit> {
        val client = client()
        channel.close()
        withTimeout(5_000) { client.closed.await() }

        assertThatExceptionOfType(TmuxChannelClosedException::class.java)
            .isThrownBy { runBlocking { client.command("late") } }
    }

    @Test
    fun `multiline commands are rejected`() = runBlocking<Unit> {
        val client = client()
        assertThatExceptionOfType(IllegalArgumentException::class.java)
            .isThrownBy { runBlocking { client.command("evil\ninjection") } }
    }
}
