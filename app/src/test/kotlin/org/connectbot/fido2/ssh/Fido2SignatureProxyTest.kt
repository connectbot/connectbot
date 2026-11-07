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

package org.connectbot.fido2.ssh

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.connectbot.data.entity.Fido2Transport
import org.connectbot.fido2.Fido2Algorithm
import org.connectbot.fido2.Fido2Manager
import org.connectbot.fido2.Fido2Result
import org.connectbot.fido2.Fido2SignatureResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.stubbing.Answer
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class Fido2SignatureProxyTest {
    @Test
    fun signsUsingConfiguredTransportAndEncodesAlgorithmSpecificSignatures() = runTest {
        var callback: ((Fido2Result<Fido2SignatureResult>) -> Unit)? = null
        var response: Fido2Result<Fido2SignatureResult> = Fido2Result.Error("not set")
        val manager = mock(
            Fido2Manager::class.java,
            Answer { call ->
                when (call.method.name) {
                    "prepareSshSigning" -> {
                        callback = call.getArgument(3)
                        null
                    }

                    "connectAndSignUsb", "requestNfcSigning" -> {
                        callback!!.invoke(response)
                        null
                    }

                    else -> Answers.RETURNS_DEFAULTS.answer(call)
                }
            },
        )
        for (transport in Fido2Transport.entries) {
            for (algorithm in Fido2Algorithm.entries) {
                val result = Fido2SignatureResult(ByteArray(37).apply { this[32] = 1 }, if (algorithm == Fido2Algorithm.EDDSA) ByteArray(64) else byteArrayOf(0x30, 6, 2, 1, 1, 2, 1, 2), true, false, 1)
                response = Fido2Result.Success(result)
                val key = if (algorithm == Fido2Algorithm.EDDSA) SkEd25519PublicKey("ssh:", ByteArray(32)) else SkEcdsaPublicKey("ssh:", ByteArray(65).apply { this[0] = 4 })
                val proxy = Fido2SignatureProxy(key, byteArrayOf(1), "ssh:", algorithm, "1234", transport, manager)
                val expected = if (algorithm == Fido2Algorithm.EDDSA) SkEd25519Verify.encodeSignature(result) else SkEcdsaVerify.encodeSignature(result)
                assertArrayEquals(expected, proxy.sign(byteArrayOf(2), "SHA-256"))
                for (error in listOf(Fido2Result.PinInvalid(null), Fido2Result.PinInvalid(3), Fido2Result.PinLocked("locked"), Fido2Result.Error("failed"), Fido2Result.PinRequired(null))) {
                    response = error
                    try {
                        proxy.sign(byteArrayOf(2), "SHA-256")
                        fail("Signing must report hardware failure")
                    } catch (e: IOException) {
                        assertTrue(e.message!!.startsWith("FIDO2"))
                    }
                }
            }
        }
    }

    @Test
    fun cancellingNfcWaitCancelsHardwareSigningRequest() = runTest {
        val manager = mock(Fido2Manager::class.java)
        val proxy = Fido2SignatureProxy(SkEd25519PublicKey("ssh:", ByteArray(32)), byteArrayOf(1), "ssh:", Fido2Algorithm.EDDSA, "1234", Fido2Transport.NFC, manager)
        val signing = async { proxy.sign(byteArrayOf(2), "SHA-256") }
        runCurrent()
        signing.cancel()
        runCurrent()
        verify(manager).cancelNfcSigning()
    }
}
