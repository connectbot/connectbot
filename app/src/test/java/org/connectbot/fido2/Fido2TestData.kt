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

package org.connectbot.fido2

/**
 * Test data constants for FIDO2 unit tests.
 * Inspired by YubiKit's TestData pattern for consistent test values.
 */
object Fido2TestData {

    // PIN values
    const val PIN = "11234567"
    const val OTHER_PIN = "11231234"
    const val INVALID_PIN = "00000000"

    // SSH RP ID used by OpenSSH
    const val SSH_RP_ID = "ssh:"

    // Test user information
    const val USER_NAME = "testuser@example.com"
    val USER_ID = USER_NAME.toByteArray(Charsets.UTF_8)
    const val USER_DISPLAY_NAME = "Test User"

    // Sample credential IDs
    val CREDENTIAL_ID_1 = byteArrayOf(
        0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
        0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10
    )

    val CREDENTIAL_ID_2 = byteArrayOf(
        0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
        0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20
    )

    // Sample Ed25519 public key (32 bytes)
    val ED25519_PUBLIC_KEY = ByteArray(32) { it.toByte() }

    // Sample ECDSA P-256 public key as uncompressed point (65 bytes: 0x04 || x || y)
    val ECDSA_P256_PUBLIC_KEY = ByteArray(65) { index ->
        when (index) {
            0 -> 0x04.toByte() // Uncompressed point marker
            else -> (index - 1).toByte()
        }
    }

    // Sample COSE-encoded Ed25519 public key (OKP key type)
    // CBOR map: {1: 1, 3: -8, -1: 6, -2: <32 bytes>}
    val COSE_ED25519_KEY: ByteArray by lazy {
        createCoseEd25519Key(ED25519_PUBLIC_KEY)
    }

    // Sample COSE-encoded ECDSA P-256 public key (EC2 key type)
    // CBOR map: {1: 2, 3: -7, -1: 1, -2: <32 bytes x>, -3: <32 bytes y>}
    val COSE_ECDSA_P256_KEY: ByteArray by lazy {
        createCoseEcdsaP256Key(
            x = ByteArray(32) { it.toByte() },
            y = ByteArray(32) { (it + 32).toByte() }
        )
    }

    // Sample authenticator data (37 bytes minimum)
    // 32 bytes rpIdHash + 1 byte flags + 4 bytes counter
    fun createAuthenticatorData(
        flags: Byte = 0x01, // UP flag
        counter: Int = 1
    ): ByteArray {
        val data = ByteArray(37)
        // rpIdHash (32 bytes of zeros for test)
        // flags at byte 32
        data[32] = flags
        // counter at bytes 33-36 (big endian)
        data[33] = ((counter shr 24) and 0xFF).toByte()
        data[34] = ((counter shr 16) and 0xFF).toByte()
        data[35] = ((counter shr 8) and 0xFF).toByte()
        data[36] = (counter and 0xFF).toByte()
        return data
    }

    // Sample Ed25519 signature (64 bytes)
    val ED25519_SIGNATURE = ByteArray(64) { (it * 2).toByte() }

    // Sample ECDSA signature in DER format (approximately 70 bytes)
    val ECDSA_SIGNATURE: ByteArray by lazy {
        // Simple DER-encoded ECDSA signature for testing
        val r = ByteArray(32) { it.toByte() }
        val s = ByteArray(32) { (it + 32).toByte() }
        createDerSignature(r, s)
    }

    // Create a test Fido2Credential for Ed25519
    fun createEd25519Credential(
        credentialId: ByteArray = CREDENTIAL_ID_1,
        userName: String? = USER_NAME,
        rpId: String = SSH_RP_ID
    ) = Fido2Credential(
        credentialId = credentialId,
        rpId = rpId,
        userHandle = USER_ID,
        userName = userName,
        publicKeyCose = COSE_ED25519_KEY,
        algorithm = Fido2Algorithm.EDDSA
    )

    // Create a test Fido2Credential for ECDSA
    fun createEcdsaCredential(
        credentialId: ByteArray = CREDENTIAL_ID_2,
        userName: String? = USER_NAME,
        rpId: String = SSH_RP_ID
    ) = Fido2Credential(
        credentialId = credentialId,
        rpId = rpId,
        userHandle = USER_ID,
        userName = userName,
        publicKeyCose = COSE_ECDSA_P256_KEY,
        algorithm = Fido2Algorithm.ES256
    )

