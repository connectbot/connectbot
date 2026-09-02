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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Turn the post-login field into the keystrokes the user would have typed.
 *
 * Typing a command is not the same as running it: with no terminator the last
 * one just sits on the prompt. The text box also produces plain line feeds,
 * which a Unix tty accepts as a line terminator but Windows OpenSSH ignores,
 * so post-login never ran there even when newlines were added by hand.
 *
 * The Enter key sends a carriage return, so send that: every line ending
 * becomes one, and the text always ends in one. The remote tty turns it back
 * into the newline the shell reads, and Windows gets the character it waits
 * for.
 */
internal fun postLoginAsTyped(commands: String): String {
    val typed = commands.replace("\r\n", "\r").replace('\n', '\r')
    if (typed.endsWith('\r')) return typed
    return typed + '\r'
}

/**
 * Decides when a host's post-login commands may be written to the remote.
 *
 * The commands used to go out the instant the SSH shell request was sent, which
 * loses them whenever the far end is not a shell yet: a Tailscale SSH check
 * still waiting on its browser approval, a slow login script, or a login that
 * flushes pending terminal input before handing over to the shell. The bytes
 * are swallowed, so nothing is echoed and nothing runs.
 *
 * Waiting for the remote to look ready avoids that. With [waitFor] set, the
 * commands go out once that text turns up in the remote output, which is what
 * makes the interactive cases work: the wait outlasts the browser round trip
 * and ends on the real shell prompt. With [waitFor] empty they go out once the
 * remote has said something and then stayed quiet for [idleMillis]. Either way
 * a timeout caps the wait, so a host whose trigger never arrives still runs its
 * commands instead of silently skipping them.
 *
 * Matching is a plain substring test against the raw output, escape sequences
 * included, so a coloured prompt still matches on its visible tail.
 */
class PostLoginWaiter(
    waitFor: String?,
    private val idleMillis: Long = DEFAULT_IDLE_MILLIS,
    idleTimeoutMillis: Long = DEFAULT_IDLE_TIMEOUT_MILLIS,
    waitForTimeoutMillis: Long = DEFAULT_WAIT_FOR_TIMEOUT_MILLIS,
) {
    private val target = waitFor?.takeIf { it.isNotEmpty() }

    private val timeoutMillis = if (target == null) idleTimeoutMillis else waitForTimeoutMillis

    /** Tail of the remote output, only as much of it as a [target] match can still need. */
    private val tail = StringBuilder()

    /** Signals that output arrived. Conflated, so a burst nobody is reading counts once. */
    private val output = Channel<Unit>(Channel.CONFLATED)

    private val targetSeen = CompletableDeferred<Unit>()

    /**
     * Feed a chunk of decoded remote output. Called from the relay for every
     * chunk it decodes, so it stays cheap and never suspends.
     */
    fun onOutput(text: String) {
        if (text.isEmpty()) return

        output.trySend(Unit)

        val target = this.target ?: return
        if (targetSeen.isCompleted) return

        synchronized(tail) {
            tail.append(text)
            if (tail.indexOf(target) >= 0) {
                tail.setLength(0)
                targetSeen.complete(Unit)
                return
            }
            // Keep only what the next chunk could still complete a match with.
            val keep = target.length - 1
            if (tail.length > keep) {
                tail.delete(0, tail.length - keep)
            }
        }
    }

    /**
     * Suspends until the remote looks ready for the commands.
     *
     * @return true if the remote became ready, false if the wait timed out and
     *   the caller should send the commands anyway
     */
    suspend fun awaitReady(): Boolean {
        val ready = withTimeoutOrNull(timeoutMillis) {
            if (target != null) {
                targetSeen.await()
            } else {
                // Wait for the remote to say something, then for it to stop talking.
                output.receive()
                while (withTimeoutOrNull(idleMillis) { output.receive() } != null) {
                    // More output arrived, so the remote is still busy.
                }
            }
        }
        return ready != null
    }

    companion object {
        /** How long the remote must stay quiet before it counts as ready. */
        const val DEFAULT_IDLE_MILLIS = 500L

        /** Caps the idle wait, so a remote that never says anything still gets its commands. */
        const val DEFAULT_IDLE_TIMEOUT_MILLIS = 10_000L

        /** Caps the [waitFor] wait. Generous, because it can span a browser round trip. */
        const val DEFAULT_WAIT_FOR_TIMEOUT_MILLIS = 120_000L
    }
}
