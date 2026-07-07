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

import org.connectbot.util.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandCompletionTest {

    @Test
    fun meetsCompletionThresholdBoundaries() {
        assertTrue(meetsCompletionThreshold(durationMs = 30_000, thresholdMs = 30_000))
        assertTrue(meetsCompletionThreshold(durationMs = 30_000, thresholdMs = 1_000))
        assertFalse(meetsCompletionThreshold(durationMs = 30_000, thresholdMs = 30_001))
        assertFalse(
            "threshold 0 means the feature is off",
            meetsCompletionThreshold(durationMs = 30_000, thresholdMs = 0),
        )
        assertFalse(
            "termlib reports -1 when the command start was never seen",
            meetsCompletionThreshold(durationMs = -1, thresholdMs = 30_000),
        )
    }

    @Test
    fun formatDurationRendering() {
        assertEquals("0s", formatDuration(499))
        assertEquals("37s", formatDuration(37_000))
        assertEquals("2m 14s", formatDuration(134_000))
        assertEquals("1h 03m", formatDuration(3_780_000))
    }
}
