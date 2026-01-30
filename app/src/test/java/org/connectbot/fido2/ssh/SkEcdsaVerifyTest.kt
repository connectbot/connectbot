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
import org.connectbot.fido2.Fido2SignatureResult
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SkEcdsaVerifyTest {

    companion object {
        private const val KEY_TYPE = "sk-ecdsa-sha2-nistp256@openssh.com"
        private const val CURVE_NAME = "nistp256"
        private const val EC_POINT_SIZE = 65 // 0x04 + 32 bytes X + 32 bytes Y
    }

    @Test
    fun `encodePublicKey produces correct SSH wire format`() {
        // Uncompressed EC point: 0x04 || X (32 bytes) || Y (32 bytes)
        val ecPoint = ByteArray(EC_POINT_SIZE)
        ecPoint[0] = 0x04
        for (i in 1 until EC_POINT_SIZE) {
            ecPoint[i] = i.toByte()
        }
        val application = "ssh:"

        val pubkey = SkEcdsaPublicKey(
            application = application,
            ecPoint = ecPoint,
            curve = CURVE_NAME
        )

        val encoded = SkEcdsaVerify.encodePublicKey(pubkey)

        // Parse the encoded data
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)

        // Key type string
        val keyTypeLen = buffer.int
        val keyTypeBytes = ByteArray(keyTypeLen)
        buffer.get(keyTypeBytes)
        assertThat(String(keyTypeBytes)).isEqualTo(KEY_TYPE)

        // Curve name
        val curveLen = buffer.int
        val curveBytes = ByteArray(curveLen)
        buffer.get(curveBytes)
        assertThat(String(curveBytes)).isEqualTo(CURVE_NAME)

        // EC point
        val pointLen = buffer.int
        assertThat(pointLen).isEqualTo(EC_POINT_SIZE)
        val pointBytes = ByteArray(pointLen)
        buffer.get(pointBytes)
        assertThat(pointBytes).isEqualTo(ecPoint)

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
        val ecPoint = ByteArray(EC_POINT_SIZE)
        ecPoint[0] = 0x04
        for (i in 1 until EC_POINT_SIZE) {
            ecPoint[i] = (i * 2).toByte()
        }
        val application = "ssh:"

        // Build the encoded format manually
        val encoded = buildPublicKeyBlob(KEY_TYPE, CURVE_NAME, ecPoint, application)

        val decoded = SkEcdsaVerify.decodePublicKey(encoded)

        assertThat(decoded.ecPoint).isEqualTo(ecPoint)
        assertThat(decoded.curve).isEqualTo(CURVE_NAME)
        assertThat(decoded.application).isEqualTo(application)
    }

    @Test
    fun `decodePublicKey handles stripped key type`() {
        val ecPoint = ByteArray(EC_POINT_SIZE)
        ecPoint[0] = 0x04
        val application = "ssh:"

        // Build without key type (as if already stripped by caller)
        val curveBytes = CURVE_NAME.toByteArray()
        val appBytes = application.toByteArray()
        val buffer = ByteBuffer.allocate(
            4 + curveBytes.size +
                4 + ecPoint.size +
                4 + appBytes.size
        ).order(ByteOrder.BIG_ENDIAN)

        buffer.putInt(curveBytes.size)
        buffer.put(curveBytes)
        buffer.putInt(ecPoint.size)
        buffer.put(ecPoint)
        buffer.putInt(appBytes.size)
        buffer.put(appBytes)

        val decoded = SkEcdsaVerify.decodePublicKey(buffer.array())

        assertThat(decoded.ecPoint).isEqualTo(ecPoint)
        assertThat(decoded.curve).isEqualTo(CURVE_NAME)
        assertThat(decoded.application).isEqualTo(application)
    }

    @Test
    fun `encodePublicKey and decodePublicKey are inverse operations`() {
        val ecPoint = ByteArray(EC_POINT_SIZE)
        ecPoint[0] = 0x04
        for (i in 1 until EC_POINT_SIZE) {
            ecPoint[i] = (i * 3).toByte()
        }
        val application = "ssh:testhost"

        val original = SkEcdsaPublicKey(
            application = application,
            ecPoint = ecPoint,
            curve = CURVE_NAME
        )

        val encoded = SkEcdsaVerify.encodePublicKey(original)
        val decoded = SkEcdsaVerify.decodePublicKey(encoded)

        assertThat(decoded.ecPoint).isEqualTo(original.ecPoint)
        assertThat(decoded.curve).isEqualTo(original.curve)
        assertThat(decoded.application).isEqualTo(original.application)
    }

    @Test
    fun `encodeSignature with raw signature format produces correct output`() {
        // Raw ECDSA signature: r (32 bytes) || s (32 bytes)
        val rawSignature = ByteArray(64)
        for (i in 0 until 32) {
            rawSignature[i] = (i + 1).toByte() // r
            rawSignature[i + 32] = (i + 33).toByte() // s
        }

        val authenticatorData = ByteArray(37)
        authenticatorData[32] = 0x01 // UP flag

        val fido2Result = Fido2SignatureResult(
            authenticatorData = authenticatorData,
            signature = rawSignature,
            userPresenceVerified = true,
            userVerified = false,
            counter = 42
        )

        val encoded = SkEcdsaVerify.encodeSignature(fido2Result)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)

        // Key type
        val keyTypeLen = buffer.int
        val keyTypeBytes = ByteArray(keyTypeLen)
        buffer.get(keyTypeBytes)
        assertThat(String(keyTypeBytes)).isEqualTo(KEY_TYPE)

        // Signature blob (should be SSH mpint format)
        val sigLen = buffer.int
        assertThat(sigLen).isGreaterThan(0)
        val sigBytes = ByteArray(sigLen)
        buffer.get(sigBytes)

        // Flags
        val flags = buffer.get()
        assertThat(flags).isEqualTo(0x01.toByte())

        // Counter
        val counter = buffer.int
        assertThat(counter).isEqualTo(42)

        // No remaining bytes
        assertThat(buffer.hasRemaining()).isFalse()
    }

    @Test
    fun `encodeSignature with DER signature format produces correct output`() {
        // DER-encoded ECDSA signature
        // SEQUENCE { INTEGER r, INTEGER s }
        val r = ByteArray(32) { (it + 1).toByte() }
        val s = ByteArray(32) { (it + 33).toByte() }
        val derSignature = buildDerSignature(r, s)

        val authenticatorData = ByteArray(37)
        authenticatorData[32] = 0x05 // UP + UV flags

        val fido2Result = Fido2SignatureResult(
            authenticatorData = authenticatorData,
            signature = derSignature,
            userPresenceVerified = true,
            userVerified = true,
            counter = 100
        )

        val encoded = SkEcdsaVerify.encodeSignature(fido2Result)

        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)

        // Key type
        val keyTypeLen = buffer.int
        val keyTypeBytes = ByteArray(keyTypeLen)
        buffer.get(keyTypeBytes)
        assertThat(String(keyTypeBytes)).isEqualTo(KEY_TYPE)

        // Signature blob
        val sigLen = buffer.int
        assertThat(sigLen).isGreaterThan(0)
        buffer.position(buffer.position() + sigLen)

        // Flags
        val flags = buffer.get()
        assertThat(flags).isEqualTo(0x05.toByte())

        // Counter
        val counter = buffer.int
        assertThat(counter).isEqualTo(100)
    }

    @Test
    fun `encodeSignature handles various counter values`() {
        val signature = ByteArray(64)
        val authenticatorData = ByteArray(37)

        // Test with zero counter
        val result1 = Fido2SignatureResult(
            authenticatorData = authenticatorData,
            signature = signature,
            userPresenceVerified = true,
            userVerified = false,
            counter = 0
        )
        val encoded1 = SkEcdsaVerify.encodeSignature(result1)
        assertThat(extractCounter(encoded1)).isEqualTo(0)

        // Test with max counter
        val result2 = Fido2SignatureResult(
            authenticatorData = authenticatorData,
            signature = signature,
            userPresenceVerified = true,
            userVerified = false,
            counter = Int.MAX_VALUE
        )
        val encoded2 = SkEcdsaVerify.encodeSignature(result2)
        assertThat(extractCounter(encoded2)).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `encodeSignature preserves all flag combinations`() {
        val signature = ByteArray(64)

        // UP only
        val authData1 = ByteArray(37)
        authData1[32] = 0x01
        val result1 = Fido2SignatureResult(
            authenticatorData = authData1,
            signature = signature,
            userPresenceVerified = true,
            userVerified = false,
            counter = 1
        )
        assertThat(extractFlags(SkEcdsaVerify.encodeSignature(result1))).isEqualTo(0x01.toByte())

        // UV only
        val authData2 = ByteArray(37)
        authData2[32] = 0x04
        val result2 = Fido2SignatureResult(
            authenticatorData = authData2,
            signature = signature,
            userPresenceVerified = false,
            userVerified = true,
            counter = 1
        )
        assertThat(extractFlags(SkEcdsaVerify.encodeSignature(result2))).isEqualTo(0x04.toByte())

        // Both UP and UV
        val authData3 = ByteArray(37)
        authData3[32] = 0x05
        val result3 = Fido2SignatureResult(
            authenticatorData = authData3,
            signature = signature,
            userPresenceVerified = true,
            userVerified = true,
            counter = 1
        )
        assertThat(extractFlags(SkEcdsaVerify.encodeSignature(result3))).isEqualTo(0x05.toByte())
    }

    private fun buildPublicKeyBlob(
        keyType: String,
        curveName: String,
        ecPoint: ByteArray,
        application: String
    ): ByteArray {
        val keyTypeBytes = keyType.toByteArray()
        val curveBytes = curveName.toByteArray()
        val appBytes = application.toByteArray()

        val buffer = ByteBuffer.allocate(
            4 + keyTypeBytes.size +
                4 + curveBytes.size +
                4 + ecPoint.size +
                4 + appBytes.size
        ).order(ByteOrder.BIG_ENDIAN)

        buffer.putInt(keyTypeBytes.size)
        buffer.put(keyTypeBytes)
        buffer.putInt(curveBytes.size)
        buffer.put(curveBytes)
        buffer.putInt(ecPoint.size)
        buffer.put(ecPoint)
        buffer.putInt(appBytes.size)
        buffer.put(appBytes)

        return buffer.array()
    }

    private fun buildDerSignature(r: ByteArray, s: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()

        // Build r INTEGER
        val rInt = buildDerInteger(r)
        // Build s INTEGER
        val sInt = buildDerInteger(s)

        // SEQUENCE tag
        baos.write(0x30)
        val seqLen = rInt.size + sInt.size
        if (seqLen < 128) {
            baos.write(seqLen)
        } else {
            baos.write(0x81)
            baos.write(seqLen)
        }
        baos.write(rInt)
        baos.write(sInt)

        return baos.toByteArray()
    }

    private fun buildDerInteger(value: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(0x02) // INTEGER tag

        // Add padding byte if high bit is set
        val needsPadding = (value[0].toInt() and 0x80) != 0
        val len = value.size + (if (needsPadding) 1 else 0)

        if (len < 128) {
            baos.write(len)
        } else {
            baos.write(0x81)
            baos.write(len)
        }

        if (needsPadding) {
            baos.write(0)
        }
        baos.write(value)

        return baos.toByteArray()
    }

    private fun extractFlags(encoded: ByteArray): Byte {
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)

        // Skip key type
        val keyTypeLen = buffer.int
        buffer.position(buffer.position() + keyTypeLen)

        // Skip signature blob
        val sigLen = buffer.int
        buffer.position(buffer.position() + sigLen)

        // Return flags byte
        return buffer.get()
    }

    private fun extractCounter(encoded: ByteArray): Int {
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)

        // Skip key type
        val keyTypeLen = buffer.int
        buffer.position(buffer.position() + keyTypeLen)

        // Skip signature blob
        val sigLen = buffer.int
        buffer.position(buffer.position() + sigLen)

        // Skip flags
        buffer.get()

        // Return counter
        return buffer.int
    }
}
