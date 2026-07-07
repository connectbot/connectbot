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
import org.connectbot.transport.ExecChannel

/**
 * A scripted fake tmux server: command lines written to [stdin] trigger the
 * next scripted reply on [stdout]; notifications can be injected any time.
 */
internal class FakeTmuxChannel : ExecChannel {
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

    private val prefixReplies = mutableMapOf<String, ArrayDeque<String>>()

    fun scriptReply(body: String, ok: Boolean = true) {
        scriptedReplies.addLast(buildReply(body, ok))
    }

    /** Scripts a reply served to the next command starting with [prefix]. */
    fun scriptReplyFor(prefix: String, body: String, ok: Boolean = true) {
        prefixReplies.getOrPut(prefix) { ArrayDeque() }.addLast(buildReply(body, ok))
    }

    private fun buildReply(body: String, ok: Boolean): String {
        val n = nextReplyNumber++
        val terminator = if (ok) "%end" else "%error"
        return "%begin 1700000000 $n 1\n$body$terminator 1700000000 $n 1\n"
    }

    fun sendNotification(line: String) = sendRaw("$line\n")

    fun commands(): List<String> = synchronized(commandLog) { commandLog.toList() }

    private fun onCommand(command: String) {
        synchronized(commandLog) { commandLog.add(command) }
        val byPrefix = prefixReplies.entries
            .firstOrNull { command.startsWith(it.key) }
            ?.value?.removeFirstOrNull()
        val reply = when {
            byPrefix != null -> byPrefix
            scriptedReplies.isNotEmpty() -> scriptedReplies.removeFirst()
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
