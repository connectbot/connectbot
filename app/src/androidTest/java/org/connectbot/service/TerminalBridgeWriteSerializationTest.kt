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
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.transport.AbsTransport
import org.connectbot.ui.MainActivity
import org.connectbot.util.waitUntilServiceBound
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
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
    private class LossyNonThreadSafeTransport : AbsTransport() {
        val attemptedBytes = AtomicInteger(0)

        // Intentionally NOT volatile / NOT synchronized — the whole point is
        // to model an unsynchronized shared buffer.
        private var data: ByteArray = ByteArray(0)

        fun receivedBytes(): Int = data.size

        override fun connect() = Unit
        override fun read(buffer: ByteArray, offset: Int, length: Int) = 0

        override fun write(buffer: ByteArray) {
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

        override fun write(c: Int) {
            write(byteArrayOf(c.toByte()))
        }

        override fun flush() = Unit
        override fun close() = Unit
        override fun setDimensions(columns: Int, rows: Int, width: Int, height: Int) = Unit
        override fun isConnected() = true
        override fun isSessionOpen() = true
        override fun getDefaultPort() = 22
        override fun getDefaultNickname(username: String?, hostname: String?, port: Int) = "test"
        override fun getSelectionArgs(uri: Uri, selection: MutableMap<String, String>) = Unit
        override fun createHost(uri: Uri) = Host()
        override fun usesNetwork() = true
        override fun getLocalIpAddress(): String = "127.0.0.1"
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
                id = 0L,
                nickname = "bridge-serialization-test",
                protocol = "local",
                username = "",
                hostname = "",
                port = 0,
                profileId = 1L,
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
}
