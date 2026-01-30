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
 * FIDO2 algorithm identifiers as defined in COSE (RFC 8152).
 * These map to SSH security key types.
 */
enum class Fido2Algorithm(
    val coseId: Int,
    val sshKeyType: String,
    val displayName: String
) {
    /** ECDSA with P-256 curve and SHA-256 (COSE algorithm -7) */
    ES256(-7, "sk-ecdsa-sha2-nistp256@openssh.com", "ECDSA-SK"),

    /** EdDSA with Ed25519 curve (COSE algorithm -8) */
    EDDSA(-8, "sk-ssh-ed25519@openssh.com", "Ed25519-SK");

    companion object {
        fun fromCoseId(coseId: Int): Fido2Algorithm? = entries.find { it.coseId == coseId }

        fun fromSshKeyType(sshKeyType: String): Fido2Algorithm? = entries.find { it.sshKeyType == sshKeyType }
    }
}

/**
 * Represents a FIDO2 resident credential discovered on a security key.
 */
data class Fido2Credential(
    /** The credential ID (key handle) used to identify this credential */
    val credentialId: ByteArray,

    /** The relying party ID, typically "ssh:" for SSH keys */
    val rpId: String,

    /** Optional user handle associated with this credential */
    val userHandle: ByteArray?,

    /** Optional user name associated with this credential */
    val userName: String?,

    /** The public key in COSE format */
    val publicKeyCose: ByteArray,

    /** The cryptographic algorithm used by this credential */
    val algorithm: Fido2Algorithm
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Fido2Credential

        if (!credentialId.contentEquals(other.credentialId)) return false
        if (rpId != other.rpId) return false
        if (userHandle != null) {
            if (other.userHandle == null) return false
            if (!userHandle.contentEquals(other.userHandle)) return false
        } else if (other.userHandle != null) {
            return false
        }
        if (userName != other.userName) return false
        if (!publicKeyCose.contentEquals(other.publicKeyCose)) return false
        if (algorithm != other.algorithm) return false

        return true
    }

    override fun hashCode(): Int {
        var result = credentialId.contentHashCode()
        result = 31 * result + rpId.hashCode()
        result = 31 * result + (userHandle?.contentHashCode() ?: 0)
        result = 31 * result + (userName?.hashCode() ?: 0)
        result = 31 * result + publicKeyCose.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        return result
    }
}

/**
 * Result of a FIDO2 signing operation (GetAssertion).
 * Contains all data needed to construct an SSH sk-* signature.
 */
data class Fido2SignatureResult(
    /** The authenticator data containing flags and counter */
    val authenticatorData: ByteArray,

    /** The cryptographic signature over clientDataHash */
    val signature: ByteArray,

    /** User presence flag (bit 0 of flags byte) */
    val userPresenceVerified: Boolean,

    /** User verification flag (bit 2 of flags byte, indicates PIN was used) */
    val userVerified: Boolean,

    /** Signature counter from authenticator data */
    val counter: Int
) {
    /** The flags byte from authenticator data (first byte after rpIdHash) */
    val flags: Byte
        get() = if (authenticatorData.size > 32) authenticatorData[32] else 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Fido2SignatureResult

        if (!authenticatorData.contentEquals(other.authenticatorData)) return false
        if (!signature.contentEquals(other.signature)) return false
        if (userPresenceVerified != other.userPresenceVerified) return false
        if (userVerified != other.userVerified) return false
        if (counter != other.counter) return false

        return true
    }

    override fun hashCode(): Int {
        var result = authenticatorData.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        result = 31 * result + userPresenceVerified.hashCode()
        result = 31 * result + userVerified.hashCode()
        result = 31 * result + counter
        return result
    }
}

/**
 * Connection state of FIDO2 security key.
 */
sealed class Fido2ConnectionState {
    /** No security key is connected */
    data object Disconnected : Fido2ConnectionState()

    /** Attempting to connect to a security key */
    data object Connecting : Fido2ConnectionState()

    /** Security key is connected and ready */
    data class Connected(
        /** Transport type: "USB" or "NFC" */
        val transport: String,
        /** Device name if available */
        val deviceName: String? = null
    ) : Fido2ConnectionState()

    /** An error occurred during connection or operation */
    data class Error(val message: String) : Fido2ConnectionState()
}

/**
 * Result of authenticator operations that may require PIN.
 */
sealed class Fido2Result<out T> {
    data class Success<T>(val value: T) : Fido2Result<T>()
    data class PinRequired(val attemptsRemaining: Int?) : Fido2Result<Nothing>()
    data class PinInvalid(val attemptsRemaining: Int?) : Fido2Result<Nothing>()
    data class PinLocked(val message: String) : Fido2Result<Nothing>()
    data class Error(val message: String, val cause: Throwable? = null) : Fido2Result<Nothing>()
    data object Cancelled : Fido2Result<Nothing>()
}

/**
 * Information about a FIDO2 authenticator device.
 */
data class Fido2AuthenticatorInfo(
    /** CTAP protocol versions supported (e.g., "FIDO_2_0", "FIDO_2_1") */
    val versions: List<String>,

    /** AAGUID (Authenticator Attestation GUID) */
    val aaguid: ByteArray?,

    /** Whether PIN is configured on this authenticator */
    val pinConfigured: Boolean,

    /** Whether credential management is supported */
    val credentialManagementSupported: Boolean,

    /** Whether resident keys are supported */
    val residentKeySupported: Boolean,

    /** Maximum number of credentials that can be stored */
    val maxCredentialCount: Int?,

    /** Remaining credential slots available */
    val remainingCredentialCount: Int?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Fido2AuthenticatorInfo

        if (versions != other.versions) return false
        if (aaguid != null) {
            if (other.aaguid == null) return false
            if (!aaguid.contentEquals(other.aaguid)) return false
        } else if (other.aaguid != null) {
            return false
        }
        if (pinConfigured != other.pinConfigured) return false
        if (credentialManagementSupported != other.credentialManagementSupported) return false
        if (residentKeySupported != other.residentKeySupported) return false
        if (maxCredentialCount != other.maxCredentialCount) return false
        if (remainingCredentialCount != other.remainingCredentialCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = versions.hashCode()
        result = 31 * result + (aaguid?.contentHashCode() ?: 0)
        result = 31 * result + pinConfigured.hashCode()
        result = 31 * result + credentialManagementSupported.hashCode()
        result = 31 * result + residentKeySupported.hashCode()
        result = 31 * result + (maxCredentialCount ?: 0)
        result = 31 * result + (remainingCredentialCount ?: 0)
        return result
    }
}
