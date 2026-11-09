/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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

package org.connectbot.ctap2

import com.yubico.yubikit.core.fido.FidoConnection
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.io.IOException

class Ctap2ClientTest {
    @Test
    fun failedSessionInitializationClosesConnection() {
        val connection = mock(FidoConnection::class.java)
        val failure = IOException("device removed")
        doThrow(failure).`when`(connection).send(any(ByteArray::class.java))
        val thrown = assertThrows(IOException::class.java) { YubiKitCtap2Client.open(connection) }
        assertSame(failure, thrown)
        verify(connection).close()
    }

    @Test
    fun invalidClientDataHashDoesNotSendPinOrTouchSession() {
        val session = mock(com.yubico.yubikit.fido.ctap.Ctap2Session::class.java)
        val result = Ctap2Operations().getAssertion(session, "1234", byteArrayOf(1), ByteArray(31), "example.com")
        org.junit.Assert.assertTrue(result is Fido2Result.Error)
        org.mockito.Mockito.verifyNoInteractions(session)
    }

    @Test
    fun suppliedHashIsForwardedWithoutRehashing() {
        val info = mock(com.yubico.yubikit.fido.ctap.Ctap2Session.InfoData::class.java)
        org.mockito.Mockito.`when`(info.pinUvAuthProtocols).thenReturn(listOf(1))
        val assertion = mock(com.yubico.yubikit.fido.ctap.Ctap2Session.AssertionData::class.java)
        org.mockito.Mockito.`when`(assertion.authenticatorData).thenReturn(ByteArray(37))
        org.mockito.Mockito.`when`(assertion.signature).thenReturn(byteArrayOf(7))
        val expected = ByteArray(32) { it.toByte() }
        var received: ByteArray? = null
        val sessionSettings = org.mockito.Mockito.withSettings().defaultAnswer { call ->
            when (call.method.name) {
                "getCachedInfo" -> info

                "getAssertions" -> {
                    org.junit.Assert.assertEquals("example.com", call.getArgument<String>(0))
                    received = call.getArgument(1)
                    listOf(assertion)
                }

                else -> org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
            }
        }
        val pinSettings = org.mockito.Mockito.withSettings().defaultAnswer { call ->
            if (call.method.name == "getPinToken") {
                ByteArray(32) { 1 }
            } else {
                org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
            }
        }
        org.mockito.Mockito.mockConstruction(com.yubico.yubikit.fido.ctap.Ctap2Session::class.java, sessionSettings).use { sessions ->
            org.mockito.Mockito.mockConstruction(com.yubico.yubikit.fido.ctap.ClientPin::class.java, pinSettings).use {
                YubiKitCtap2Client.open(mock(FidoConnection::class.java)).use { client ->
                    val result = client.getAssertion("1234", byteArrayOf(1), "example.com", expected)
                    org.junit.Assert.assertTrue(result is Fido2Result.Success)
                    org.junit.Assert.assertArrayEquals(expected, received)
                }
                verify(sessions.constructed().single()).close()
            }
        }
    }
}
