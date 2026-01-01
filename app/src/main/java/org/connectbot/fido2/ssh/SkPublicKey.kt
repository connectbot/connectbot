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

/**
 * Common interface for FIDO2 security key SSH public keys.
 *
 * These keys follow the OpenSSH sk-* format introduced in OpenSSH 8.2.
 *
 * This interface extends the sshlib SkPublicKey interface to enable
 * SK key authentication in the SSH library.
 */
interface SkPublicKey : com.trilead.ssh2.signature.SkPublicKey {
    /** The application ID, typically "ssh:" for SSH keys */
    override fun getApplication(): String

    /** The underlying public key bytes (format depends on algorithm) */
    override fun getKeyData(): ByteArray

    /** The SSH key type identifier */
    override fun getSshKeyType(): String
}

/**
 * SK-ECDSA public key (sk-ecdsa-sha2-nistp256@openssh.com).
 *
 * Wire format:
 * - string "sk-ecdsa-sha2-nistp256@openssh.com"
 * - string curve identifier "nistp256"
 * - string EC point (0x04 || x || y, uncompressed)
 * - string application (e.g., "ssh:")
 */
data class SkEcdsaPublicKey(
    /** The application ID, typically "ssh:" for SSH keys */
    private val application: String,
    /** EC point in uncompressed format (0x04 || x || y) */
    val ecPoint: ByteArray,
    /** Curve identifier, typically "nistp256" */
    val curve: String = "nistp256"
) : SkPublicKey {

    override fun getSshKeyType(): String = KEY_TYPE

    override fun getApplication(): String = application

    override fun getKeyData(): ByteArray = ecPoint

    override fun getAlgorithm(): String = "EC"

    override fun getFormat(): String = "OpenSSH"

    override fun getEncoded(): ByteArray {
        // Return SSH wire format encoding
        return SkEcdsaVerify.encodePublicKey(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SkEcdsaPublicKey

        if (application != other.application) return false
        if (!ecPoint.contentEquals(other.ecPoint)) return false
        if (curve != other.curve) return false

        return true
    }

    override fun hashCode(): Int {
        var result = application.hashCode()
        result = 31 * result + ecPoint.contentHashCode()
        result = 31 * result + curve.hashCode()
        return result
    }

    companion object {
        const val KEY_TYPE = "sk-ecdsa-sha2-nistp256@openssh.com"
    }
}

/**
 * SK-Ed25519 public key (sk-ssh-ed25519@openssh.com).
 *
 * Wire format:
 * - string "sk-ssh-ed25519@openssh.com"
 * - string Ed25519 public key (32 bytes)
 * - string application (e.g., "ssh:")
 */
data class SkEd25519PublicKey(
    /** The application ID, typically "ssh:" for SSH keys */
    private val application: String,
    /** Ed25519 public key (32 bytes) */
    val ed25519Key: ByteArray
) : SkPublicKey {

    override fun getSshKeyType(): String = KEY_TYPE

    override fun getApplication(): String = application

    override fun getKeyData(): ByteArray = ed25519Key

    override fun getAlgorithm(): String = "Ed25519"

    override fun getFormat(): String = "OpenSSH"

    override fun getEncoded(): ByteArray {
        // Return SSH wire format encoding
        return SkEd25519Verify.encodePublicKey(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SkEd25519PublicKey

        if (application != other.application) return false
        if (!ed25519Key.contentEquals(other.ed25519Key)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = application.hashCode()
        result = 31 * result + ed25519Key.contentHashCode()
        return result
    }

    companion object {
        const val KEY_TYPE = "sk-ssh-ed25519@openssh.com"
    }
}
