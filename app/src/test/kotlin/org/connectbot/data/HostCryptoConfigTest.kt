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

package org.connectbot.data

import org.connectbot.data.entity.HostCryptoConfig
import org.connectbot.sshlib.AlgorithmCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HostCryptoConfigTest {
    @Test
    fun eachCategoryNormalizesItsExpressionAndCanBeClearedIndependently() {
        var config = HostCryptoConfig(preset = "CUSTOM", minimumRsaBits = 4096)
        AlgorithmCategory.entries.forEach { category ->
            config = config.withExpression(category, "  +future@example.com  ")
            assertEquals("+future@example.com", config.expression(category))
        }
        AlgorithmCategory.entries.forEach { category ->
            val cleared = config.withExpression(category, "  ")
            assertEquals(null, cleared.expression(category))
            assertEquals(cleared, config.withExpression(category, null))
            assertEquals(4096, cleared.minimumRsaBits)
            AlgorithmCategory.entries.filter { it != category }.forEach {
                assertEquals("+future@example.com", cleared.expression(it))
            }
        }
    }

    @Test
    fun customResolutionAppliesAllCategoriesAndMinimums() {
        val overrides = mapOf(
            AlgorithmCategory.KEX to "curve25519-sha256",
            AlgorithmCategory.CIPHER to "aes256-ctr",
            AlgorithmCategory.MAC to "hmac-sha2-256",
            AlgorithmCategory.HOST_KEY to "ssh-ed25519",
            AlgorithmCategory.PUBKEY to "ssh-ed25519",
        )
        val config = overrides.entries.fold(HostCryptoConfig(preset = "CUSTOM", minimumRsaBits = 4096, minimumDhBits = 4096)) { value, (category, expression) -> value.withExpression(category, expression) }
        val policy = config.resolve()
        overrides.forEach { (category, expression) -> assertEquals(listOf(expression), policy.algorithms[category]) }
        assertEquals(4096, policy.minimumRsaBits)
        assertEquals(4096, policy.minimumDhBits)
    }

    @Test
    fun namedPresetIgnoresRetainedCustomSettingsAndUnknownPresetsFailClosed() {
        val policy = HostCryptoConfig(preset = "LEGACY", cipher = "invalid", minimumRsaBits = -1).resolve()
        assertEquals(1024, policy.minimumRsaBits)
        assertNotNull(policy.algorithms[AlgorithmCategory.CIPHER])
        assertThrows(IllegalArgumentException::class.java) { HostCryptoConfig(preset = "UNKNOWN").resolve() }
    }
}
