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

package org.connectbot.fido2.ssh

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.connectbot.fido2.Fido2SignatureResult
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SkEd25519VerifyTest {

    companion object {
        private const val KEY_TYPE = "sk-ssh-ed25519@openssh.com"
        private const val ED25519_KEY_SIZE = 32
        private const val ED25519_SIG_SIZE = 64
    }

    @Test
    fun `encodePublicKey produces correct SSH wire format`() {
        val ed25519Key = ByteArray(ED25519_KEY_SIZE) { it.toByte() }
        val application = "ssh:"

        val pubkey = SkEd25519PublicKey(
            application = application,
            ed25519Key = ed25519Key
        )

        val encoded = SkEd25519Verify.encodePublicKey(pubkey)

        // Parse the encoded data
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)

        // Key type string
        val keyTypeLen = buffer.int
        val keyTypeBytes = ByteArray(keyTypeLen)
        buffer.get(keyTypeBytes)
        assertThat(String(keyTypeBytes)).isEqualTo(KEY_TYPE)

        // Ed25519 key
        val keyLen = buffer.int
        assertThat(keyLen).isEqualTo(ED25519_KEY_SIZE)
        val keyBytes = ByteArray(keyLen)
        buffer.get(keyBytes)
        assertThat(keyBytes).isEqualTo(ed25519Key)

        // Application
        val appLen = buffer.int
        val appBytes = ByteArray(appLen)
        buffer.get(appBytes)
        assertThat(String(appBytes)).isEqualTo(application)

        // No remaining bytes
        assertThat(buffer.hasRemaining()).isFalse()
    }

    @Test
    fun `decodePublicKey parses SSH wire format correctly`() {
        val ed25519Key = ByteArray(ED25519_KEY_SIZE) { (it * 2).toByte() }
        val application = "ssh:"

        // Build the encoded format manually
        val encoded = buildPublicKeyBlob(KEY_TYPE, ed25519Key, application)

        val decoded = SkEd25519Verify.decodePublicKey(encoded)

        assertThat(decoded.ed25519Key).isEqualTo(ed25519Key)
        assertThat(decoded.application).isEqualTo(application)
    }

    @Test
    fun `decodePublicKey handles stripped key type`() {
        val ed25519Key = ByteArray(ED25519_KEY_SIZE) { (it + 10).toByte() }
        val application = "ssh:"

        // Build without key type (as if already stripped by caller)
        val buffer = ByteBuffer.allocate(4 + ED25519_KEY_SIZE + 4 + application.length)
            .order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(ED25519_KEY_SIZE)
        buffer.put(ed25519Key)
        buffer.putInt(application.length)
        buffer.put(application.toByteArray())

        val decoded = SkEd25519Verify.decodePublicKey(buffer.array())

        assertThat(decoded.ed25519Key).isEqualTo(ed25519Key)
        assertThat(decoded.application).isEqualTo(application)
    }

    @Test
    fun `decodePublicKey throws on invalid key size`() {
        val invalidKey = ByteArray(16) { it.toByte() } // Wrong size
        val application = "ssh:"

        val encoded = buildPublicKeyBlob(KEY_TYPE, invalidKey, application)

        assertThatThrownBy { SkEd25519Verify.decodePublicKey(encoded) }
            .isInstanceOf(IOException::class.java)
            .hasMessageContaining("Invalid Ed25519 key size")
    }

    @Test
    fun `encodePublicKey and decodePublicKey are inverse operations`() {
        val ed25519Key = ByteArray(ED25519_KEY_SIZE) { (it * 3).toByte() }
        val application = "ssh:testhost"

        val original = SkEd25519PublicKey(
            application = application,
            ed25519Key = ed25519Key
        )

        val encoded = SkEd25519Verify.encodePublicKey(original)
        val decoded = SkEd25519Verify.decodePublicKey(encoded)

        assertThat(decoded.ed25519Key).isEqualTo(original.ed25519Key)
        assertThat(decoded.application).isEqualTo(original.application)
    }

    @Test
    fun `encodeSignature produces correct SSH signature format`() {
        val signature = ByteArray(ED25519_SIG_SIZE) { it.toByte() }
        val authenticatorData = ByteArray(37) // 32 bytes rpIdHash + 1 byte flags + 4 bytes counter
        authenticatorData[32] = 0x01 // UP flag

        val fido2Result = Fido2SignatureResult(
            authenticatorData = authenticatorData,
            signature = signature,
            userPresenceVerified = true,
            userVerified = false,
            counter = 12345
        )

        val encoded = SkEd25519Verify.encodeSignature(fido2Result)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)

        // Key type
        val keyTypeLen = buffer.int
        val keyTypeBytes = ByteArray(keyTypeLen)
        buffer.get(keyTypeBytes)
        assertThat(String(keyTypeBytes)).isEqualTo(KEY_TYPE)

        // Signature
        val sigLen = buffer.int
        assertThat(sigLen).isEqualTo(ED25519_SIG_SIZE)
        val sigBytes = ByteArray(sigLen)
        buffer.get(sigBytes)
        assertThat(sigBytes).isEqualTo(signature)

        // Flags
        val flags = buffer.get()
        assertThat(flags).isEqualTo(0x01.toByte())

        // Counter
        val counter = buffer.int
        assertThat(counter).isEqualTo(12345)

        // No remaining bytes
        assertThat(buffer.hasRemaining()).isFalse()
    }

    @Test
    fun `encodeSignature extracts Ed25519 signature from longer format`() {
        // Simulate a wrapped signature (longer than 64 bytes)
        val wrappedSignature = ByteArray(70) { (it + 100).toByte() }
        val expectedSignature = wrappedSignature.sliceArray(6 until 70) // Last 64 bytes

        val authenticatorData = ByteArray(37)
        authenticatorData[32] = 0x05 // UP + UV

        val fido2Result = Fido2SignatureResult(
            authenticatorData = authenticatorData,
            signature = wrappedSignature,
            userPresenceVerified = true,
            userVerified = true,
            counter = 99
        )

        val encoded = SkEd25519Verify.encodeSignature(fido2Result)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)

        // Skip key type
        val keyTypeLen = buffer.int
        buffer.position(buffer.position() + keyTypeLen)

        // Signature should be 64 bytes
        val sigLen = buffer.int
        assertThat(sigLen).isEqualTo(ED25519_SIG_SIZE)
        val sigBytes = ByteArray(sigLen)
        buffer.get(sigBytes)
        assertThat(sigBytes).isEqualTo(expectedSignature)
    }

    @Test
    fun `encodeSignature handles counter values correctly`() {
        val signature = ByteArray(ED25519_SIG_SIZE)
        val authenticatorData = ByteArray(37)

        // Test with max counter value
        val fido2Result = Fido2SignatureResult(
            authenticatorData = authenticatorData,
            signature = signature,
            userPresenceVerified = true,
            userVerified = false,
            counter = Int.MAX_VALUE
        )

        val encoded = SkEd25519Verify.encodeSignature(fido2Result)
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)

        // Skip to counter (key type + signature + flags)
        val keyTypeLen = buffer.int
        buffer.position(buffer.position() + keyTypeLen)
        val sigLen = buffer.int
        buffer.position(buffer.position() + sigLen + 1) // +1 for flags

        val counter = buffer.int
        assertThat(counter).isEqualTo(Int.MAX_VALUE)
    }

    private fun buildPublicKeyBlob(keyType: String, key: ByteArray, application: String): ByteArray {
        val keyTypeBytes = keyType.toByteArray()
        val appBytes = application.toByteArray()

        val buffer = ByteBuffer.allocate(
            4 + keyTypeBytes.size +
            4 + key.size +
            4 + appBytes.size
        ).order(ByteOrder.BIG_ENDIAN)

        buffer.putInt(keyTypeBytes.size)
        buffer.put(keyTypeBytes)
        buffer.putInt(key.size)
        buffer.put(key)
        buffer.putInt(appBytes.size)
        buffer.put(appBytes)

        return buffer.array()
    }
}
