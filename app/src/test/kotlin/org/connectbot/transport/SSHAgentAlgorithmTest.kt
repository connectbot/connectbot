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

import org.connectbot.sshlib.AgentSignatureFlags
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAKey
import java.security.interfaces.RSAPublicKey

class SSHAgentAlgorithmTest {
    @Test
    fun keystoreRsa_honorsRequestedDigestWithoutExportablePrivateKey() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        // Android Keystore implements RSAKey, but deliberately not RSAPrivateKey.
        val opaqueKey = object : PrivateKey, RSAKey {
            override fun getAlgorithm() = "RSA"
            override fun getFormat(): String? = null
            override fun getEncoded(): ByteArray? = null
            override fun getModulus(): BigInteger = (pair.public as RSAPublicKey).modulus
        }
        for (key in listOf(pair, KeyPair(pair.public, opaqueKey))) {
            val ssh = SSH()
            assertEquals("rsa-sha2-512", ssh.determineAlgorithm(key, AgentSignatureFlags.RSA_SHA2_512))
            assertEquals("rsa-sha2-256", ssh.determineAlgorithm(key, AgentSignatureFlags.RSA_SHA2_256))
            assertEquals("ssh-rsa", ssh.determineAlgorithm(key, 0))
        }
    }
}
