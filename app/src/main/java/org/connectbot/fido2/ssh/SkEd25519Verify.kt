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
 * SK-Ed25519 signature encoding/decoding for SSH.
 *
 * Implements the sk-ssh-ed25519@openssh.com key type as defined in:
 * https://github.com/openssh/openssh-portable/blob/master/PROTOCOL.u2f
 *
 * Public key format:
 *   string    "sk-ssh-ed25519@openssh.com"
 *   string    Ed25519 public key (32 bytes)
 *   string    application (e.g., "ssh:")
 *
 * Signature format:
 *   string    "sk-ssh-ed25519@openssh.com"
 *   string    ed25519_signature (64 bytes)
 *   byte      flags
 *   uint32    counter
 */
object SkEd25519Verify {

    private const val KEY_TYPE = "sk-ssh-ed25519@openssh.com"
    private const val ED25519_KEY_SIZE = 32
    private const val ED25519_SIG_SIZE = 64

    /**
     * Encode an SK-Ed25519 public key to SSH wire format.
     */
    fun encodePublicKey(key: SkEd25519PublicKey): ByteArray {
        val baos = ByteArrayOutputStream()

        // Key type
        writeString(baos, KEY_TYPE)
        // Ed25519 public key
        writeBytes(baos, key.ed25519Key)
        // Application
        writeString(baos, key.getApplication())

        return baos.toByteArray()
    }

    /**
     * Decode an SK-Ed25519 public key from SSH wire format.
     */
    fun decodePublicKey(blob: ByteArray): SkEd25519PublicKey {
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.BIG_ENDIAN)

        // Key type (may already be consumed by caller)
        val firstBytes = readBytes(buffer)
        val firstString = String(firstBytes, Charsets.UTF_8)

        val ed25519Key: ByteArray
        val application: String

        if (firstString == KEY_TYPE) {
            // Full format with key type
            ed25519Key = readBytes(buffer)
            application = readString(buffer)
        } else if (firstBytes.size == ED25519_KEY_SIZE) {
            // Key type was already stripped, firstBytes is the Ed25519 key
            ed25519Key = firstBytes
            application = readString(buffer)
        } else {
            throw IOException("Invalid SK-Ed25519 public key format")
        }

        if (ed25519Key.size != ED25519_KEY_SIZE) {
            throw IOException("Invalid Ed25519 key size: ${ed25519Key.size}")
        }

        return SkEd25519PublicKey(
            application = application,
            ed25519Key = ed25519Key
        )
    }

    /**
     * Encode a FIDO2 signature result to SSH sk-ed25519 signature format.
     *
     * The SSH signature format for sk-ed25519 is:
     *   string    "sk-ssh-ed25519@openssh.com"
     *   string    ed25519_signature (64 bytes)
     *   byte      flags
     *   uint32    counter
     */
    fun encodeSignature(fido2Result: Fido2SignatureResult): ByteArray {
        val baos = ByteArrayOutputStream()

        // Key type
        writeString(baos, KEY_TYPE)

        // Ed25519 signature (should be 64 bytes)
        val signature = if (fido2Result.signature.size == ED25519_SIG_SIZE) {
            fido2Result.signature
        } else {
            // Try to extract 64-byte signature if it's in a different format
            extractEd25519Signature(fido2Result.signature)
        }
        writeBytes(baos, signature)

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
     * Extract Ed25519 signature from potentially wrapped format.
     */
    private fun extractEd25519Signature(signature: ByteArray): ByteArray {
        // Ed25519 signatures are always 64 bytes
        // If the signature is longer, it might be wrapped
        if (signature.size >= ED25519_SIG_SIZE) {
            // Take the last 64 bytes (common for COSE-wrapped signatures)
            return signature.sliceArray(signature.size - ED25519_SIG_SIZE until signature.size)
        }
        // Return as-is and let SSH server handle any errors
        return signature
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
