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

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.NegativeInteger
import co.nstant.`in`.cbor.model.UnsignedInteger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.ByteArrayOutputStream

class CoseKeyDecoderTest {

    @Test
    fun `decodeEd25519PublicKey extracts 32 byte key from OKP COSE key`() {
        val expectedKey = ByteArray(32) { it.toByte() }
        val coseKey = createEd25519CoseKey(expectedKey)

        val result = CoseKeyDecoder.decodeEd25519PublicKey(coseKey)

        assertThat(result).isEqualTo(expectedKey)
    }

    @Test
    fun `decodeEd25519PublicKey throws for EC2 key type`() {
        val xCoord = ByteArray(32) { it.toByte() }
        val yCoord = ByteArray(32) { (it + 32).toByte() }
        val coseKey = createEcdsaP256CoseKey(xCoord, yCoord)

        assertThatThrownBy {
            CoseKeyDecoder.decodeEd25519PublicKey(coseKey)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("OKP")
    }

    @Test
    fun `decodeEd25519PublicKey throws for wrong key size`() {
        val wrongSizeKey = ByteArray(16) { it.toByte() }
        val coseKey = createEd25519CoseKey(wrongSizeKey)

        assertThatThrownBy {
            CoseKeyDecoder.decodeEd25519PublicKey(coseKey)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("32 bytes")
    }

    @Test
    fun `decodeEcdsaP256PublicKey extracts uncompressed point from EC2 COSE key`() {
        val xCoord = ByteArray(32) { it.toByte() }
        val yCoord = ByteArray(32) { (it + 32).toByte() }
        val coseKey = createEcdsaP256CoseKey(xCoord, yCoord)

        val result = CoseKeyDecoder.decodeEcdsaP256PublicKey(coseKey)

        assertThat(result).hasSize(65)
        assertThat(result[0]).isEqualTo(0x04.toByte())
        assertThat(result.sliceArray(1..32)).isEqualTo(xCoord)
        assertThat(result.sliceArray(33..64)).isEqualTo(yCoord)
    }

    @Test
    fun `decodeEcdsaP256PublicKey throws for OKP key type`() {
        val ed25519Key = ByteArray(32) { it.toByte() }
        val coseKey = createEd25519CoseKey(ed25519Key)

        assertThatThrownBy {
            CoseKeyDecoder.decodeEcdsaP256PublicKey(coseKey)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("EC2")
    }

    @Test
    fun `decodeEcdsaP256PublicKey throws for wrong x coordinate size`() {
        val wrongSizeX = ByteArray(16) { it.toByte() }
        val yCoord = ByteArray(32) { (it + 32).toByte() }
        val coseKey = createEcdsaP256CoseKey(wrongSizeX, yCoord)

        assertThatThrownBy {
            CoseKeyDecoder.decodeEcdsaP256PublicKey(coseKey)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("32 bytes")
    }

    @Test
    fun `decodeEcdsaP256PublicKey throws for wrong y coordinate size`() {
        val xCoord = ByteArray(32) { it.toByte() }
        val wrongSizeY = ByteArray(16) { (it + 32).toByte() }
        val coseKey = createEcdsaP256CoseKey(xCoord, wrongSizeY)

        assertThatThrownBy {
            CoseKeyDecoder.decodeEcdsaP256PublicKey(coseKey)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("32 bytes")
    }

    @Test
    fun `decoder throws for empty CBOR data`() {
        assertThatThrownBy {
            CoseKeyDecoder.decodeEd25519PublicKey(byteArrayOf())
        }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `decoder throws for non-map CBOR data`() {
        // Create a CBOR array instead of map
        val baos = ByteArrayOutputStream()
        CborEncoder(baos).encode(CborBuilder().addArray().add(1).add(2).end().build())

        assertThatThrownBy {
            CoseKeyDecoder.decodeEd25519PublicKey(baos.toByteArray())
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Map")
    }

    private fun createEd25519CoseKey(publicKey: ByteArray): ByteArray {
        val coseKey = Map()
        coseKey.put(UnsignedInteger(1), UnsignedInteger(1)) // kty: OKP (1)
        coseKey.put(UnsignedInteger(3), NegativeInteger(-8)) // alg: EdDSA (-8)
        coseKey.put(NegativeInteger(-1), UnsignedInteger(6)) // crv: Ed25519 (6)
        coseKey.put(NegativeInteger(-2), ByteString(publicKey)) // x: public key

        val baos = ByteArrayOutputStream()
        CborEncoder(baos).encode(coseKey)
        return baos.toByteArray()
    }

    private fun createEcdsaP256CoseKey(xCoord: ByteArray, yCoord: ByteArray): ByteArray {
        val coseKey = Map()
        coseKey.put(UnsignedInteger(1), UnsignedInteger(2)) // kty: EC2 (2)
        coseKey.put(UnsignedInteger(3), NegativeInteger(-7)) // alg: ES256 (-7)
        coseKey.put(NegativeInteger(-1), UnsignedInteger(1)) // crv: P-256 (1)
        coseKey.put(NegativeInteger(-2), ByteString(xCoord)) // x coordinate
        coseKey.put(NegativeInteger(-3), ByteString(yCoord)) // y coordinate

        val baos = ByteArrayOutputStream()
        CborEncoder(baos).encode(coseKey)
        return baos.toByteArray()
    }
}
