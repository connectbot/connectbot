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

package org.connectbot.ctap2

import com.yubico.yubikit.fido.webauthn.AuthenticatorData
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

/** Public-key recovery only; private keys never enter this operation (SEC 1 section 4.1.6). */
object P256PublicKeyRecovery {
    data class Assertion(val signedData: ByteArray, val signature: ByteArray)

    fun assertion(authenticatorData: ByteArray, clientDataJson: ByteArray, signature: ByteArray, rpId: String): Assertion {
        val auth = AuthenticatorData.parseFrom(ByteBuffer.wrap(authenticatorData))
        require(auth.isUp && auth.isUv && !auth.isAt) { "Verified assertion required" }
        require(auth.rpIdHash.contentEquals(MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray(Charsets.UTF_8)))) {
            "Assertion RP ID mismatch"
        }
        return Assertion(
            authenticatorData + MessageDigest.getInstance("SHA-256").digest(clientDataJson),
            signature,
        )
    }

    fun recover(first: Assertion, second: Assertion): ByteArray {
        require(!first.signedData.contentEquals(second.signedData)) { "Independent assertions are required" }
        val matches = candidates(first).filter { point ->
            verify(point, first) && verify(point, second)
        }
        require(matches.size == 1) { "Assertions do not identify a unique ES256 public key" }
        return matches.single()
    }

    private fun candidates(assertion: Assertion): List<ByteArray> {
        val (r, s) = scalars(assertion.signature)
        val params = parameters()
        val n = params.order
        require(r.signum() > 0 && r < n && s.signum() > 0 && s < n) { "Invalid ES256 signature scalars" }
        val field = (params.curve.field as ECFieldFp).p
        val curve = PublicCurve(field, params.curve.a)
        val e = BigInteger(1, MessageDigest.getInstance("SHA-256").digest(assertion.signedData))
        val inverse = r.modInverse(n)
        val result = mutableListOf<ByteArray>()
        var x = r
        while (x < field) {
            val square = (x.pow(3) + params.curve.a * x + params.curve.b).mod(field)
            // P-256's prime is 3 mod 4, so a square root is square^((p + 1) / 4).
            val y = square.modPow((field + BigInteger.ONE).shiftRight(2), field)
            if (y.pow(2).mod(field) == square) {
                for (root in listOf(y, (field - y).mod(field)).distinct()) {
                    val point = ECPoint(x, root)
                    if (curve.multiply(point, n) != null) continue
                    // SEC 1 v2 section 4.1.6: Q = r^-1 (sR - eG).
                    val key = curve.multiply(
                        curve.add(
                            curve.multiply(point, s),
                            curve.multiply(params.generator, (-e).mod(n)),
                        ),
                        inverse,
                    ) ?: continue
                    val encoded = byteArrayOf(4) + unsigned32(key.affineX) + unsigned32(key.affineY)
                    if (result.none { it.contentEquals(encoded) }) result.add(encoded)
                }
            }
            x += n
        }
        return result
    }

    /** Strict short-form DER; P-256 signatures are at most 72 bytes. */
    private fun scalars(signature: ByteArray): Pair<BigInteger, BigInteger> {
        require(signature.size in 8..72 && signature[0] == 0x30.toByte() && signature[1].toInt() == signature.size - 2) {
            "Expected DER ES256 signature"
        }
        var offset = 2
        fun integer(): BigInteger {
            require(offset + 2 <= signature.size && signature[offset++] == 2.toByte()) { "Expected DER integer" }
            val length = signature[offset++].toInt()
            require(length in 1..33 && offset + length <= signature.size) { "Invalid DER integer length" }
            require(signature[offset].toInt() >= 0) { "Negative DER integer" }
            require(length == 1 || signature[offset] != 0.toByte() || signature[offset + 1].toInt() < 0) {
                "Noncanonical DER integer"
            }
            return BigInteger(1, signature.copyOfRange(offset, offset + length)).also { offset += length }
        }
        val r = integer()
        val s = integer()
        require(offset == signature.size) { "Trailing DER signature data" }
        return r to s
    }

    /** Affine point arithmetic for public recovery inputs only; null represents infinity. */
    private class PublicCurve(private val p: BigInteger, private val a: BigInteger) {
        fun add(first: ECPoint?, second: ECPoint?): ECPoint? {
            if (first == null) return second
            if (second == null) return first
            val x = first.affineX
            val y = first.affineY
            val slope = if (x == second.affineX) {
                if ((y + second.affineY).mod(p) == BigInteger.ZERO) return null
                (x.pow(2) * BigInteger.valueOf(3) + a) * y.shiftLeft(1).modInverse(p)
            } else {
                (second.affineY - y) * (second.affineX - x).mod(p).modInverse(p)
            }.mod(p)
            val resultX = (slope.pow(2) - x - second.affineX).mod(p)
            return ECPoint(resultX, (slope * (x - resultX) - y).mod(p))
        }

        fun multiply(point: ECPoint?, scalar: BigInteger): ECPoint? {
            var result: ECPoint? = null
            for (bit in scalar.bitLength() - 1 downTo 0) {
                result = add(result, result)
                if (scalar.testBit(bit)) result = add(result, point)
            }
            return result
        }
    }

    private fun parameters(): ECParameterSpec = AlgorithmParameters.getInstance("EC").apply {
        init(ECGenParameterSpec("secp256r1"))
    }.getParameterSpec(ECParameterSpec::class.java)

    private fun unsigned32(value: BigInteger): ByteArray {
        val bytes = value.toByteArray().takeLast(32).toByteArray()
        return ByteArray(32 - bytes.size) + bytes
    }

    private fun verify(point: ByteArray, assertion: Assertion): Boolean {
        val params = parameters()
        val key = KeyFactory.getInstance("EC").generatePublic(
            ECPublicKeySpec(
                ECPoint(BigInteger(1, point.copyOfRange(1, 33)), BigInteger(1, point.copyOfRange(33, 65))),
                params,
            ),
        )
        return Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(assertion.signedData)
            verify(assertion.signature)
        }
    }
}
