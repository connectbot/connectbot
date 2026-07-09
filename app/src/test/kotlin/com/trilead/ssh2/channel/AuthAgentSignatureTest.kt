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

package com.trilead.ssh2.channel

import com.trilead.ssh2.crypto.keys.Ed25519Provider
import com.trilead.ssh2.signature.ECDSASHA2Verify
import com.trilead.ssh2.signature.Ed25519Verify
import com.trilead.ssh2.signature.RSASHA1Verify
import com.trilead.ssh2.signature.RSASHA256Verify
import com.trilead.ssh2.signature.RSASHA512Verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPublicKey

/**
 * Tests for the signature dispatch in the patched [AuthAgentForwardThread]
 * (upstream issue connectbot#2212). The dispatch must happen on the public
 * key type so that opaque private keys (Android Keystore) are signable, and
 * ECDSA keys — which the sshlib 2.2.48 copy of this class did not handle at
 * all — must round-trip.
 *
 * The rsa-sha2 flag tests are also the regression coverage for upstream issue
 * connectbot#397 ("agent refused operation"): OpenSSH 7.2+ clients ask the
 * forwarded agent for rsa-sha2-256/512 signatures via the sign-request flags,
 * so the agent must honor them instead of failing.
 * https://github.com/connectbot/connectbot/issues/397
 */
class AuthAgentSignatureTest {

    private val challenge = "agent sign request challenge".toByteArray()

    private fun generate(algorithm: String, bits: Int): KeyPair {
        val generator = KeyPairGenerator.getInstance(algorithm)
        generator.initialize(bits)
        return generator.generateKeyPair()
    }

    @Test
    fun ecdsaSignature_verifies() {
        val pair = generate("EC", 256)

        val signature = AuthAgentForwardThread.generateSignature(pair, challenge, 0)

        assertThat(signature).isNotNull()
        val publicKey = pair.public as ECPublicKey
        val verifier = ECDSASHA2Verify.getVerifierForKey(publicKey)
        assertThat(verifier.verifySignature(challenge, signature, publicKey)).isTrue()
    }

    @Test
    fun rsaSignature_withoutFlags_usesSha1() {
        val pair = generate("RSA", 2048)

        val signature = AuthAgentForwardThread.generateSignature(pair, challenge, 0)

        assertThat(signature).isNotNull()
        assertThat(RSASHA1Verify.get().verifySignature(challenge, signature, pair.public)).isTrue()
    }

    @Test
    fun rsaSignature_withSha256Flag_usesSha256() {
        val pair = generate("RSA", 2048)

        val signature = AuthAgentForwardThread.generateSignature(
            pair,
            challenge,
            AuthAgentForwardThread.SSH_AGENT_RSA_SHA2_256,
        )

        assertThat(signature).isNotNull()
        assertThat(RSASHA256Verify.get().verifySignature(challenge, signature, pair.public)).isTrue()
    }

    @Test
    fun rsaSignature_withSha512Flag_usesSha512() {
        val pair = generate("RSA", 2048)

        val signature = AuthAgentForwardThread.generateSignature(
            pair,
            challenge,
            AuthAgentForwardThread.SSH_AGENT_RSA_SHA2_512,
        )

        assertThat(signature).isNotNull()
        assertThat(RSASHA512Verify.get().verifySignature(challenge, signature, pair.public)).isTrue()
    }

    @Test
    fun ed25519Signature_verifies() {
        Ed25519Provider.insertIfNeeded()
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

        val signature = AuthAgentForwardThread.generateSignature(pair, challenge, 0)

        assertThat(signature).isNotNull()
        assertThat(Ed25519Verify.get().verifySignature(challenge, signature, pair.public)).isTrue()
    }

    @Test
    fun unsupportedKeyType_returnsNullInsteadOfThrowing() {
        val unsupportedPublic = object : PublicKey {
            override fun getAlgorithm() = "XDH"
            override fun getFormat() = null
            override fun getEncoded() = ByteArray(0)
        }
        val unsupportedPrivate = object : PrivateKey {
            override fun getAlgorithm() = "XDH"
            override fun getFormat() = null
            override fun getEncoded() = ByteArray(0)
        }

        val signature = AuthAgentForwardThread.generateSignature(
            KeyPair(unsupportedPublic, unsupportedPrivate),
            challenge,
            0,
        )

        assertThat(signature).isNull()
    }

    @Test
    fun signingFailure_returnsNullInsteadOfThrowing() {
        // An EC public key paired with a private key no provider can use
        // mimics a Keystore key whose authorization has lapsed: signing must
        // fail gracefully so the agent replies SSH_AGENT_FAILURE.
        val pair = generate("EC", 256)
        val brokenPrivate = object : PrivateKey {
            override fun getAlgorithm() = "EC"
            override fun getFormat() = null
            override fun getEncoded() = null
        }

        val signature = AuthAgentForwardThread.generateSignature(
            KeyPair(pair.public, brokenPrivate),
            challenge,
            0,
        )

        assertThat(signature).isNull()
    }
}
