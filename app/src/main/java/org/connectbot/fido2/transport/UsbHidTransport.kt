/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root, Jeffrey Sharkey
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

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/**
 * USB HID transport for FIDO2/CTAP2 communication.
 *
 * Implements the CTAPHID protocol for communication with USB security keys.
 * See: https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-20210615.html#usb
 */
class UsbHidTransport(
    private val usbManager: UsbManager,
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val inEndpoint: UsbEndpoint,
    private val outEndpoint: UsbEndpoint
) : Ctap2Transport {

    override val transportType: String = "USB"
    override val deviceName: String? = device.productName

    private var channelId: Int = BROADCAST_CHANNEL
    private var _isConnected = true

    override val isConnected: Boolean
        get() = _isConnected

    /**
     * Initialize the CTAPHID channel.
     * Must be called before sending CTAP commands.
     */
    suspend fun initialize(): UsbHidTransport {
        channelId = allocateChannel()
        Timber.d("CTAPHID channel allocated: 0x${channelId.toString(16)}")
        return this
    }

    override suspend fun sendCommand(command: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        if (!_isConnected) {
            throw Ctap2TransportException("Transport is closed")
        }

        // Wrap CTAP command in CTAPHID CBOR message
        val response = sendCtapHidMessage(CTAPHID_CBOR, command)

        if (response.isEmpty()) {
            throw Ctap2TransportException("Empty response from authenticator")
        }

        response
    }

    override fun close() {
        _isConnected = false
        try {
            connection.releaseInterface(usbInterface)
            connection.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing USB connection")
        }
    }

    private suspend fun allocateChannel(): Int = withContext(Dispatchers.IO) {
        // Generate random nonce
        val nonce = ByteArray(8)
        Random.nextBytes(nonce)

        // Send INIT command on broadcast channel
        val response = sendCtapHidMessage(CTAPHID_INIT, nonce, BROADCAST_CHANNEL)

        if (response.size < 17) {
            throw Ctap2TransportException("Invalid INIT response size: ${response.size}")
        }

        // Verify nonce matches
        if (!response.sliceArray(0 until 8).contentEquals(nonce)) {
            throw Ctap2TransportException("INIT nonce mismatch")
        }

        // Extract channel ID (bytes 8-11, big endian)
        ByteBuffer.wrap(response, 8, 4).order(ByteOrder.BIG_ENDIAN).int
    }

    private suspend fun sendCtapHidMessage(
        cmd: Byte,
        data: ByteArray,
        channel: Int = channelId
    ): ByteArray = withContext(Dispatchers.IO) {
        val packetSize = outEndpoint.maxPacketSize.coerceAtMost(HID_PACKET_SIZE)

        // Build and send initialization packet
        val initPacket = buildInitPacket(channel, cmd, data, packetSize)
        sendPacket(initPacket)

        // Send continuation packets if needed
        var offset = packetSize - 7 // Data sent in init packet
        var seq = 0
        while (offset < data.size) {
            val contPacket = buildContPacket(channel, seq, data, offset, packetSize)
            sendPacket(contPacket)
            offset += packetSize - 5
            seq++
        }

        // Read response
        readResponse(channel)
    }

    private fun buildInitPacket(channel: Int, cmd: Byte, data: ByteArray, packetSize: Int): ByteArray {
        val packet = ByteArray(packetSize)
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        // Channel ID (4 bytes)
        buffer.putInt(channel)
        // Command with TYPE_INIT bit set (1 byte)
        buffer.put((cmd.toInt() or 0x80).toByte())
        // Data length (2 bytes, big endian)
        buffer.putShort(data.size.toShort())
        // Data payload (up to packetSize - 7 bytes)
        val payloadSize = minOf(data.size, packetSize - 7)
        buffer.put(data, 0, payloadSize)

        return packet
    }

    private fun buildContPacket(
        channel: Int,
        seq: Int,
        data: ByteArray,
        offset: Int,
        packetSize: Int
    ): ByteArray {
        val packet = ByteArray(packetSize)
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        // Channel ID (4 bytes)
        buffer.putInt(channel)
        // Sequence number (1 byte, 0x00-0x7F)
        buffer.put((seq and 0x7F).toByte())
        // Data payload
        val remaining = data.size - offset
        val payloadSize = minOf(remaining, packetSize - 5)
        buffer.put(data, offset, payloadSize)

        return packet
    }

    private fun sendPacket(packet: ByteArray) {
        val result = connection.bulkTransfer(outEndpoint, packet, packet.size, TIMEOUT_MS)
        if (result < 0) {
            throw Ctap2TransportException("USB bulk transfer failed: $result")
        }
    }

    private fun readResponse(expectedChannel: Int): ByteArray {
        val buffer = ByteArray(inEndpoint.maxPacketSize.coerceAtMost(HID_PACKET_SIZE))

        // Read initialization packet
        var bytesRead = connection.bulkTransfer(inEndpoint, buffer, buffer.size, TIMEOUT_MS)
        if (bytesRead < 7) {
            throw Ctap2TransportException("Failed to read response init packet: $bytesRead")
        }

        val responseBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN)

        // Verify channel ID
        val channel = responseBuffer.int
        if (channel != expectedChannel) {
            throw Ctap2TransportException("Channel mismatch: expected $expectedChannel, got $channel")
        }

        // Get command and verify it's an init packet
        val cmd = responseBuffer.get()
        if ((cmd.toInt() and 0x80) == 0) {
            throw Ctap2TransportException("Expected init packet, got continuation")
        }

        // Check for error
        if ((cmd.toInt() and 0x7F) == CTAPHID_ERROR.toInt()) {
            val errorCode = if (bytesRead > 7) buffer[7] else 0
            throw Ctap2TransportException("CTAPHID error: 0x${errorCode.toInt().and(0xFF).toString(16)}")
        }

        // Get data length
        val dataLength = responseBuffer.short.toInt() and 0xFFFF
        if (dataLength == 0) {
            return ByteArray(0)
        }

        // Read data from init packet
        val output = ByteArrayOutputStream(dataLength)
        val initDataSize = minOf(dataLength, buffer.size - 7)
        output.write(buffer, 7, initDataSize)

        // Read continuation packets if needed
        var remaining = dataLength - initDataSize
        var expectedSeq = 0

        while (remaining > 0) {
            bytesRead = connection.bulkTransfer(inEndpoint, buffer, buffer.size, TIMEOUT_MS)
            if (bytesRead < 5) {
                throw Ctap2TransportException("Failed to read continuation packet: $bytesRead")
            }

            responseBuffer.clear()

            // Verify channel
            val contChannel = responseBuffer.int
            if (contChannel != expectedChannel) {
                throw Ctap2TransportException("Channel mismatch in continuation")
            }

            // Verify sequence
            val seq = responseBuffer.get().toInt() and 0x7F
            if (seq != expectedSeq) {
                throw Ctap2TransportException("Sequence mismatch: expected $expectedSeq, got $seq")
            }
            expectedSeq++

            // Read data
            val contDataSize = minOf(remaining, buffer.size - 5)
            output.write(buffer, 5, contDataSize)
            remaining -= contDataSize
        }

        return output.toByteArray()
    }

    companion object {
        private const val HID_PACKET_SIZE = 64
        private const val TIMEOUT_MS = 5000
        private const val BROADCAST_CHANNEL = 0xFFFFFFFF.toInt()

        // CTAPHID commands
        private const val CTAPHID_PING: Byte = 0x01
        private const val CTAPHID_MSG: Byte = 0x03
        private const val CTAPHID_LOCK: Byte = 0x04
        private const val CTAPHID_INIT: Byte = 0x06
        private const val CTAPHID_WINK: Byte = 0x08
        private const val CTAPHID_CBOR: Byte = 0x10
        private const val CTAPHID_CANCEL: Byte = 0x11
        private const val CTAPHID_KEEPALIVE: Byte = 0x3B
        private const val CTAPHID_ERROR: Byte = 0x3F

        // FIDO HID usage page
        private const val FIDO_USAGE_PAGE = 0xF1D0
        private const val FIDO_USAGE = 0x01

        /**
         * Find FIDO2-compatible USB devices.
         */
        fun findFidoDevices(usbManager: UsbManager): List<UsbDevice> {
            return usbManager.deviceList.values.filter { device ->
                isFidoDevice(device)
            }
        }

        /**
         * Check if a USB device is a FIDO2 authenticator.
         */
        fun isFidoDevice(device: UsbDevice): Boolean {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_HID) {
                    // Check for FIDO usage page in HID descriptor
                    // Most FIDO devices report as HID class
                    return true
                }
            }
            return false
        }

        /**
         * Open a connection to a FIDO2 USB device.
         */
        fun open(usbManager: UsbManager, device: UsbDevice): UsbHidTransport? {
            // Find HID interface
            var hidInterface: UsbInterface? = null
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_HID) {
                    hidInterface = iface
                    break
                }
            }

            if (hidInterface == null) {
                Timber.w("No HID interface found on device ${device.productName}")
                return null
            }

            // Find endpoints
            var inEndpoint: UsbEndpoint? = null
            var outEndpoint: UsbEndpoint? = null

            for (i in 0 until hidInterface.endpointCount) {
                val endpoint = hidInterface.getEndpoint(i)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                    if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                        inEndpoint = endpoint
                    } else {
                        outEndpoint = endpoint
                    }
                }
            }

            if (inEndpoint == null || outEndpoint == null) {
                Timber.w("Missing HID endpoints on device ${device.productName}")
                return null
            }

            // Open connection
            val connection = usbManager.openDevice(device)
            if (connection == null) {
                Timber.w("Failed to open USB device ${device.productName}")
                return null
            }

            // Claim interface
            if (!connection.claimInterface(hidInterface, true)) {
                connection.close()
                Timber.w("Failed to claim HID interface on ${device.productName}")
                return null
            }

            return UsbHidTransport(
                usbManager = usbManager,
                device = device,
                connection = connection,
                usbInterface = hidInterface,
                inEndpoint = inEndpoint,
                outEndpoint = outEndpoint
            )
        }
    }
}
