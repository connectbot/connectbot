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

package org.connectbot.passkey

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.connectbot.sshlib.SshException
import org.connectbot.sshlib.sk.SkPublicKeyDecoder
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PasskeyDiscoveryTest {
    private val id = byteArrayOf(4, 5, 6)
    private val origin = "android:apk-key-hash:test"
    private fun key() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun response(
        request: String,
        key: KeyPair,
        credentialId: ByteArray = id,
        assertionOrigin: String = origin,
        rpId: String = PasskeySupport.RP_ID,
        flags: Byte = 0x1d,
        challenge: String = JSONObject(request).getString("challenge"),
        clientPrefix: String = "",
    ): String {
        val client = (
            clientPrefix + JSONObject().put("type", "webauthn.get").put("challenge", challenge).put("origin", assertionOrigin)
                .toString()
            ).toByteArray()
        val auth = MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray()) + byteArrayOf(flags) + ByteArray(4)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(key.private)
            update(auth)
            update(MessageDigest.getInstance("SHA-256").digest(client))
            sign()
        }
        return JSONObject().put("type", "public-key").put("rawId", PasskeySupport.encode(credentialId))
            .put(
                "response",
                JSONObject()
                    .put("clientDataJSON", PasskeySupport.encode(client))
                    .put("authenticatorData", PasskeySupport.encode(auth))
                    .put("signature", PasskeySupport.encode(signature)),
            )
            .toString()
    }

    @Test
    fun explicitDiscoveryUsesTwoFreshRequestsAndPinsSelectedCredential() = runTest {
        val signer = key()
        val requests = mutableListOf<JSONObject>()
        val imported = PasskeyDiscovery.discover(origin, UnconfinedTestDispatcher(testScheduler)) { options ->
            requests.add(JSONObject(options))
            response(options, signer)
        }
        assertEquals(2, requests.size)
        assertFalse(requests.first().has("allowCredentials"))
        assertEquals("required", requests.first().getString("userVerification"))
        assertEquals(PasskeySupport.RP_ID, requests.first().getString("rpId"))
        assertEquals(1, requests.last().getJSONArray("allowCredentials").length())
        assertEquals(PasskeySupport.encode(id), requests.last().getJSONArray("allowCredentials").getJSONObject(0).getString("id"))
        assertFalse(requests.first().getString("challenge") == requests.last().getString("challenge"))
        assertArrayEquals(id, imported.credentialId)
        val public = signer.public as ECPublicKey
        fun bytes(value: BigInteger): ByteArray {
            val raw = value.toByteArray().takeLast(32).toByteArray()
            return ByteArray(32 - raw.size) + raw
        }
        assertArrayEquals(byteArrayOf(4) + bytes(public.w.affineX) + bytes(public.w.affineY), SkPublicKeyDecoder.decode(imported.publicKey).rawKey)
    }

    @Test
    fun rejectsCredentialSwapAndDifferentSigningKey() = runTest {
        val signer = key()
        for (swapId in listOf(true, false)) {
            var count = 0
            try {
                PasskeyDiscovery.discover(origin, UnconfinedTestDispatcher(testScheduler)) { options ->
                    count++
                    response(
                        options,
                        if (count == 2 && !swapId) key() else signer,
                        if (count == 2 && swapId) byteArrayOf(8) else id,
                    )
                }
                fail("Mixed credentials must not import")
            } catch (_: IllegalArgumentException) {
                assertEquals(2, count)
            }
        }
    }

    @Test
    fun rejectsInvalidOriginRpChallengeOrVerificationBeforeSecondRequest() = runTest {
        val signer = key()
        for (invalid in 0..3) {
            var count = 0
            try {
                PasskeyDiscovery.discover(origin, UnconfinedTestDispatcher(testScheduler)) { options ->
                    count++
                    response(
                        options,
                        signer,
                        assertionOrigin = if (invalid == 0) "https://foreign.example" else origin,
                        rpId = if (invalid == 1) "foreign.example" else PasskeySupport.RP_ID,
                        flags = if (invalid == 2) 1 else 5,
                        challenge = if (invalid == 3) PasskeySupport.encode(byteArrayOf(9)) else JSONObject(options).getString("challenge"),
                    )
                }
                fail("Invalid assertions must not import")
            } catch (_: Exception) {
                assertEquals(1, count)
            }
        }
    }

    @Test
    fun rejectsIncompatibleSignedJsonWithoutRewritingIt() = runTest {
        var calls = 0
        val signer = key()
        try {
            PasskeyDiscovery.discover(origin, UnconfinedTestDispatcher(testScheduler)) { options ->
                calls++
                response(options, signer, clientPrefix = " ")
            }
            fail("OpenSSH-incompatible JSON must not import")
        } catch (_: SshException) {
            assertEquals(1, calls)
        }
    }

    @Test
    fun cancellationDoesNotIssueMoreRequests() = runTest {
        val signer = key()
        for (cancelAt in 1..2) {
            var count = 0
            try {
                PasskeyDiscovery.discover(origin, UnconfinedTestDispatcher(testScheduler)) { options ->
                    count++
                    if (count == cancelAt) throw CancellationException("cancelled")
                    response(options, signer)
                }
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
                assertEquals(cancelAt, count)
            }
        }
    }
}
