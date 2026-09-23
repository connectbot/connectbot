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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.connectbot.data.EntityIds
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.TelnetEchoMode
import org.connectbot.data.entity.TelnetNegotiationMode
import org.connectbot.data.keyboard.MacroAction
import org.connectbot.data.keyboard.MacroFormat
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.terminal.TerminalEmulatorFactory
import org.connectbot.terminal.VTermKey
import org.connectbot.transport.AbsTransport
import org.connectbot.transport.SSH
import org.connectbot.ui.MainActivity
import org.connectbot.util.HostConstants
import org.connectbot.util.waitUntilServiceBound
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * On-device regression test for GitHub issue #2058 — "ConnectBot loses chars
 * on long copy+paste".
 *
 * Root cause: [TerminalKeyListener.onKey] wrote keyboard bytes directly to
 * `transport.write(...)` from the caller's thread (UI), while paste data
 * enqueued through [TerminalBridge.injectString] was written from the IO
 * coroutine draining `transportOperations`. Under load (long paste + any
 * keystroke) the two paths raced on the same underlying `OutputStream` and
 * bytes interleaved at the SSH channel level — dropping characters.
 *
 * This test drives both paths concurrently against a transport that models a
 * non-thread-safe byte stream (the same pattern as the real SSH
 * [java.io.OutputStream] when two threads call `write()` on an
 * unsynchronized buffer — the last writer's `System.arraycopy` clobbers the
 * earlier writer's appended region, losing bytes). It then asserts that the
 * bytes actually delivered equal the bytes attempted. On the pre-fix code
 * bytes go missing exactly as users report.
 *
 * Must run on-device (androidTest) because `TerminalBridge`'s constructor
 * transitively loads the termlib JNI (`jni_cb_term`), which is only built
 * for Android ABIs.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TerminalBridgeWriteSerializationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val runtimePermissionRule: TestRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TestRule { base, _ -> base }
        }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /**
     * Transport backed by a non-thread-safe "grow-and-copy" byte buffer —
     * exactly the pattern that corrupts SSH's underlying stdin stream when
     * two threads call write() concurrently. Concurrent writes race on the
     * grow-then-copy-then-publish sequence, so the last writer's published
     * buffer overwrites the earlier writer's with neither writer's bytes
     * preserved.
     *
     * A short sleep between the allocate and the arraycopy widens the race
     * window so the bug manifests deterministically instead of
     * intermittently.
     */
    private open class LossyNonThreadSafeTransport : AbsTransport() {
        val attemptedBytes = AtomicInteger(0)

        // Intentionally NOT volatile / NOT synchronized — the whole point is
        // to model an unsynchronized shared buffer.
        private var data: ByteArray = ByteArray(0)

        fun receivedBytes(): Int = data.size

        override suspend fun connect(scope: CoroutineScope, dispatchers: CoroutineDispatchers, encoding: String) = Unit
        override suspend fun read(buffer: ByteArray, offset: Int, length: Int) = 0

        override suspend fun write(buffer: ByteArray) {
            attemptedBytes.addAndGet(buffer.size)
            val current = data
            val next = ByteArray(current.size + buffer.size)
            System.arraycopy(current, 0, next, 0, current.size)
            // Widen the race window: between reading `data` and publishing
            // `data = next`, another writer can complete its own
            // grow-and-copy, and our publish will overwrite theirs.
            Thread.sleep(1)
            System.arraycopy(buffer, 0, next, current.size, buffer.size)
            data = next
        }

        override suspend fun flush() = Unit
        override suspend fun close() = Unit
        override suspend fun resize(columns: Int, rows: Int, width: Int, height: Int) = Unit
        override fun isConnected() = true
        override fun isSessionOpen() = true
        override fun getDefaultPort() = 22
        override fun getDefaultNickname(username: String?, hostname: String?, port: Int) = "test"
        override fun getSelectionArgs(uri: Uri, selection: MutableMap<String, String>) = Unit
        override fun createHost(uri: Uri) = Host()
        override fun usesNetwork() = true
        override fun getLocalIpAddress(): String = "127.0.0.1"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun newSshTransportUsesExistingTerminalDimensions() = runTest {
        val intent = Intent(context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            var capturedActivity: MainActivity? = null
            scenario.onActivity { capturedActivity = it }
            val manager = runBlocking { requireNotNull(capturedActivity).waitUntilServiceBound() }.terminalManager
            val host = Host(
                id = UUID.randomUUID(),
                nickname = "bridge-resize-test",
                protocol = "ssh",
                username = "",
                hostname = "127.0.0.1",
                port = 1,
                profileId = EntityIds.DEFAULT_PROFILE,
            )
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val dispatchers = CoroutineDispatchers(
                default = testDispatcher,
                io = testDispatcher,
                main = Dispatchers.Main,
            )
            val bridge = TerminalBridge(manager, host, dispatchers)
            try {
                runCurrent()
                bridge.terminalEmulator.resize(42, 100)
                // Drain the resize before connecting so only the initial size seed can reach SSH.
                runCurrent()
                val dimensions = bridge.terminalEmulator.dimensions

                bridge.startConnection()
                // startConnection seeds dimensions on the injected IO dispatcher.
                runCurrent()

                val transport = bridge.transport as SSH
                fun dimension(name: String): Int = SSH::class.java.getDeclaredField(name).let { field ->
                    field.isAccessible = true
                    field.getInt(transport)
                }
                assertEquals(dimensions.columns, dimension("columns"))
                assertEquals(dimensions.rows, dimension("rows"))
                assertEquals(dimensions.widthPixels, dimension("width"))
                assertEquals(dimensions.heightPixels, dimension("height"))
            } finally {
                bridge.cleanup()
            }
        }
    }

    @Test
    fun concurrentPasteAndKeystrokeLoseCharactersAtTransport() {
        // MainActivity is launched only to get the Hilt-wired TerminalManager
        // via its bound service. We don't interact with the UI.
        val intent = Intent(context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            // Capture the activity on the main thread but do NOT block the main
            // thread with runBlocking — the TerminalEmulator's onKeyboardInput
            // callback is dispatched via Handler(mainLooper), so blocking the
            // main thread would prevent tab keystrokes from ever reaching the
            // transport.
            var capturedActivity: MainActivity? = null
            scenario.onActivity { activity ->
                capturedActivity = activity
            }
            val activity = requireNotNull(capturedActivity)

            val state = runBlocking { activity.waitUntilServiceBound() }
            val manager = state.terminalManager

            // id <= 0 makes TerminalBridge take the short-circuit profile-
            // observation path (no host observer needed).
            val host = Host(
                id = UUID(0, 0),
                nickname = "bridge-serialization-test",
                protocol = "local",
                username = "",
                hostname = "",
                port = 0,
                profileId = EntityIds.DEFAULT_PROFILE,
            )
            val dispatchers = CoroutineDispatchers(
                default = Dispatchers.Default,
                io = Dispatchers.IO,
                main = Dispatchers.Main,
            )

            val bridge = TerminalBridge(manager, host, dispatchers)
            try {
                val transport = LossyNonThreadSafeTransport()
                bridge.transport = transport

                val keyListener = bridge.keyHandler

                val iterations = 40

                runBlocking {
                    val jobs = mutableListOf<Job>()
                    repeat(iterations) { i ->
                        // Paste path: injectString -> transportOperations
                        // channel -> IO coroutine -> transport.write.
                        jobs += launch(Dispatchers.Default) {
                            bridge.injectString(
                                "paste-$i-" + "x".repeat(128),
                            )
                        }
                        // Keystroke path: pre-fix this called
                        // transport.write(0x09) directly from whichever
                        // thread invoked onKey.
                        jobs += launch(Dispatchers.IO) {
                            keyListener.sendTab()
                        }
                    }
                    jobs.forEach { it.join() }

                    // Let the serialized queue drain any remaining
                    // enqueued writes.
                    val expectedBytes =
                        // Each paste: "paste-N-" (or "paste-NN-") + 128 'x's.
                        (0 until iterations).sumOf { i ->
                            "paste-$i-".length + 128
                        } + iterations // one byte per Tab keystroke
                    withTimeout(10_000) {
                        while (transport.attemptedBytes.get() < expectedBytes) {
                            delay(10)
                        }
                        delay(250)
                    }
                }

                assertEquals(
                    "Characters were lost at the transport layer: " +
                        "attempted ${transport.attemptedBytes.get()} " +
                        "bytes, but only ${transport.receivedBytes()} " +
                        "survived. This is exactly the symptom reported " +
                        "in issue #2058 — dropped characters on long " +
                        "pastes due to unserialized writes racing at " +
                        "the underlying OutputStream.",
                    transport.attemptedBytes.get(),
                    transport.receivedBytes(),
                )
            } finally {
                bridge.cleanup()
            }
        }
    }

    @Test
    fun telnetBridgeQueuesEnterAndPasteAndDrainsRemoteOutput() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            var activity: MainActivity? = null
            scenario.onActivity { activity = it }
            val manager = runBlocking { requireNotNull(activity).waitUntilServiceBound() }.terminalManager
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                val text = "https://local.example/"
                val expected = (text + "\r\n").toByteArray() + byteArrayOf(13, 0)
                val wire = CompletableFuture.supplyAsync {
                    server.accept().use { peer ->
                        peer.soTimeout = 10000
                        peer.getOutputStream().write("https://remote.example/\r\n".toByteArray())
                        peer.getOutputStream().flush()
                        val received = ByteArray(expected.size)
                        java.io.DataInputStream(peer.getInputStream()).readFully(received)
                        peer.getOutputStream().write("https://eof.example/\r\n".toByteArray())
                        peer.getOutputStream().flush()
                        received
                    }
                }
                val host = Host(
                    id = UUID.randomUUID(), nickname = "Telnet bridge test", protocol = "telnet", hostname = "127.0.0.1", port = server.localPort,
                    telnetEchoMode = TelnetEchoMode.ON, telnetNegotiationMode = TelnetNegotiationMode.PASSIVE,
                    quickDisconnect = false, stayConnected = false,
                )
                val bridge = TerminalBridge(manager, host, CoroutineDispatchers(Dispatchers.Default, Dispatchers.IO, Dispatchers.Main))
                try {
                    bridge.startConnection()
                    runBlocking {
                        withTimeout(10000) {
                            while (!bridge.isSessionOpen || "https://remote.example/" !in bridge.scanForURLs()) delay(20)
                        }
                    }
                    bridge.injectString(text + "\n")
                    // Verify echo before Enter releases the peer's final response and EOF.
                    runBlocking {
                        withTimeout(10000) {
                            while (text !in bridge.scanForURLs()) delay(20)
                        }
                    }
                    bridge.keyHandler.sendPressedKey(VTermKey.ENTER)
                    assertArrayEquals(expected, wire.get(10, TimeUnit.SECONDS))
                    runBlocking {
                        withTimeout(10000) {
                            while (!bridge.disconnected || "https://eof.example/" !in bridge.scanForURLs()) delay(20)
                        }
                    }
                    assertEquals(DisconnectReason.REMOTE_EOF, bridge.disconnectReason)
                } finally {
                    bridge.cleanup()
                }
            }
        }
    }

    @Test
    fun queuedInputDoesNotMoveToReplacementTransport() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            var activity: MainActivity? = null
            scenario.onActivity { activity = it }
            val manager = runBlocking { requireNotNull(activity).waitUntilServiceBound() }.terminalManager
            val bridge = TerminalBridge(
                manager,
                Host(nickname = "Queue test", protocol = "local"),
                CoroutineDispatchers(Dispatchers.Default, Dispatchers.IO, Dispatchers.Main),
            )
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            val original = object : LossyNonThreadSafeTransport() {
                override suspend fun write(buffer: ByteArray) {
                    entered.complete(Unit)
                    release.await()
                    super.write(buffer)
                }
            }
            val replacement = LossyNonThreadSafeTransport()
            try {
                bridge.transport = original
                bridge.sendByte(1)
                bridge.sendByte(2)
                runBlocking { withTimeout(5000) { entered.await() } }
                bridge.transport = replacement
                bridge.sendByte(3)
                release.complete(Unit)
                runBlocking { withTimeout(5000) { while (replacement.receivedBytes() == 0) delay(20) } }
                assertEquals(1, original.receivedBytes())
                assertEquals(1, replacement.receivedBytes())
            } finally {
                release.complete(Unit)
                bridge.cleanup()
            }
        }
    }
    private class RecordingInputTransport(private val beforeWrite: suspend () -> Unit = {}) : LossyNonThreadSafeTransport() {
        private val bytes = ByteArrayOutputStream()
        override suspend fun write(buffer: ByteArray) {
            beforeWrite()
            synchronized(bytes) { bytes.write(buffer) }
        }
        fun text(): String = synchronized(bytes) { bytes.toString("UTF-8") }
        override fun isSessionOpen() = false
    }

    private fun withInputBridge(test: (TerminalBridge) -> Unit) {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            var activity: MainActivity? = null
            scenario.onActivity { activity = it }
            val manager = runBlocking { requireNotNull(activity).waitUntilServiceBound() }.terminalManager
            val bridge = TerminalBridge(manager, Host(nickname = "Configured input test", protocol = "local"), CoroutineDispatchers(Dispatchers.Default, Dispatchers.IO, Dispatchers.Main))
            try {
                test(bridge)
            } finally {
                bridge.cleanup()
            }
        }
    }

    private fun macro(vararg text: String): String = MacroFormat.encode(text.map { MacroAction.Text(it) })

    @Test
    fun configuredKeysAndMacrosCannotMoveToReplacementTransport() = withInputBridge { bridge ->
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val original = RecordingInputTransport {
            entered.complete(Unit)
            release.await()
        }
        val replacement = RecordingInputTransport()
        try {
            bridge.transport = original
            bridge.sendKeyboardMacro(macro("started", "old remainder"))
            runBlocking { withTimeout(5000) { entered.await() } }
            bridge.sendConfiguredKey("enter")
            bridge.sendKeyboardMacro(macro("queued"))
            bridge.transport = replacement
            bridge.sendKeyboardMacro(macro("new"))
            release.complete(Unit)
            runBlocking { withTimeout(5000) { while (replacement.text() != "new") delay(10) } }
            assertEquals("started", original.text())
            assertEquals("new", replacement.text())
        } finally {
            release.complete(Unit)
        }
    }

    @Test
    fun disconnectInvalidatesQueuedInputEvenWhenTransportObjectIsReused() = withInputBridge { bridge ->
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val original = RecordingInputTransport {
            entered.complete(Unit)
            release.await()
        }
        try {
            bridge.transport = original
            bridge.sendKeyboardMacro(macro("started", "old remainder"))
            runBlocking { withTimeout(5000) { entered.await() } }
            bridge.sendConfiguredKey("enter")
            bridge.sendKeyboardMacro(macro("queued"))
            bridge.dispatchDisconnect(DisconnectReason.IO_ERROR)
            bridge.onConnected()
            bridge.sendKeyboardMacro(macro("new"))
            release.complete(Unit)
            runBlocking { withTimeout(5000) { while (!original.text().endsWith("new")) delay(10) } }
            assertEquals("startednew", original.text())
        } finally {
            release.complete(Unit)
        }
    }

    @Test
    fun delayedSemanticCallbacksStayOnTheirCapturedConnection() {
        for (character in listOf(false, true)) {
            withInputBridge { bridge ->
                val original = RecordingInputTransport()
                val replacement = RecordingInputTransport()
                val outputQueued = CountDownLatch(1)
                val releaseCallback = CountDownLatch(1)
                val emulator = bridge.terminalEmulator

                @Suppress("UNCHECKED_CAST")
                val keyboardCallback = emulator.javaClass.getDeclaredField("onKeyboardInput").apply { isAccessible = true }.get(emulator) as (ByteArray) -> Unit
                val delayedEmulator = TerminalEmulatorFactory.create(onKeyboardInput = { bytes ->
                    outputQueued.countDown()
                    assertTrue(releaseCallback.await(10, TimeUnit.SECONDS))
                    keyboardCallback(bytes)
                })
                TerminalBridge::class.java.getDeclaredField("terminalEmulator").apply { isAccessible = true }.set(bridge, delayedEmulator)
                try {
                    bridge.transport = original
                    if (character) {
                        bridge.sendKeyboardMacro(MacroFormat.encode(listOf(MacroAction.Key(character = "x"), MacroAction.Text("old remainder"))))
                    } else {
                        bridge.sendConfiguredKey("enter")
                    }
                    assertTrue(outputQueued.await(5, TimeUnit.SECONDS))
                    // Cover both replacement alone and cancellation while callbacks are pending.
                    if (character) bridge.dispatchDisconnect(DisconnectReason.IO_ERROR)
                    bridge.transport = replacement
                    if (character) bridge.onConnected()
                    releaseCallback.countDown()
                    val callbacksDrained = CountDownLatch(1)
                    Handler(Looper.getMainLooper()).post { callbacksDrained.countDown() }
                    assertTrue(callbacksDrained.await(5, TimeUnit.SECONDS))
                    bridge.sendKeyboardMacro(macro("new"))
                    runBlocking { withTimeout(5000) { while (replacement.text() != "new") delay(10) } }
                    assertEquals("", original.text())
                    assertEquals("new", replacement.text())
                } finally {
                    releaseCallback.countDown()
                }
            }
        }
    }
}
