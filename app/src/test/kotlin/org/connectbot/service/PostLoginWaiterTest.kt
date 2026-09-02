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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostLoginWaiterTest {

    @Test
    fun `idle wait holds until the remote stops talking`() = runTest {
        val waiter = PostLoginWaiter(waitFor = null, idleMillis = 500L)

        val ready = async { waiter.awaitReady() }
        runCurrent()
        assertThat(ready.isCompleted).isFalse()

        waiter.onOutput("Welcome to the machine\r\n")
        advanceTimeBy(400L)
        runCurrent()
        assertThat(ready.isCompleted).isFalse()

        // Still talking, so the quiet period starts over.
        waiter.onOutput("Last login: today\r\n")
        advanceTimeBy(400L)
        runCurrent()
        assertThat(ready.isCompleted).isFalse()

        advanceTimeBy(200L)
        runCurrent()
        assertThat(ready.await()).isTrue()
    }

    @Test
    fun `idle wait does not fire before the remote says anything`() = runTest {
        val waiter = PostLoginWaiter(waitFor = null, idleMillis = 500L, idleTimeoutMillis = 10_000L)

        val ready = async { waiter.awaitReady() }

        advanceTimeBy(5_000L)
        runCurrent()
        assertThat(ready.isCompleted).isFalse()

        waiter.onOutput("$ ")
        advanceTimeBy(500L)
        assertThat(ready.await()).isTrue()
    }

    @Test
    fun `idle wait gives up once the timeout expires`() = runTest {
        val waiter = PostLoginWaiter(waitFor = null, idleMillis = 500L, idleTimeoutMillis = 10_000L)

        val ready = async { waiter.awaitReady() }

        advanceTimeBy(10_001L)
        assertThat(ready.await()).isFalse()
    }

    @Test
    fun `wait for text ignores output that does not match`() = runTest {
        val waiter = PostLoginWaiter(waitFor = "user@host:~$ ")

        val ready = async { waiter.awaitReady() }

        // A Tailscale check prints a URL and then waits on the browser.
        waiter.onOutput("# Tailscale SSH requires an additional check.\r\n")
        waiter.onOutput("# To authenticate, visit: https://login.example.com/a/0123456789ab\r\n")
        advanceTimeBy(30_000L)
        runCurrent()
        assertThat(ready.isCompleted).isFalse()

        waiter.onOutput("user@host:~$ ")
        runCurrent()
        assertThat(ready.await()).isTrue()
    }

    @Test
    fun `wait for text matches across chunk boundaries`() = runTest {
        val waiter = PostLoginWaiter(waitFor = "ready>")

        val ready = async { waiter.awaitReady() }

        waiter.onOutput("system is rea")
        runCurrent()
        assertThat(ready.isCompleted).isFalse()

        waiter.onOutput("dy> ")
        runCurrent()
        assertThat(ready.await()).isTrue()
    }

    @Test
    fun `wait for text matches through escape sequences on a coloured prompt`() = runTest {
        val waiter = PostLoginWaiter(waitFor = "$ ")

        val ready = async { waiter.awaitReady() }

        waiter.onOutput("\u001B[1;32muser@host\u001B[0m:\u001B[1;34m~\u001B[0m$ ")
        runCurrent()
        assertThat(ready.await()).isTrue()
    }

    @Test
    fun `wait for text that already arrived is not missed`() = runTest {
        val waiter = PostLoginWaiter(waitFor = "ready>")

        waiter.onOutput("ready> ")

        assertThat(waiter.awaitReady()).isTrue()
    }

    @Test
    fun `wait for text gives up once the timeout expires`() = runTest {
        val waiter = PostLoginWaiter(waitFor = "never appears", waitForTimeoutMillis = 120_000L)

        val ready = async { waiter.awaitReady() }

        waiter.onOutput("something else entirely\r\n")
        advanceTimeBy(120_001L)
        assertThat(ready.await()).isFalse()
    }

    @Test
    fun `unterminated commands get entered`() {
        assertThat(postLoginAsTyped("tmux attach")).isEqualTo("tmux attach\r")
    }

    @Test
    fun `every line of multi-line commands gets entered`() {
        assertThat(postLoginAsTyped("cd /var/www\nls -l")).isEqualTo("cd /var/www\rls -l\r")
    }

    @Test
    fun `line feeds become carriage returns for windows openssh`() {
        assertThat(postLoginAsTyped("dir\n")).isEqualTo("dir\r")
        assertThat(postLoginAsTyped("dir\r\n")).isEqualTo("dir\r")
    }

    @Test
    fun `commands already ending in a carriage return are unchanged`() {
        assertThat(postLoginAsTyped("tmux attach\r")).isEqualTo("tmux attach\r")
    }

    @Test
    fun `empty wait for text falls back to the idle wait`() = runTest {
        val waiter = PostLoginWaiter(waitFor = "", idleMillis = 500L)

        val ready = async { waiter.awaitReady() }

        waiter.onOutput("$ ")
        advanceTimeBy(500L)
        assertThat(ready.await()).isTrue()
    }
}
