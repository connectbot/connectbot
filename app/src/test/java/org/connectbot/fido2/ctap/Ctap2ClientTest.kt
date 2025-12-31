/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

package org.connectbot.fido2.ctap

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.UnsignedInteger
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.fido2.Fido2Result
import org.connectbot.fido2.transport.Ctap2Commands
import org.connectbot.fido2.transport.Ctap2Status
import org.connectbot.fido2.transport.Ctap2Transport
import org.connectbot.fido2.transport.Ctap2TransportException
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import timber.log.Timber
import java.io.ByteArrayOutputStream

class Ctap2ClientTest {

    private lateinit var transport: Ctap2Transport
    private lateinit var client: Ctap2Client

    @Before
    fun setUp() {
        // Plant a no-op Timber tree for tests
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                // No-op
            }
        })

        transport = mock()
        client = Ctap2Client(transport)
    }

    @Test
    fun `getInfo sends correct command byte`() = runTest {
        // Setup mock to return a minimal valid response
        val response = buildGetInfoResponse(
            versions = listOf("FIDO_2_0"),
            pinConfigured = false
        )
        whenever(transport.sendCommand(any())).thenReturn(response)

        client.getInfo()

        val captor = argumentCaptor<ByteArray>()
        verify(transport).sendCommand(captor.capture())

        // Command should be just the GET_INFO byte (0x04)
        assertThat(captor.firstValue).hasSize(1)
        assertThat(captor.firstValue[0]).isEqualTo(Ctap2Commands.GET_INFO)
    }

    @Test
    fun `getInfo returns Success with authenticator info`() = runTest {
        val response = buildGetInfoResponse(
            versions = listOf("FIDO_2_0", "FIDO_2_1"),
            aaguid = ByteArray(16) { it.toByte() },
            pinConfigured = true,
            credentialManagementSupported = true,
            residentKeySupported = true
        )
        whenever(transport.sendCommand(any())).thenReturn(response)

        val result = client.getInfo()

        assertThat(result).isInstanceOf(Fido2Result.Success::class.java)
        val info = (result as Fido2Result.Success).value

        assertThat(info.versions).containsExactly("FIDO_2_0", "FIDO_2_1")
        assertThat(info.aaguid).isEqualTo(ByteArray(16) { it.toByte() })
        assertThat(info.pinConfigured).isTrue()
        assertThat(info.credentialManagementSupported).isTrue()
        assertThat(info.residentKeySupported).isTrue()
    }

    @Test
    fun `getInfo returns Error on empty response`() = runTest {
        whenever(transport.sendCommand(any())).thenReturn(byteArrayOf())

        val result = client.getInfo()

        assertThat(result).isInstanceOf(Fido2Result.Error::class.java)
        assertThat((result as Fido2Result.Error).message).isEqualTo("Empty response")
    }

    @Test
    fun `getInfo returns Error on non-OK status`() = runTest {
        // Return error status with no data
        val response = byteArrayOf(Ctap2Status.INVALID_COMMAND)
        whenever(transport.sendCommand(any())).thenReturn(response)

        val result = client.getInfo()

        assertThat(result).isInstanceOf(Fido2Result.Error::class.java)
        assertThat((result as Fido2Result.Error).message).contains("Invalid command")
    }

    @Test
    fun `getInfo returns Error on transport exception`() = runTest {
        whenever(transport.sendCommand(any()))
            .thenAnswer { throw Ctap2TransportException("Connection lost") }

        val result = client.getInfo()

        assertThat(result).isInstanceOf(Fido2Result.Error::class.java)
        assertThat((result as Fido2Result.Error).message).contains("Connection lost")
    }

    @Test
    fun `getInfo parses minimal response correctly`() = runTest {
        // Minimal response with just versions
        val response = buildGetInfoResponse(
            versions = listOf("FIDO_2_0"),
            pinConfigured = false
        )
        whenever(transport.sendCommand(any())).thenReturn(response)

        val result = client.getInfo()

        assertThat(result).isInstanceOf(Fido2Result.Success::class.java)
        val info = (result as Fido2Result.Success).value

        assertThat(info.versions).containsExactly("FIDO_2_0")
        assertThat(info.pinConfigured).isFalse()
        assertThat(info.aaguid).isNull()
    }

    @Test
    fun `getInfo handles PIN_NOT_SET status`() = runTest {
        val response = byteArrayOf(Ctap2Status.PIN_NOT_SET)
        whenever(transport.sendCommand(any())).thenReturn(response)

        val result = client.getInfo()

        assertThat(result).isInstanceOf(Fido2Result.Error::class.java)
        assertThat((result as Fido2Result.Error).message).contains("PIN not set")
    }

    @Test
    fun `getInfo handles PIN_BLOCKED status`() = runTest {
        val response = byteArrayOf(Ctap2Status.PIN_BLOCKED)
        whenever(transport.sendCommand(any())).thenReturn(response)

        val result = client.getInfo()

        assertThat(result).isInstanceOf(Fido2Result.Error::class.java)
        assertThat((result as Fido2Result.Error).message).contains("PIN blocked")
    }

    /**
     * Build a mock GetInfo response with CBOR-encoded data.
     */
    private fun buildGetInfoResponse(
        versions: List<String>,
        aaguid: ByteArray? = null,
        pinConfigured: Boolean = false,
        credentialManagementSupported: Boolean = false,
        residentKeySupported: Boolean = false
    ): ByteArray {
        val baos = ByteArrayOutputStream()

        // Status byte (success)
        baos.write(Ctap2Status.OK.toInt())

        // CBOR map
        val builder = CborBuilder()
        val mapBuilder = builder.addMap()

        // 0x01: versions (array of strings)
        val versionsArray = co.nstant.`in`.cbor.model.Array()
        versions.forEach { versionsArray.add(co.nstant.`in`.cbor.model.UnicodeString(it)) }
        mapBuilder.put(UnsignedInteger(0x01), versionsArray)

        // 0x02: extensions (optional, skip for now)

        // 0x03: aaguid (16 bytes)
        if (aaguid != null) {
            mapBuilder.put(UnsignedInteger(0x03), ByteString(aaguid))
        }

        // 0x04: options (map)
        val optionsMap = co.nstant.`in`.cbor.model.Map()
        if (residentKeySupported) {
            optionsMap.put(
                co.nstant.`in`.cbor.model.UnicodeString("rk"),
                co.nstant.`in`.cbor.model.SimpleValue.TRUE
            )
        }
        if (pinConfigured) {
            optionsMap.put(
                co.nstant.`in`.cbor.model.UnicodeString("clientPin"),
                co.nstant.`in`.cbor.model.SimpleValue.TRUE
            )
        }
        if (credentialManagementSupported) {
            optionsMap.put(
                co.nstant.`in`.cbor.model.UnicodeString("credMgmt"),
                co.nstant.`in`.cbor.model.SimpleValue.TRUE
            )
        }
        if (optionsMap.keys.isNotEmpty()) {
            mapBuilder.put(UnsignedInteger(0x04), optionsMap)
        }

        mapBuilder.end()

        // Encode CBOR
        val cborBaos = ByteArrayOutputStream()
        CborEncoder(cborBaos).encode(builder.build())
        baos.write(cborBaos.toByteArray())

        return baos.toByteArray()
    }
}
