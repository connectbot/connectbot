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

import java.io.Closeable

/**
 * Interface for CTAP2 transport layer communication with FIDO2 authenticators.
 *
 * Implementations handle the transport-specific framing (USB HID or NFC APDU)
 * while exposing a simple command/response interface for CTAP2 messages.
 */
interface Ctap2Transport : Closeable {

    /**
     * Send a CTAP2 command and receive the response.
     *
     * @param command The CTAP2 command byte followed by CBOR-encoded parameters.
     *                The first byte is the CTAP command code (0x01-0x0C).
     * @return The CTAP2 response: status byte followed by optional CBOR-encoded data.
     *         Status 0x00 indicates success.
     * @throws Ctap2TransportException if communication fails
     */
    suspend fun sendCommand(command: ByteArray): ByteArray

    /**
     * Get the transport type identifier.
     */
    val transportType: String

    /**
     * Get a human-readable device name if available.
     */
    val deviceName: String?

    /**
     * Check if the transport is still connected.
     */
    val isConnected: Boolean
}

/**
 * Exception thrown when CTAP2 transport communication fails.
 */
class Ctap2TransportException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * CTAP2 command codes.
 */
object Ctap2Commands {
    const val MAKE_CREDENTIAL: Byte = 0x01
    const val GET_ASSERTION: Byte = 0x02
    const val GET_INFO: Byte = 0x04
    const val CLIENT_PIN: Byte = 0x06
    const val RESET: Byte = 0x07
    const val GET_NEXT_ASSERTION: Byte = 0x08
    const val CREDENTIAL_MANAGEMENT: Byte = 0x0A
    const val SELECTION: Byte = 0x0B
    const val LARGE_BLOBS: Byte = 0x0C
    const val CONFIG: Byte = 0x0D
}

/**
 * CTAP2 status codes.
 */
object Ctap2Status {
    const val OK: Byte = 0x00
    const val INVALID_COMMAND: Byte = 0x01
    const val INVALID_PARAMETER: Byte = 0x02
    const val INVALID_LENGTH: Byte = 0x03
    const val INVALID_SEQ: Byte = 0x04
    const val TIMEOUT: Byte = 0x05
    const val CHANNEL_BUSY: Byte = 0x06
    const val LOCK_REQUIRED: Byte = 0x0A
    const val INVALID_CHANNEL: Byte = 0x0B
    const val CBOR_UNEXPECTED_TYPE: Byte = 0x11
    const val INVALID_CBOR: Byte = 0x12
    const val MISSING_PARAMETER: Byte = 0x14
    const val LIMIT_EXCEEDED: Byte = 0x15
    const val UNSUPPORTED_EXTENSION: Byte = 0x16
    const val CREDENTIAL_EXCLUDED: Byte = 0x19
    const val PROCESSING: Byte = 0x21
    const val INVALID_CREDENTIAL: Byte = 0x22
    const val USER_ACTION_PENDING: Byte = 0x23
    const val OPERATION_PENDING: Byte = 0x24
    const val NO_OPERATIONS: Byte = 0x25
    const val UNSUPPORTED_ALGORITHM: Byte = 0x26
    const val OPERATION_DENIED: Byte = 0x27
    const val KEY_STORE_FULL: Byte = 0x28
    const val NOT_BUSY: Byte = 0x29
    const val NO_OPERATION_PENDING: Byte = 0x2A
    const val UNSUPPORTED_OPTION: Byte = 0x2B
    const val INVALID_OPTION: Byte = 0x2C
    const val KEEPALIVE_CANCEL: Byte = 0x2D
    const val NO_CREDENTIALS: Byte = 0x2E
    const val USER_ACTION_TIMEOUT: Byte = 0x2F
    const val NOT_ALLOWED: Byte = 0x30
    const val PIN_INVALID: Byte = 0x31
    const val PIN_BLOCKED: Byte = 0x32
    const val PIN_AUTH_INVALID: Byte = 0x33
    const val PIN_AUTH_BLOCKED: Byte = 0x34
    const val PIN_NOT_SET: Byte = 0x35
    const val PIN_REQUIRED: Byte = 0x36
    const val PIN_POLICY_VIOLATION: Byte = 0x37
    const val PIN_TOKEN_EXPIRED: Byte = 0x38
    const val REQUEST_TOO_LARGE: Byte = 0x39
    const val ACTION_TIMEOUT: Byte = 0x3A
    const val UP_REQUIRED: Byte = 0x3B
    const val UV_BLOCKED: Byte = 0x3C
    const val INTEGRITY_FAILURE: Byte = 0x3D
    const val INVALID_SUBCOMMAND: Byte = 0x3E
    const val UV_INVALID: Byte = 0x3F
    const val UNAUTHORIZED_PERMISSION: Byte = 0x40

    fun getMessage(status: Byte): String = when (status) {
        OK -> "Success"
        INVALID_COMMAND -> "Invalid command"
        INVALID_PARAMETER -> "Invalid parameter"
        INVALID_LENGTH -> "Invalid length"
        INVALID_SEQ -> "Invalid sequence"
        TIMEOUT -> "Timeout"
        CHANNEL_BUSY -> "Channel busy"
        LOCK_REQUIRED -> "Lock required"
        INVALID_CHANNEL -> "Invalid channel"
        CBOR_UNEXPECTED_TYPE -> "Unexpected CBOR type"
        INVALID_CBOR -> "Invalid CBOR"
        MISSING_PARAMETER -> "Missing parameter"
        LIMIT_EXCEEDED -> "Limit exceeded"
        UNSUPPORTED_EXTENSION -> "Unsupported extension"
        CREDENTIAL_EXCLUDED -> "Credential excluded"
        PROCESSING -> "Processing"
        INVALID_CREDENTIAL -> "Invalid credential"
        USER_ACTION_PENDING -> "User action pending"
        OPERATION_PENDING -> "Operation pending"
        NO_OPERATIONS -> "No operations"
        UNSUPPORTED_ALGORITHM -> "Unsupported algorithm"
        OPERATION_DENIED -> "Operation denied"
        KEY_STORE_FULL -> "Key store full"
        NO_CREDENTIALS -> "No credentials"
        USER_ACTION_TIMEOUT -> "User action timeout"
        NOT_ALLOWED -> "Not allowed"
        PIN_INVALID -> "PIN invalid"
        PIN_BLOCKED -> "PIN blocked"
        PIN_AUTH_INVALID -> "PIN auth invalid"
        PIN_AUTH_BLOCKED -> "PIN auth blocked"
        PIN_NOT_SET -> "PIN not set"
        PIN_REQUIRED -> "PIN required"
        PIN_POLICY_VIOLATION -> "PIN policy violation"
        PIN_TOKEN_EXPIRED -> "PIN token expired"
        REQUEST_TOO_LARGE -> "Request too large"
        ACTION_TIMEOUT -> "Action timeout"
        UP_REQUIRED -> "User presence required"
        UV_BLOCKED -> "User verification blocked"
        else -> "Unknown error (0x${status.toInt().and(0xFF).toString(16)})"
    }
}
