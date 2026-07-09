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

package org.connectbot.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceConstantsReconnectTest {

    @Test
    fun parseReconnectMaxAttempts_acceptsNonNegativeValues() {
        assertEquals(0, PreferenceConstants.parseReconnectMaxAttempts("0"))
        assertEquals(3, PreferenceConstants.parseReconnectMaxAttempts("3"))
    }

    @Test
    fun parseReconnectMaxAttempts_clampsNegativeValuesToZero() {
        assertEquals(0, PreferenceConstants.parseReconnectMaxAttempts("-2"))
    }

    @Test
    fun parseReconnectMaxAttempts_usesDefaultForInvalidValues() {
        assertEquals(0, PreferenceConstants.parseReconnectMaxAttempts(null))
        assertEquals(0, PreferenceConstants.parseReconnectMaxAttempts(""))
        assertEquals(0, PreferenceConstants.parseReconnectMaxAttempts("nope"))
    }

    @Test
    fun parseReconnectIntervalSeconds_acceptsConfiguredValue() {
        assertEquals(15, PreferenceConstants.parseReconnectIntervalSeconds("15"))
    }

    @Test
    fun parseReconnectIntervalSeconds_clampsToAllowedRange() {
        assertEquals(0, PreferenceConstants.parseReconnectIntervalSeconds("-1"))
        assertEquals(
            PreferenceConstants.MAX_RECONNECT_INTERVAL_SECONDS,
            PreferenceConstants.parseReconnectIntervalSeconds("7200"),
        )
    }

    @Test
    fun parseReconnectIntervalSeconds_usesDefaultForInvalidValues() {
        assertEquals(
            PreferenceConstants.DEFAULT_RECONNECT_INTERVAL_SECONDS,
            PreferenceConstants.parseReconnectIntervalSeconds(null),
        )
        assertEquals(
            PreferenceConstants.DEFAULT_RECONNECT_INTERVAL_SECONDS,
            PreferenceConstants.parseReconnectIntervalSeconds(""),
        )
        assertEquals(
            PreferenceConstants.DEFAULT_RECONNECT_INTERVAL_SECONDS,
            PreferenceConstants.parseReconnectIntervalSeconds("nope"),
        )
    }
}
