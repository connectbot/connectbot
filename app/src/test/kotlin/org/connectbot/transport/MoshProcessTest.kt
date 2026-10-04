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

import org.junit.Assert.assertEquals
import org.junit.Test

class MoshProcessTest {
    @Test
    fun terminationResumesRunningAndStoppedClientsAndPreventsLaterSignals() {
        for (stopped in listOf(false, true)) {
            val signals = mutableListOf<Pair<Long, Int>>()
            val process = MoshProcess { pid, signal -> signals.add(pid to signal) }
            process.attach(123)
            if (stopped) process.pause()
            signals.clear()
            process.terminate()
            process.pause()
            process.resume()
            process.terminate()
            assertEquals(listOf(123L to 18, 123L to 15), signals)
        }
    }

    @Test
    fun absentAndExitedClientsReceiveNoSignals() {
        val signals = mutableListOf<Int>()
        val process = MoshProcess { _, signal -> signals.add(signal) }
        process.terminate()
        process.attach(123)
        process.exited(123)
        process.pause()
        process.resume()
        process.terminate()
        assertEquals(emptyList<Int>(), signals)
    }

    @Test
    fun oldExitDoesNotClearNewClient() {
        val signals = mutableListOf<Pair<Long, Int>>()
        val process = MoshProcess { pid, signal -> signals.add(pid to signal) }
        process.attach(456)
        process.exited(123)
        process.terminate()
        assertEquals(listOf(456L to 18, 456L to 15), signals)
    }
}
