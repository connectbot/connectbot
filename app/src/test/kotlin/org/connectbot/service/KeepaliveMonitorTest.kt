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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class KeepaliveMonitorTest {

    private class Harness(
        var eligible: Boolean = true,
        var probe: suspend () -> Unit = {},
    ) {
        var probeCount = 0
            private set
        var deadCount = 0
            private set

        fun monitor(intervalMs: Long = 60_000L, timeoutMs: Long = 20_000L) = KeepaliveMonitor(
            intervalMs = intervalMs,
            timeoutMs = timeoutMs,
            isEligible = { eligible },
            sendKeepalive = {
                probeCount++
                probe()
            },
            onDead = { deadCount++ },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroInterval_isRejected() {
        Harness().monitor(intervalMs = 0L)
    }

    @Test
    fun probesOnEachInterval_whileHealthy() = runTest {
        val harness = Harness()
        val job = launch { harness.monitor().run() }

        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(1, harness.probeCount)

        advanceTimeBy(120_000L)
        runCurrent()
        assertEquals(3, harness.probeCount)
        assertEquals(0, harness.deadCount)

        job.cancelAndJoin()
    }

    @Test
    fun skipsProbe_whenIneligible() = runTest {
        val harness = Harness(eligible = false)
        val job = launch { harness.monitor().run() }

        advanceTimeBy(180_000L)
        runCurrent()
        assertEquals(0, harness.probeCount)
        assertEquals(0, harness.deadCount)

        // Becomes eligible again (e.g. grace period ended) and resumes probing
        harness.eligible = true
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(1, harness.probeCount)

        job.cancelAndJoin()
    }

    @Test
    fun declaresDead_whenProbeThrows() = runTest {
        val harness = Harness(probe = { throw IOException("connection dead") })
        val job = launch { harness.monitor().run() }

        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(1, harness.deadCount)
        assertTrue(job.isCompleted)
    }

    @Test
    fun declaresDead_whenProbeTimesOut() = runTest {
        val harness = Harness(probe = { delay(600_000L) })
        val job = launch { harness.monitor().run() }

        // Interval elapses, then the probe hangs past the timeout
        advanceTimeBy(60_000L + 20_000L)
        runCurrent()
        assertEquals(1, harness.deadCount)
        assertTrue(job.isCompleted)
    }

    @Test
    fun stopsProbing_afterDead() = runTest {
        val harness = Harness(probe = { throw IOException("connection dead") })
        val job = launch { harness.monitor().run() }

        advanceTimeBy(600_000L)
        runCurrent()
        assertEquals(1, harness.probeCount)
        assertEquals(1, harness.deadCount)
        assertTrue(job.isCompleted)
    }

    @Test
    fun cancellation_stopsCleanly_withoutDeclaringDead() = runTest {
        val harness = Harness()
        val job = launch { harness.monitor().run() }

        advanceTimeBy(60_000L)
        runCurrent()
        job.cancelAndJoin()

        advanceTimeBy(600_000L)
        runCurrent()
        assertEquals(1, harness.probeCount)
        assertEquals(0, harness.deadCount)
        assertFalse(job.isActive)
    }
}
