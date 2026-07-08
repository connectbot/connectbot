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

import com.trilead.ssh2.auth.SignatureProxy
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import com.trilead.ssh2.signature.Ed25519Verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security

/**
 * Exercises [Ed25519SignatureProxy] with a JDK-provided Ed25519 key, which —
 * like an Android Keystore key — is not sshlib's software key type, so the
 * proxy has to route signing through the JCA Signature API.
 */
class Ed25519SignatureProxyTest {

    /**
     * Generate the pair with a provider that also offers an Ed25519 Signature.
     * Other tests insert sshlib's Ed25519Provider, which registers a
     * KeyPairGenerator but no Signature; keys from it can't be signed with by
     * the JDK's own implementation, which is not the situation the proxy is
     * for (opaque Keystore keys sign in their own provider).
     */
    private fun generateJdkKeyPair(): KeyPair {
        val provider = Security.getProviders("Signature.Ed25519").first()
        return KeyPairGenerator.getInstance("Ed25519", provider).generateKeyPair()
    }

    @Test
    fun publicKey_isConvertedToSshlibType() {
        val pair = generateJdkKeyPair()

        val proxy = Ed25519SignatureProxy(pair.public, pair.private)

        assertTrue(proxy.publicKey is Ed25519PublicKey)
    }

    @Test
    fun sign_producesVerifiableSshWireSignature() {
        val pair = generateJdkKeyPair()
        val proxy = Ed25519SignatureProxy(pair.public, pair.private)
        val message = "the quick brown fox".toByteArray()

        val sshSignature = proxy.sign(message, SignatureProxy.SHA512)

        assertTrue(Ed25519Verify.get().verifySignature(message, sshSignature, proxy.publicKey))
    }

    @Test
    fun sign_matchesSshlibSignatureEncoding() {
        val pair = generateJdkKeyPair()
        val proxy = Ed25519SignatureProxy(pair.public, pair.private)
        val message = "encoding check".toByteArray()

        val sshSignature = proxy.sign(message, SignatureProxy.SHA512)

        // string "ssh-ed25519" + string <64-byte signature>
        assertEquals(4 + 11 + 4 + 64, sshSignature.size)
        val format = String(sshSignature, 4, 11, Charsets.US_ASCII)
        assertEquals("ssh-ed25519", format)
    }
}
