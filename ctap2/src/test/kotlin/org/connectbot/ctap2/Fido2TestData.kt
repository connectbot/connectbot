/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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
        0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
    )

    val CREDENTIAL_ID_2 = byteArrayOf(
        0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18,
        0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x20,
    )

    // Sample Ed25519 public key (32 bytes)
    val ED25519_PUBLIC_KEY = ByteArray(32) { it.toByte() }

    // Sample ECDSA P-256 public key as uncompressed point (65 bytes: 0x04 || x || y)
    val ECDSA_P256_PUBLIC_KEY = ByteArray(65) { index ->
        when (index) {
            0 -> 0x04.toByte()

            // Uncompressed point marker
            else -> (index - 1).toByte()
        }
    }

    // Sample authenticator data (37 bytes minimum)
    // 32 bytes rpIdHash + 1 byte flags + 4 bytes counter
    fun createAuthenticatorData(
        flags: Byte = 0x01, // UP flag
        counter: Int = 1,
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
        rpId: String = SSH_RP_ID,
    ) = Fido2Credential(
        credentialId = credentialId,
        rpId = rpId,
        userHandle = USER_ID,
        userName = userName,
        publicKeyBytes = ED25519_PUBLIC_KEY,
        algorithm = Fido2Algorithm.EDDSA,
    )

    // Create a test Fido2Credential for ECDSA
    fun createEcdsaCredential(
        credentialId: ByteArray = CREDENTIAL_ID_2,
        userName: String? = USER_NAME,
        rpId: String = SSH_RP_ID,
    ) = Fido2Credential(
        credentialId = credentialId,
        rpId = rpId,
        userHandle = USER_ID,
        userName = userName,
        publicKeyBytes = ECDSA_P256_PUBLIC_KEY,
        algorithm = Fido2Algorithm.ES256,
    )

    // Create a test Fido2SignatureResult
    fun createSignatureResult(
        authenticatorData: ByteArray = createAuthenticatorData(),
        signature: ByteArray = ED25519_SIGNATURE,
        counter: Int = 1,
    ) = Fido2SignatureResult(
        authenticatorData = authenticatorData,
        signature = signature,

        counter = counter,
    )

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
