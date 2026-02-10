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

import org.connectbot.sshlib.SshSigning
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.KeyPairGenerator

class SSHHostKeySizeTest {
    @Test
    fun rsaSize_isTheModulusBitLength() {
        for (bits in listOf(2048, 3072, 4096)) {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()
            val blob = SshSigning.encodePublicKey(pair).publicKeyBlob
            for (algorithm in listOf("ssh-rsa", "rsa-sha2-256", "rsa-sha2-512")) {
                assertEquals(bits, SSH.getKeySizeFromAlgorithm(algorithm, blob))
            }
            assertEquals(0, SSH.getKeySizeFromAlgorithm("ssh-rsa", blob.copyOf(blob.size - 1)))
        }
    }

    @Test
    fun malformedKeySize_isUnknown() {
        assertEquals(0, SSH.getKeySizeFromAlgorithm("ssh-rsa", byteArrayOf()))
        assertEquals(0, SSH.getKeySizeFromAlgorithm("ssh-rsa", byteArrayOf(-1, -1, -1, -1)))
    }
}
