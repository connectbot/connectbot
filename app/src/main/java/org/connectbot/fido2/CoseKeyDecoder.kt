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

package org.connectbot.fido2

import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.NegativeInteger
import co.nstant.`in`.cbor.model.UnsignedInteger
import java.io.ByteArrayInputStream

/**
 * Utility for decoding COSE public keys from CBOR format.
 *
 * COSE key format (RFC 8152):
 * - For EC2 (ECDSA P-256):
 *   - 1 (kty): 2 (EC2)
 *   - -1 (crv): 1 (P-256)
 *   - -2 (x): x coordinate (32 bytes)
 *   - -3 (y): y coordinate (32 bytes)
 *
 * - For OKP (Ed25519):
 *   - 1 (kty): 1 (OKP)
 *   - -1 (crv): 6 (Ed25519)
 *   - -2 (x): public key (32 bytes)
 */
object CoseKeyDecoder {

    private const val COSE_KTY = 1L
    private const val COSE_KTY_OKP = 1L
    private const val COSE_KTY_EC2 = 2L

    /**
     * Decode a COSE Ed25519 public key and return the raw 32-byte key.
     *
     * @param coseKeyBytes CBOR-encoded COSE public key
     * @return 32-byte Ed25519 public key
     * @throws IllegalArgumentException if the key is not a valid Ed25519 key
     */
    fun decodeEd25519PublicKey(coseKeyBytes: ByteArray): ByteArray {
        val coseKey = decodeCborMap(coseKeyBytes)

        // Verify key type is OKP (1)
        val kty = getUnsignedInt(coseKey, COSE_KTY)
        require(kty == COSE_KTY_OKP) { "Expected OKP key type (1), got $kty" }

        // Get the x parameter (-2) which contains the public key
        val xBytes = getBytes(coseKey, -2)
        require(xBytes.size == 32) { "Ed25519 key must be 32 bytes, got ${xBytes.size}" }

        return xBytes
    }

    /**
     * Decode a COSE EC2 P-256 public key and return the uncompressed EC point.
     *
     * @param coseKeyBytes CBOR-encoded COSE public key
     * @return 65-byte uncompressed EC point (0x04 || x || y)
     * @throws IllegalArgumentException if the key is not a valid EC2 P-256 key
     */
    fun decodeEcdsaP256PublicKey(coseKeyBytes: ByteArray): ByteArray {
        val coseKey = decodeCborMap(coseKeyBytes)

        // Verify key type is EC2 (2)
        val kty = getUnsignedInt(coseKey, COSE_KTY)
        require(kty == COSE_KTY_EC2) { "Expected EC2 key type (2), got $kty" }

        // Get x and y coordinates
        val xBytes = getBytes(coseKey, -2)
        val yBytes = getBytes(coseKey, -3)

        require(xBytes.size == 32) { "EC x coordinate must be 32 bytes, got ${xBytes.size}" }
        require(yBytes.size == 32) { "EC y coordinate must be 32 bytes, got ${yBytes.size}" }

        // Build uncompressed EC point: 0x04 || x || y
        return byteArrayOf(0x04) + xBytes + yBytes
    }

    private fun decodeCborMap(data: ByteArray): Map {
        val bais = ByteArrayInputStream(data)
        val decoded = CborDecoder(bais).decode()
        require(decoded.isNotEmpty()) { "Empty CBOR data" }
        return decoded[0] as? Map ?: throw IllegalArgumentException("Expected CBOR Map")
    }

    private fun getUnsignedInt(map: Map, key: Long): Long {
        val keyItem = if (key >= 0) UnsignedInteger(key) else NegativeInteger(key)
        return when (val value = map.get(keyItem)) {
            is UnsignedInteger -> value.value.toLong()
            is NegativeInteger -> value.value.toLong()
            else -> throw IllegalArgumentException("Expected integer at key $key")
        }
    }

    private fun getBytes(map: Map, key: Long): ByteArray {
        val keyItem = if (key >= 0) UnsignedInteger(key) else NegativeInteger(key)
        return when (val value = map.get(keyItem)) {
            is ByteString -> value.bytes
            else -> throw IllegalArgumentException("Expected byte string at key $key")
        }
    }
}
