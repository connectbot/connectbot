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
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test

class TmuxPaneRegistryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val evicted = mutableListOf<String>()
    private val emulators = mutableMapOf<String, FakeEmulator>()
    private val registry = TmuxPaneRegistry(
        maxLivePanes = 3,
        onEvicted = { evicted.add(it.paneId) },
    )

    @After
    fun tearDown() {
        registry.clear()
        scope.cancel()
    }

    private fun newTerminal(sessionId: String, paneId: String): TmuxPaneTerminal = TmuxPaneTerminal(
        sessionId = sessionId,
        paneId = paneId,
        initialRows = 24,
        initialCols = 80,
        colors = TmuxPaneColors.DEFAULT,
        scope = scope,
        sendCommand = { TmuxReply(0, true, emptyList()) },
        emulatorFactory = { _, _, _, _, _, _, _, _ ->
            FakeEmulator().also { emulators["$sessionId|$paneId"] = it }
        },
    )

    private fun acquire(sessionId: String, paneId: String): Pair<TmuxPaneTerminal, Boolean> = registry.acquire(sessionId, paneId) { newTerminal(sessionId, paneId) }

    @Test
    fun `acquire returns same terminal for same pane`() {
        val (first, created) = acquire("\$0", "%1")
        val (second, createdAgain) = acquire("\$0", "%1")
        assertThat(created).isTrue()
        assertThat(createdAgain).isFalse()
        assertThat(second).isSameAs(first)
        assertThat(registry.liveCount()).isEqualTo(1)
    }

    @Test
    fun `least recently acquired pane is evicted beyond cap`() {
        acquire("\$0", "%1")
        acquire("\$0", "%2")
        acquire("\$0", "%3")
        acquire("\$0", "%1") // touch %1: %2 is now oldest

        acquire("\$0", "%4")

        assertThat(evicted).containsExactly("%2")
        assertThat(registry.liveCount()).isEqualTo(3)
        assertThat(registry.get("\$0", "%2")).isNull()
        assertThat(registry.get("\$0", "%1")).isNotNull
        assertThat(emulators["\$0|%2"]!!.closeCount).isEqualTo(1)
        assertThat(emulators["\$0|%1"]!!.closeCount).isZero()
    }

    @Test
    fun `routing output does not refresh recency`() {
        val (oldest, _) = acquire("\$0", "%1")
        acquire("\$0", "%2")
        acquire("\$0", "%3")

        // Flooding output on the oldest pane must not protect it.
        registry.route("\$0", TmuxNotification.Output("%1", "flood".toByteArray(), 1))
        acquire("\$0", "%4")

        assertThat(evicted).containsExactly("%1")
        assertThat(registry.get("\$0", "%1")).isNull()
        // The evicted terminal was destroyed: further routing is a no-op.
        registry.route("\$0", TmuxNotification.Output("%1", "late".toByteArray(), 2))
        assertThat(oldest.paneId).isEqualTo("%1")
    }

    @Test
    fun `remove session destroys only that session's terminals`() {
        acquire("\$0", "%1")
        acquire("\$1", "%9")

        registry.removeSession("\$0")

        assertThat(registry.get("\$0", "%1")).isNull()
        assertThat(registry.get("\$1", "%9")).isNotNull
        assertThat(registry.liveCount()).isEqualTo(1)
        assertThat(emulators["\$0|%1"]!!.closeCount).isEqualTo(1)
        assertThat(emulators["\$1|%9"]!!.closeCount).isZero()
    }

    @Test
    fun `clear destroys everything`() {
        acquire("\$0", "%1")
        acquire("\$1", "%9")
        registry.clear()
        assertThat(registry.liveCount()).isEqualTo(0)
        assertThat(emulators.values.map { it.closeCount }).containsExactlyInAnyOrder(1, 1)
    }
}
