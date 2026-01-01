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

package org.connectbot.fido2.ssh

import org.connectbot.fido2.Fido2SignatureResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SK-ECDSA signature encoding/decoding for SSH.
 *
 * Implements the sk-ecdsa-sha2-nistp256@openssh.com key type as defined in:
 * https://github.com/openssh/openssh-portable/blob/master/PROTOCOL.u2f
 *
 * Public key format:
 *   string    "sk-ecdsa-sha2-nistp256@openssh.com"
 *   string    curve name "nistp256"
 *   string    EC public key (uncompressed point: 0x04 || x || y)
 *   string    application (e.g., "ssh:")
 *
 * Signature format:
 *   string    "sk-ecdsa-sha2-nistp256@openssh.com"
 *   string    ecdsa_signature (DER-encoded r,s or SSH-format)
 *   byte      flags
 *   uint32    counter
 */
object SkEcdsaVerify {

    private const val KEY_TYPE = "sk-ecdsa-sha2-nistp256@openssh.com"
    private const val CURVE_NAME = "nistp256"

    /**
     * Encode an SK-ECDSA public key to SSH wire format.
     */
    fun encodePublicKey(key: SkEcdsaPublicKey): ByteArray {
        val baos = ByteArrayOutputStream()

        // Key type
        writeString(baos, KEY_TYPE)
        // Curve name
        writeString(baos, key.curve)
        // EC point
        writeBytes(baos, key.ecPoint)
        // Application
        writeString(baos, key.getApplication())

        return baos.toByteArray()
    }

    /**
     * Decode an SK-ECDSA public key from SSH wire format.
     */
    fun decodePublicKey(blob: ByteArray): SkEcdsaPublicKey {
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.BIG_ENDIAN)

        // Key type (already consumed by caller in most cases, but handle it if present)
        val firstString = readString(buffer)
        val curve: String
        val ecPoint: ByteArray
        val application: String

        if (firstString == KEY_TYPE) {
            // Full format with key type
            curve = readString(buffer)
            ecPoint = readBytes(buffer)
            application = readString(buffer)
        } else {
            // Key type was already stripped, firstString is curve name
            curve = firstString
            ecPoint = readBytes(buffer)
            application = readString(buffer)
        }

        return SkEcdsaPublicKey(
            application = application,
            ecPoint = ecPoint,
            curve = curve
        )
    }

    /**
     * Encode a FIDO2 signature result to SSH sk-ecdsa signature format.
     *
     * The SSH signature format for sk-ecdsa is:
     *   string    "sk-ecdsa-sha2-nistp256@openssh.com"
     *   string    ecdsa_signature_blob
     *   byte      flags
     *   uint32    counter
     *
     * Where ecdsa_signature_blob is:
     *   mpint     r
     *   mpint     s
     */
    fun encodeSignature(fido2Result: Fido2SignatureResult): ByteArray {
        val baos = ByteArrayOutputStream()

        // Key type
        writeString(baos, KEY_TYPE)

        // ECDSA signature blob (convert from DER to SSH format if needed)
        val sshSig = convertEcdsaSignatureToSsh(fido2Result.signature)
        writeBytes(baos, sshSig)

        // Flags byte
        baos.write(fido2Result.flags.toInt())

        // Counter (4 bytes, big endian)
        val counterBytes = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(fido2Result.counter)
            .array()
        baos.write(counterBytes)

        return baos.toByteArray()
    }

    /**
     * Convert ECDSA signature from DER format to SSH format.
     *
     * DER format: SEQUENCE { INTEGER r, INTEGER s }
     * SSH format: string { mpint r, mpint s }
     */
    private fun convertEcdsaSignatureToSsh(derSignature: ByteArray): ByteArray {
        try {
            val buffer = ByteBuffer.wrap(derSignature)

            // Parse DER SEQUENCE
            val tag = buffer.get().toInt() and 0xFF
            if (tag != 0x30) {
                // Not DER format, assume already in raw r||s format
                return convertRawEcdsaToSsh(derSignature)
            }

            val seqLen = readDerLength(buffer)

            // Parse r INTEGER
            val rTag = buffer.get().toInt() and 0xFF
            if (rTag != 0x02) throw IOException("Expected INTEGER tag for r")
            val rLen = readDerLength(buffer)
            val rBytes = ByteArray(rLen)
            buffer.get(rBytes)

            // Parse s INTEGER
            val sTag = buffer.get().toInt() and 0xFF
            if (sTag != 0x02) throw IOException("Expected INTEGER tag for s")
            val sLen = readDerLength(buffer)
            val sBytes = ByteArray(sLen)
            buffer.get(sBytes)

            // Convert to SSH format (mpint r, mpint s)
            val baos = ByteArrayOutputStream()
            writeMpint(baos, rBytes)
            writeMpint(baos, sBytes)

            return baos.toByteArray()
        } catch (e: Exception) {
            // If DER parsing fails, try raw format
            return convertRawEcdsaToSsh(derSignature)
        }
    }

    /**
     * Convert raw r||s format (64 bytes for P-256) to SSH mpint format.
     */
    private fun convertRawEcdsaToSsh(rawSignature: ByteArray): ByteArray {
        if (rawSignature.size != 64) {
            // Unknown format, return as-is
            return rawSignature
        }

        val r = rawSignature.sliceArray(0 until 32)
        val s = rawSignature.sliceArray(32 until 64)

        val baos = ByteArrayOutputStream()
        writeMpint(baos, r)
        writeMpint(baos, s)

        return baos.toByteArray()
    }

    private fun readDerLength(buffer: ByteBuffer): Int {
        val b = buffer.get().toInt() and 0xFF
        return if (b < 0x80) {
            b
        } else {
            val numBytes = b and 0x7F
            var length = 0
            for (i in 0 until numBytes) {
                length = (length shl 8) or (buffer.get().toInt() and 0xFF)
            }
            length
        }
    }

    private fun writeString(baos: ByteArrayOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        writeBytes(baos, bytes)
    }

    private fun writeBytes(baos: ByteArrayOutputStream, bytes: ByteArray) {
        val lenBytes = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(bytes.size)
            .array()
        baos.write(lenBytes)
        baos.write(bytes)
    }

    private fun writeMpint(baos: ByteArrayOutputStream, bytes: ByteArray) {
        // Remove leading zeros, but keep one if the number is zero
        var start = 0
        while (start < bytes.size - 1 && bytes[start] == 0.toByte()) {
            start++
        }

        // Add leading zero if high bit is set (to keep number positive)
        val needsPadding = (bytes[start].toInt() and 0x80) != 0
        val length = bytes.size - start + (if (needsPadding) 1 else 0)

        val lenBytes = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(length)
            .array()
        baos.write(lenBytes)

        if (needsPadding) {
            baos.write(0)
        }
        baos.write(bytes, start, bytes.size - start)
    }

    private fun readString(buffer: ByteBuffer): String {
        val bytes = readBytes(buffer)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readBytes(buffer: ByteBuffer): ByteArray {
        val length = buffer.int
        if (length < 0 || length > buffer.remaining()) {
            throw IOException("Invalid length: $length")
        }
        val bytes = ByteArray(length)
        buffer.get(bytes)
        return bytes
    }
}
