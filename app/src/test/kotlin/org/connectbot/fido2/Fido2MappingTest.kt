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

package org.connectbot.fido2

import org.connectbot.fido2.ssh.SkEcdsaVerify
import org.connectbot.fido2.ssh.SkEd25519Verify
import org.junit.Assert.assertThrows
import org.junit.Test

class Fido2MappingTest {
    @Test
    fun securityKeyDecoderRejectsWrongAlgorithmAndMalformedBlobs() {
        val ed = org.connectbot.sshlib.sk.SkPublicKeyEncoder.encode(org.connectbot.sshlib.sk.SkAlgorithm.ED25519, ByteArray(32), "ssh:")
        val ec = org.connectbot.sshlib.sk.SkPublicKeyEncoder.encode(org.connectbot.sshlib.sk.SkAlgorithm.ECDSA_P256, ByteArray(65).apply { this[0] = 4 }, "ssh:")
        assertThrows(java.io.IOException::class.java) { SkEcdsaVerify.decodePublicKey(ed) }
        assertThrows(java.io.IOException::class.java) { SkEd25519Verify.decodePublicKey(ec) }
        assertThrows(java.io.IOException::class.java) { SkEd25519Verify.decodePublicKey(byteArrayOf(0, 1, 2)) }
    }
}
