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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class Fido2ModelsTest {

    @Test
    fun `Fido2Algorithm fromCoseId returns correct algorithm for ES256`() {
        val algorithm = Fido2Algorithm.fromCoseId(-7)
        assertThat(algorithm).isEqualTo(Fido2Algorithm.ES256)
    }

    @Test
    fun `Fido2Algorithm fromCoseId returns correct algorithm for EDDSA`() {
        val algorithm = Fido2Algorithm.fromCoseId(-8)
        assertThat(algorithm).isEqualTo(Fido2Algorithm.EDDSA)
    }

    @Test
    fun `Fido2Algorithm fromCoseId returns null for unknown algorithm`() {
        val algorithm = Fido2Algorithm.fromCoseId(-999)
        assertThat(algorithm).isNull()
    }

    @Test
    fun `Fido2Algorithm fromSshKeyType returns correct algorithm for sk-ecdsa`() {
        val algorithm = Fido2Algorithm.fromSshKeyType("sk-ecdsa-sha2-nistp256@openssh.com")
        assertThat(algorithm).isEqualTo(Fido2Algorithm.ES256)
    }

    @Test
    fun `Fido2Algorithm fromSshKeyType returns correct algorithm for sk-ed25519`() {
        val algorithm = Fido2Algorithm.fromSshKeyType("sk-ssh-ed25519@openssh.com")
        assertThat(algorithm).isEqualTo(Fido2Algorithm.EDDSA)
    }

    @Test
    fun `Fido2Algorithm fromSshKeyType returns null for unknown key type`() {
        val algorithm = Fido2Algorithm.fromSshKeyType("ssh-rsa")
        assertThat(algorithm).isNull()
    }

    @Test
    fun `Fido2Algorithm ES256 has correct properties`() {
        assertThat(Fido2Algorithm.ES256.coseId).isEqualTo(-7)
        assertThat(Fido2Algorithm.ES256.sshKeyType).isEqualTo("sk-ecdsa-sha2-nistp256@openssh.com")
        assertThat(Fido2Algorithm.ES256.displayName).isEqualTo("ECDSA-SK")
    }

    @Test
    fun `Fido2Algorithm EDDSA has correct properties`() {
        assertThat(Fido2Algorithm.EDDSA.coseId).isEqualTo(-8)
        assertThat(Fido2Algorithm.EDDSA.sshKeyType).isEqualTo("sk-ssh-ed25519@openssh.com")
        assertThat(Fido2Algorithm.EDDSA.displayName).isEqualTo("Ed25519-SK")
    }

    @Test
    fun `Fido2Credential equals compares ByteArray fields correctly`() {
        val credential1 = Fido2Credential(
            credentialId = byteArrayOf(1, 2, 3),
            rpId = "ssh:",
            userHandle = byteArrayOf(10, 20),
            userName = "testuser",
            publicKeyCose = byteArrayOf(100, 101, 102),
            algorithm = Fido2Algorithm.ES256
        )

        val credential2 = Fido2Credential(
            credentialId = byteArrayOf(1, 2, 3),
            rpId = "ssh:",
            userHandle = byteArrayOf(10, 20),
            userName = "testuser",
            publicKeyCose = byteArrayOf(100, 101, 102),
            algorithm = Fido2Algorithm.ES256
        )

        assertThat(credential1).isEqualTo(credential2)
        assertThat(credential1.hashCode()).isEqualTo(credential2.hashCode())
    }

    @Test
    fun `Fido2Credential equals returns false when credentialId differs`() {
        val credential1 = Fido2Credential(
            credentialId = byteArrayOf(1, 2, 3),
            rpId = "ssh:",
            userHandle = null,
            userName = null,
            publicKeyCose = byteArrayOf(100, 101, 102),
            algorithm = Fido2Algorithm.ES256
        )

        val credential2 = Fido2Credential(
            credentialId = byteArrayOf(1, 2, 99), // Different
            rpId = "ssh:",
            userHandle = null,
            userName = null,
            publicKeyCose = byteArrayOf(100, 101, 102),
            algorithm = Fido2Algorithm.ES256
        )

        assertThat(credential1).isNotEqualTo(credential2)
    }

    @Test
    fun `Fido2Credential equals handles null userHandle`() {
        val credential1 = Fido2Credential(
            credentialId = byteArrayOf(1, 2, 3),
            rpId = "ssh:",
            userHandle = null,
            userName = "testuser",
            publicKeyCose = byteArrayOf(100, 101, 102),
            algorithm = Fido2Algorithm.ES256
        )

        val credential2 = Fido2Credential(
            credentialId = byteArrayOf(1, 2, 3),
            rpId = "ssh:",
            userHandle = byteArrayOf(10, 20), // Non-null
            userName = "testuser",
            publicKeyCose = byteArrayOf(100, 101, 102),
            algorithm = Fido2Algorithm.ES256
        )

        assertThat(credential1).isNotEqualTo(credential2)
    }

    @Test
    fun `Fido2SignatureResult flags byte is extracted correctly`() {
        // Authenticator data: 32 bytes rpIdHash + 1 byte flags + 4 bytes counter
        val authenticatorData = ByteArray(37)
        authenticatorData[32] = 0x05.toByte() // UP=1, UV=1

        val result = Fido2SignatureResult(
            authenticatorData = authenticatorData,
            signature = byteArrayOf(1, 2, 3),
            userPresenceVerified = true,
            userVerified = true,
            counter = 42
        )

        assertThat(result.flags).isEqualTo(0x05.toByte())
    }

    @Test
    fun `Fido2SignatureResult flags returns 0 for short authenticatorData`() {
        val result = Fido2SignatureResult(
            authenticatorData = byteArrayOf(1, 2, 3), // Too short
            signature = byteArrayOf(1, 2, 3),
            userPresenceVerified = true,
            userVerified = false,
            counter = 0
        )

        assertThat(result.flags).isEqualTo(0.toByte())
    }

    @Test
    fun `Fido2SignatureResult equals compares ByteArray fields correctly`() {
        val result1 = Fido2SignatureResult(
            authenticatorData = byteArrayOf(1, 2, 3),
            signature = byteArrayOf(10, 20, 30),
            userPresenceVerified = true,
            userVerified = false,
            counter = 100
        )

        val result2 = Fido2SignatureResult(
            authenticatorData = byteArrayOf(1, 2, 3),
            signature = byteArrayOf(10, 20, 30),
            userPresenceVerified = true,
            userVerified = false,
            counter = 100
        )

        assertThat(result1).isEqualTo(result2)
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode())
    }

    @Test
    fun `Fido2ConnectionState Connected contains transport info`() {
        val state = Fido2ConnectionState.Connected(
            transport = "USB",
            deviceName = "YubiKey 5"
        )

        assertThat(state.transport).isEqualTo("USB")
        assertThat(state.deviceName).isEqualTo("YubiKey 5")
    }

    @Test
    fun `Fido2ConnectionState Error contains message`() {
        val state = Fido2ConnectionState.Error("Connection failed")

        assertThat(state.message).isEqualTo("Connection failed")
    }

    @Test
    fun `Fido2Result Success contains value`() {
        val result: Fido2Result<String> = Fido2Result.Success("test data")

        assertThat(result).isInstanceOf(Fido2Result.Success::class.java)
        assertThat((result as Fido2Result.Success).value).isEqualTo("test data")
    }

    @Test
    fun `Fido2Result PinInvalid contains attempts remaining`() {
        val result: Fido2Result<Nothing> = Fido2Result.PinInvalid(3)

        assertThat(result).isInstanceOf(Fido2Result.PinInvalid::class.java)
        assertThat((result as Fido2Result.PinInvalid).attemptsRemaining).isEqualTo(3)
    }

    @Test
    fun `Fido2Result Error contains message and optional cause`() {
        val cause = RuntimeException("underlying error")
        val result: Fido2Result<Nothing> = Fido2Result.Error("Operation failed", cause)

        assertThat(result).isInstanceOf(Fido2Result.Error::class.java)
        assertThat((result as Fido2Result.Error).message).isEqualTo("Operation failed")
        assertThat(result.cause).isEqualTo(cause)
    }

    @Test
    fun `Fido2AuthenticatorInfo equals compares ByteArray aaguid correctly`() {
        val info1 = Fido2AuthenticatorInfo(
            versions = listOf("FIDO_2_0"),
            aaguid = byteArrayOf(1, 2, 3, 4),
            pinConfigured = true,
            credentialManagementSupported = true,
            residentKeySupported = true,
            maxCredentialCount = 25,
            remainingCredentialCount = 20
        )

        val info2 = Fido2AuthenticatorInfo(
            versions = listOf("FIDO_2_0"),
            aaguid = byteArrayOf(1, 2, 3, 4),
            pinConfigured = true,
            credentialManagementSupported = true,
            residentKeySupported = true,
            maxCredentialCount = 25,
            remainingCredentialCount = 20
        )

        assertThat(info1).isEqualTo(info2)
        assertThat(info1.hashCode()).isEqualTo(info2.hashCode())
    }

    @Test
    fun `Fido2AuthenticatorInfo equals handles null aaguid`() {
        val info1 = Fido2AuthenticatorInfo(
            versions = listOf("FIDO_2_0"),
            aaguid = null,
            pinConfigured = false,
            credentialManagementSupported = false,
            residentKeySupported = true,
            maxCredentialCount = null,
            remainingCredentialCount = null
        )

        val info2 = Fido2AuthenticatorInfo(
            versions = listOf("FIDO_2_0"),
            aaguid = byteArrayOf(1, 2, 3, 4), // Non-null
            pinConfigured = false,
            credentialManagementSupported = false,
            residentKeySupported = true,
            maxCredentialCount = null,
            remainingCredentialCount = null
        )

        assertThat(info1).isNotEqualTo(info2)
    }
}