    // Create a test Fido2SignatureResult
    fun createSignatureResult(
        authenticatorData: ByteArray = createAuthenticatorData(),
        signature: ByteArray = ED25519_SIGNATURE,
        userPresenceVerified: Boolean = true,
        userVerified: Boolean = false,
        counter: Int = 1
    ) = Fido2SignatureResult(
        authenticatorData = authenticatorData,
        signature = signature,
        userPresenceVerified = userPresenceVerified,
        userVerified = userVerified,
        counter = counter
    )

    // Create a test Fido2AuthenticatorInfo
    fun createAuthenticatorInfo(
        pinConfigured: Boolean = true,
        credentialManagementSupported: Boolean = true,
        residentKeySupported: Boolean = true
    ) = Fido2AuthenticatorInfo(
        versions = listOf("FIDO_2_0", "FIDO_2_1"),
        aaguid = ByteArray(16) { it.toByte() },
        pinConfigured = pinConfigured,
        credentialManagementSupported = credentialManagementSupported,
        residentKeySupported = residentKeySupported,
        maxCredentialCount = 25,
        remainingCredentialCount = 20
    )

    // Helper to create COSE Ed25519 key in CBOR format
    private fun createCoseEd25519Key(publicKey: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val encoder = co.nstant.`in`.cbor.CborEncoder(output)

        val cborMap = co.nstant.`in`.cbor.model.Map()
        // kty = 1 (OKP)
        cborMap.put(
            co.nstant.`in`.cbor.model.UnsignedInteger(1),
            co.nstant.`in`.cbor.model.UnsignedInteger(1)
        )
        // alg = -8 (EdDSA)
        cborMap.put(
            co.nstant.`in`.cbor.model.UnsignedInteger(3),
            co.nstant.`in`.cbor.model.NegativeInteger(-8)
        )
        // crv = 6 (Ed25519)
        cborMap.put(
            co.nstant.`in`.cbor.model.NegativeInteger(-1),
            co.nstant.`in`.cbor.model.UnsignedInteger(6)
        )
        // x = public key
        cborMap.put(
            co.nstant.`in`.cbor.model.NegativeInteger(-2),
            co.nstant.`in`.cbor.model.ByteString(publicKey)
        )

        encoder.encode(cborMap)
        return output.toByteArray()
    }

    // Helper to create COSE ECDSA P-256 key in CBOR format
    private fun createCoseEcdsaP256Key(x: ByteArray, y: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val encoder = co.nstant.`in`.cbor.CborEncoder(output)

        val cborMap = co.nstant.`in`.cbor.model.Map()
        // kty = 2 (EC2)
        cborMap.put(
            co.nstant.`in`.cbor.model.UnsignedInteger(1),
            co.nstant.`in`.cbor.model.UnsignedInteger(2)
        )
        // alg = -7 (ES256)
        cborMap.put(
            co.nstant.`in`.cbor.model.UnsignedInteger(3),
            co.nstant.`in`.cbor.model.NegativeInteger(-7)
        )
        // crv = 1 (P-256)
        cborMap.put(
            co.nstant.`in`.cbor.model.NegativeInteger(-1),
            co.nstant.`in`.cbor.model.UnsignedInteger(1)
        )
        // x coordinate
        cborMap.put(
            co.nstant.`in`.cbor.model.NegativeInteger(-2),
            co.nstant.`in`.cbor.model.ByteString(x)
        )
        // y coordinate
        cborMap.put(
            co.nstant.`in`.cbor.model.NegativeInteger(-3),
            co.nstant.`in`.cbor.model.ByteString(y)
        )

        encoder.encode(cborMap)
        return output.toByteArray()
    }

    // Helper to create DER-encoded ECDSA signature
    private fun createDerSignature(r: ByteArray, s: ByteArray): ByteArray {
        // Simplified DER encoding for test purposes
        val rWithLeadingZero = if (r[0] < 0) byteArrayOf(0) + r else r
        val sWithLeadingZero = if (s[0] < 0) byteArrayOf(0) + s else s

        val innerLength = 2 + rWithLeadingZero.size + 2 + sWithLeadingZero.size
        val output = java.io.ByteArrayOutputStream()

        output.write(0x30) // SEQUENCE
        output.write(innerLength)
        output.write(0x02) // INTEGER
        output.write(rWithLeadingZero.size)
        output.write(rWithLeadingZero)
        output.write(0x02) // INTEGER
        output.write(sWithLeadingZero.size)
        output.write(sWithLeadingZero)

        return output.toByteArray()
    }
}
