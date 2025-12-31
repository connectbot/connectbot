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

package org.connectbot.fido2.transport

import android.nfc.Tag
import android.nfc.tech.IsoDep
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * NFC transport for CTAP2 communication with FIDO2 security keys.
 * Uses ISO 7816-4 APDUs over ISO-DEP (ISO 14443-4).
 */
class NfcTransport(private val tag: Tag) : Ctap2Transport {

    private var isoDep: IsoDep? = null
    private var _isConnected = false

    companion object {
        // FIDO2 NFC AID (Application Identifier)
        private val FIDO2_AID = byteArrayOf(
            0xA0.toByte(), 0x00, 0x00, 0x06, 0x47, 0x2F, 0x00, 0x01
        )

        // ISO 7816-4 instruction bytes
        private const val CLA_ISO7816 = 0x00.toByte()
        private const val CLA_CHAINING = 0x10.toByte()
        private const val INS_SELECT = 0xA4.toByte()
        private const val INS_NFCTAP_MSG = 0x10.toByte()
        private const val P1_SELECT_BY_DF_NAME = 0x04.toByte()
        private const val P2_NO_RESPONSE = 0x00.toByte()

        // Status words
        private const val SW_SUCCESS = 0x9000
        private const val SW_MORE_DATA_PREFIX = 0x61

        // Maximum APDU data size (conservative estimate)
        private const val MAX_APDU_SIZE = 255
    }

    override val transportType: String = "NFC"

    override val deviceName: String? = "Security Key"

    override val isConnected: Boolean
        get() = _isConnected && isoDep?.isConnected == true

    /**
     * Connect to the NFC tag and select the FIDO2 application.
     * @return true if connection was successful
     */
    suspend fun connect(): Boolean {
        return try {
            val iso = IsoDep.get(tag)
            if (iso == null) {
                Timber.e("Tag does not support IsoDep")
                return false
            }

            iso.connect()
            iso.timeout = 30000 // 30 second timeout for FIDO2 operations

            // Select FIDO2 application
            val selectCommand = buildSelectApdu(FIDO2_AID)
            val response = iso.transceive(selectCommand)
            val sw = getStatusWord(response)

            if (sw != SW_SUCCESS) {
                Timber.e("Failed to select FIDO2 application: SW=%04X", sw)
                iso.close()
                return false
            }

            isoDep = iso
            _isConnected = true
            Timber.d("NFC FIDO2 connection established")
            true
        } catch (e: IOException) {
            Timber.e(e, "Failed to connect to NFC tag")
            false
        }
    }

    override suspend fun sendCommand(command: ByteArray): ByteArray {
        val iso = isoDep ?: throw Ctap2TransportException("Not connected")

        // Send message using chaining if needed
        val response = sendWithChaining(iso, command)

        // Check for CTAP2 status
        if (response.isEmpty()) {
            throw Ctap2TransportException("Empty response from authenticator")
        }

        return response
    }

    override fun close() {
        try {
            isoDep?.close()
        } catch (e: IOException) {
            Timber.e(e, "Error closing NFC connection")
        } finally {
            isoDep = null
            _isConnected = false
        }
    }

    /**
     * Build an ISO 7816-4 SELECT command APDU.
     */
    private fun buildSelectApdu(aid: ByteArray): ByteArray {
        return byteArrayOf(
            CLA_ISO7816,
            INS_SELECT,
            P1_SELECT_BY_DF_NAME,
            P2_NO_RESPONSE,
            aid.size.toByte(),
            *aid,
            0x00 // Le = 0 (accept any length response)
        )
    }

    /**
     * Send a CTAP2 message, using command chaining if the message is too large.
     */
    private fun sendWithChaining(iso: IsoDep, data: ByteArray): ByteArray {
        var offset = 0
        var response: ByteArray? = null

        while (offset < data.size) {
            val remaining = data.size - offset
            val chunkSize = minOf(remaining, MAX_APDU_SIZE)
            val isLastChunk = (offset + chunkSize) >= data.size

            // Build APDU
            val cla = if (isLastChunk) CLA_ISO7816 else CLA_CHAINING
            val chunk = data.copyOfRange(offset, offset + chunkSize)
            val apdu = buildNfcTapApdu(cla, chunk)

            response = iso.transceive(apdu)
            val sw = getStatusWord(response)

            if (!isLastChunk) {
                // For chained commands, expect success with no data
                if (sw != SW_SUCCESS) {
                    throw Ctap2TransportException("Chaining failed: SW=%04X".format(sw))
                }
            }

            offset += chunkSize
        }

        // Handle response (may need to fetch more data)
        return handleResponse(iso, response ?: byteArrayOf())
    }

    /**
     * Build an NFCTAP_MSG APDU for sending CTAP2 commands.
     */
    private fun buildNfcTapApdu(cla: Byte, data: ByteArray): ByteArray {
        return byteArrayOf(
            cla,
            INS_NFCTAP_MSG,
            0x00, // P1
            0x00, // P2
            data.size.toByte(),
            *data,
            0x00 // Le = 0 (accept any length response)
        )
    }

    /**
     * Handle response, fetching additional data if indicated by SW=61XX.
     */
    private fun handleResponse(iso: IsoDep, initialResponse: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        var response = initialResponse

        while (true) {
            val sw = getStatusWord(response)
            val sw1 = (sw shr 8) and 0xFF
            val sw2 = sw and 0xFF

            // Extract data (everything except last 2 bytes which are status word)
            if (response.size > 2) {
                outputStream.write(response, 0, response.size - 2)
            }

            when {
                sw == SW_SUCCESS -> {
                    // All data received
                    break
                }
                sw1 == SW_MORE_DATA_PREFIX -> {
                    // More data available, fetch it with GET RESPONSE
                    val getResponseApdu = byteArrayOf(
                        CLA_ISO7816,
                        0xC0.toByte(), // INS: GET RESPONSE
                        0x00,
                        0x00,
                        sw2.toByte() // Le = number of bytes available
                    )
                    response = iso.transceive(getResponseApdu)
                }
                else -> {
                    throw Ctap2TransportException("NFC error: SW=%04X".format(sw))
                }
            }
        }

        return outputStream.toByteArray()
    }

    /**
     * Extract status word (SW1-SW2) from response.
     */
    private fun getStatusWord(response: ByteArray): Int {
        if (response.size < 2) {
            return 0
        }
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        return (sw1 shl 8) or sw2
    }
}
