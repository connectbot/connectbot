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

/** Serializes lifecycle signals so a closing client cannot be stopped again. */
internal class MoshProcess(private val signal: (Long, Int) -> Unit) {
    private var processId = 0L

    @Synchronized
    fun attach(pid: Long) {
        processId = pid
    }

    @Synchronized
    fun pause() {
        if (processId > 0) signal(processId, SIGSTOP)
    }

    @Synchronized
    fun resume() {
        if (processId > 0) signal(processId, SIGCONT)
    }

    @Synchronized
    fun terminate() {
        if (processId <= 0) return
        val pid = processId
        processId = 0
        signal(pid, SIGCONT)
        signal(pid, SIGTERM)
    }

    @Synchronized
    fun exited(pid: Long) {
        if (processId == pid) processId = 0
    }

    private companion object {
        const val SIGSTOP = 19
        const val SIGCONT = 18
        const val SIGTERM = 15
    }
}
