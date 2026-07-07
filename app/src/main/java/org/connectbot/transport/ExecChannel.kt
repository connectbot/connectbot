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

package org.connectbot.transport

import java.io.InputStream
import java.io.OutputStream

/**
 * A secondary command channel multiplexed over an established, authenticated
 * connection, running a single remote command independently of the primary
 * shell session (e.g. a `tmux` control-mode client or a one-shot probe).
 *
 * Callers own the channel and must [close] it when finished.
 */
interface ExecChannel {
    /** Stream connected to the remote command's standard input. */
    val stdin: OutputStream

    /** Stream connected to the remote command's standard output. */
    val stdout: InputStream

    /** Stream connected to the remote command's standard error. */
    val stderr: InputStream

    /**
     * Exit status of the remote command, or null if it has not exited (or the
     * transport never learned it).
     */
    fun exitStatus(): Int?

    /** Closes the channel. The remote command is torn down if still running. */
    fun close()
}
