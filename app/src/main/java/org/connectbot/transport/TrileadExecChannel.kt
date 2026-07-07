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

import com.trilead.ssh2.Session
import java.io.InputStream
import java.io.OutputStream

/**
 * [ExecChannel] backed by a trilead SSH [Session] on which a remote command
 * has already been started via [Session.execCommand].
 */
class TrileadExecChannel(private val session: Session) : ExecChannel {
    override val stdin: OutputStream
        get() = session.stdin

    override val stdout: InputStream
        get() = session.stdout

    override val stderr: InputStream
        get() = session.stderr

    override fun exitStatus(): Int? = session.exitStatus

    override fun close() {
        session.close()
    }
}
