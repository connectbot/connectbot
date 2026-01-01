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

import org.connectbot.fido2.Fido2Algorithm
import java.security.PrivateKey

/**
 * A wrapper class for FIDO2 private keys used in SSH authentication.
 *
 * FIDO2 security keys don't expose the actual private key - signing happens
 * on the hardware device. This class holds the credential information needed
 * to perform signing operations via the FIDO2 device.
 *
 * When the SSH library needs to sign data with this key, the signing is
 * delegated to the FIDO2 device through [Fido2SigningCallback].
 */
class Fido2PrivateKey(
    /** The credential ID on the security key */
    val credentialId: ByteArray,
    /** The relying party ID (typically "ssh:") */
    val rpId: String,
    /** The algorithm used by this credential */
    val algorithm: Fido2Algorithm,
    /** Callback to perform actual signing on FIDO2 device */
    val signingCallback: Fido2SigningCallback
) : PrivateKey {

    override fun getAlgorithm(): String = when (algorithm) {
        Fido2Algorithm.EDDSA -> "Ed25519"
        Fido2Algorithm.ES256 -> "EC"
    }

    override fun getFormat(): String = "FIDO2"

    override fun getEncoded(): ByteArray? {
        // FIDO2 private keys are not extractable
        return null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Fido2PrivateKey) return false
        return credentialId.contentEquals(other.credentialId) &&
            rpId == other.rpId &&
            algorithm == other.algorithm
    }

    override fun hashCode(): Int {
        var result = credentialId.contentHashCode()
        result = 31 * result + rpId.hashCode()
        result = 31 * result + algorithm.hashCode()
        return result
    }
}

/**
 * Callback interface for FIDO2 signing operations.
 *
 * This is called when the SSH library needs to sign data for authentication.
 * The implementation should handle connecting to the FIDO2 device and
 * performing the signing operation.
 */
interface Fido2SigningCallback {
    /**
     * Sign the given data using the FIDO2 device.
     *
     * @param data The data to sign (typically the SSH session ID + auth request)
     * @return The signature bytes in SSH format (including flags and counter for SK keys),
     *         or null if signing failed
     */
    fun sign(data: ByteArray): ByteArray?
}
