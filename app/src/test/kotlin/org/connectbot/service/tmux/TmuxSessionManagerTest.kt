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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.transport.ExecChannel
import org.junit.After
import org.junit.Test

/** One-shot channel: fixed stdout, then EOF. */
private class OneShotChannel(text: String) : ExecChannel {
    override val stdin: OutputStream = ByteArrayOutputStream()
    override val stdout: InputStream = ByteArrayInputStream(text.toByteArray(Charsets.ISO_8859_1))
    override val stderr: InputStream = ByteArrayInputStream(ByteArray(0))
    override fun exitStatus(): Int? = 0
    override fun close() = Unit
}

private class FakeChannelFactory : TmuxChannelFactory {
    val execLog = mutableListOf<String>()
    val controlChannels = mutableListOf<FakeTmuxChannel>()

    var probeResponse = "tmux 3.7b\n"
    var listResponse = "\$0\tmain\t0\n\$1\twork\t1\n"
    var snapshotResponse = "snapshot line 1\nsnapshot line 2\n"

    /** Scripts each new control channel before the client starts reading. */
    var onControlChannel: (FakeTmuxChannel) -> Unit = { channel ->
        channel.scriptReply("@0\t0\tshell\t1\t*\n@1\t1\tlogs\t0\t-\n")
        channel.scriptReply(
            "@0\t%0\t0\t100\t30\t0\t0\t1\n" +
                "@0\t%1\t1\t100\t29\t0\t31\t0\n" +
                "@1\t%2\t0\t100\t60\t0\t0\t1\n",
        )
    }

    override fun open(command: String): ExecChannel {
        synchronized(execLog) { execLog.add(command) }
        return when {
            command.startsWith("tmux -u -C attach-session") ->
                FakeTmuxChannel().also { onControlChannel(it); controlChannels.add(it) }

            command.startsWith("command -v tmux") -> OneShotChannel(probeResponse)
            command.startsWith("tmux ls") -> OneShotChannel(listResponse)
            command.startsWith("tmux capture-pane") -> OneShotChannel(snapshotResponse)
            else -> OneShotChannel("")
        }
    }
}

class TmuxSessionManagerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val factory = FakeChannelFactory()
    private val manager = TmuxSessionManager(factory, scope)

    @After
    fun tearDown() {
        manager.shutdown()
        scope.cancel()
    }

    private fun awaitState(predicate: (TmuxHostState) -> Boolean): TmuxHostState = runBlocking {
        withTimeout(5_000) { manager.state.first(predicate) }
    }

    private fun connectAndAwaitReady(): TmuxHostState {
        manager.onTransportConnected()
        return awaitState { it.availability == TmuxAvailability.READY }
    }

    @Test
    fun `probe without tmux disables integration`() {
        factory.probeResponse = "NO_TMUX\n"
        manager.onTransportConnected()
        val state = awaitState { it.availability == TmuxAvailability.UNAVAILABLE }
        assertThat(state.sessions).isEmpty()
    }

    @Test
    fun `probe with old tmux reports unsupported`() {
        factory.probeResponse = "tmux 1.8\n"
        manager.onTransportConnected()
        val state = awaitState { it.availability == TmuxAvailability.UNSUPPORTED_VERSION }
        assertThat(state.version?.raw).isEqualTo("tmux 1.8")
    }

    @Test
    fun `no server means ready with offer`() {
        factory.listResponse = "NO_SERVER\n"
        val state = connectAndAwaitReady()
        assertThat(state.sessions).isEmpty()
        assertThat(state.offerSession).isTrue()
    }

    @Test
    fun `discovery lists sessions and fetches snapshots`() {
        val state = connectAndAwaitReady()
        assertThat(state.sessions.map { it.name }).containsExactly("main", "work")
        assertThat(state.sessions.map { it.id }).containsExactly("\$0", "\$1")
        assertThat(state.offerSession).isFalse()

        val withSnapshots = awaitState { s -> s.sessions.all { it.snapshot != null } }
        assertThat(withSnapshots.sessions[0].snapshot).contains("snapshot line 1")
    }

    @Test
    fun `attach loads the window tree and marks attached`() = runBlocking<Unit> {
        connectAndAwaitReady()

        withTimeout(5_000) { manager.attach("\$0") }

        val state = manager.state.value
        val session = state.session("\$0")!!
        assertThat(session.attachState).isEqualTo(TmuxAttachState.ATTACHED)
        assertThat(session.snapshot).isNull()
        assertThat(session.windows.map { it.name }).containsExactly("shell", "logs")
        assertThat(session.activeWindowId).isEqualTo("@0")
        assertThat(session.windows[0].panes.map { it.id }).containsExactly("%0", "%1")
        assertThat(session.windows[0].activePaneId).isEqualTo("%0")
        assertThat(session.windows[1].panes.map { it.id }).containsExactly("%2")

        // Landed on the server-side active window/pane and mirrored it.
        assertThat(manager.currentTarget.value)
            .isEqualTo(TmuxTarget("\$0", "@0", "%0", "main"))
        val control = factory.controlChannels.single()
        withTimeout(5_000) {
            while (control.commands().none { it.startsWith("select-pane") }) {
                kotlinx.coroutines.delay(10)
            }
        }
        assertThat(control.commands().filter { it.startsWith("select-") })
            .containsExactly("select-window -t '@0'", "select-pane -t '%0'")
    }

    @Test
    fun `pane output is routed to the sink`() = runBlocking<Unit> {
        val received = mutableListOf<Triple<String, String, String>>()
        manager.paneOutputSink = { sessionId, paneId, bytes ->
            synchronized(received) {
                received.add(Triple(sessionId, paneId, bytes.toString(Charsets.UTF_8)))
            }
        }
        connectAndAwaitReady()
        withTimeout(5_000) { manager.attach("\$0") }

        factory.controlChannels.single().sendNotification("%output %0 hello\\015\\012")

        withTimeout(5_000) {
            while (synchronized(received) { received.isEmpty() }) kotlinx.coroutines.delay(10)
        }
        assertThat(received.single()).isEqualTo(Triple("\$0", "%0", "hello\r\n"))
    }

    @Test
    fun `notifications update attached session state`() = runBlocking<Unit> {
        connectAndAwaitReady()
        withTimeout(5_000) { manager.attach("\$0") }

        val control = factory.controlChannels.single()
        control.sendNotification("%window-renamed @1 build")
        awaitState { it.session("\$0")!!.windows[1].name == "build" }

        control.sendNotification("%session-window-changed \$0 @1")
        awaitState { it.session("\$0")!!.activeWindowId == "@1" }
    }

    @Test
    fun `detach keeps session listed and refetches snapshot`() = runBlocking<Unit> {
        connectAndAwaitReady()
        withTimeout(5_000) { manager.attach("\$0") }

        withTimeout(5_000) { manager.detach("\$0") }

        val state = awaitState {
            it.session("\$0")!!.attachState == TmuxAttachState.DETACHED &&
                it.session("\$0")!!.snapshot != null
        }
        assertThat(state.session("\$0")!!.snapshot).isNotEmpty
        assertThat(factory.controlChannels.single().commands()).contains("detach-client")
    }

    @Test
    fun `transport loss detaches everything and reconnect reattaches`() = runBlocking<Unit> {
        connectAndAwaitReady()
        withTimeout(5_000) { manager.attach("\$0") }
        assertThat(manager.currentTarget.value).isNotNull

        manager.onTransportLost()
        awaitState { state -> state.sessions.all { it.attachState == TmuxAttachState.DETACHED } }

        manager.onTransportConnected()
        val state = awaitState { it.session("\$0")?.attachState == TmuxAttachState.ATTACHED }
        assertThat(state.session("\$0")!!.windows).isNotEmpty
        assertThat(factory.controlChannels).hasSize(2)
    }

    @Test
    fun `session killed externally is dropped from state`() = runBlocking<Unit> {
        connectAndAwaitReady()
        withTimeout(5_000) { manager.attach("\$0") }

        factory.listResponse = "\$1\twork\t1\n"
        val control = factory.controlChannels.single()
        control.sendNotification("%exit killed")
        control.close()

        val state = awaitState { it.session("\$0") == null }
        assertThat(state.sessions.map { it.id }).containsExactly("\$1")
    }

    @Test
    fun `create session and attach`() = runBlocking<Unit> {
        factory.listResponse = "NO_SERVER\n"
        connectAndAwaitReady()

        factory.listResponse = "\$5\tconnectbot\t0\n"
        withTimeout(5_000) { manager.createSessionAndAttach("connectbot") }

        assertThat(factory.execLog).anyMatch { it.startsWith("tmux new-session -d -s 'connectbot'") }
        val session = manager.state.value.session("\$5")!!
        assertThat(session.attachState).isEqualTo(TmuxAttachState.ATTACHED)
    }

    @Test
    fun `shell quoting escapes single quotes in names`() {
        assertThat(TmuxSessionManager.escapeSingleQuotes("it's"))
            .isEqualTo("it'\\''s")
    }
}
