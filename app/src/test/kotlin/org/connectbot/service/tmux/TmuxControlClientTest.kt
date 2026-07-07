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
