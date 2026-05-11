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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SSHTest(
    private val algorithm: String,
    private val expectedKeyType: String?,
) {
    @Test
    fun getKeyType_returnsDisplayName() {
        assertThat(SSH().getKeyType(algorithm)).isEqualTo(expectedKeyType)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun data(): List<Array<Any?>> = listOf(
            arrayOf("ssh-rsa", "RSA"),
            arrayOf("rsa-sha2-256", "RSA"),
            arrayOf("rsa-sha2-512", "RSA"),
            arrayOf("ssh-dss", "DSA"),
            arrayOf("ssh-ed25519", "Ed25519"),
            arrayOf("ecdsa-sha2-nistp256", "EC"),
            arrayOf("unknown", null),
        )
    }
}
