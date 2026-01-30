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

import com.trilead.ssh2.auth.SignatureProxy
import kotlinx.coroutines.runBlocking
import org.connectbot.data.entity.Fido2Transport
import org.connectbot.fido2.Fido2Algorithm
import org.connectbot.fido2.Fido2Manager
import org.connectbot.fido2.Fido2Result
import org.connectbot.fido2.Fido2SignatureResult
import timber.log.Timber
import java.io.IOException

/**
 * SignatureProxy implementation for FIDO2 Security Key (SK) authentication.
 *
 * This proxy delegates signing operations to a FIDO2 hardware security key.
 * The signing is performed by the security key hardware, with the signature
 * encoded in SK format (including flags and counter).
 *
 * @param publicKey The SK public key (SkEd25519PublicKey or SkEcdsaPublicKey)
 * @param credentialId The credential ID on the security key
 * @param rpId The relying party ID (typically "ssh:")
 * @param algorithm The cryptographic algorithm used by this credential
 * @param pin The PIN for the security key
 * @param transport The preferred transport (USB or NFC)
 * @param fido2Manager The FIDO2 manager for device communication
 */
class Fido2SignatureProxy(
    publicKey: SkPublicKey,
    private val credentialId: ByteArray,
    private val rpId: String,
    private val algorithm: Fido2Algorithm,
    private val pin: String,
    private val transport: Fido2Transport,
    private val fido2Manager: Fido2Manager
) : SignatureProxy(publicKey) {

    /**
     * Sign the message using the FIDO2 security key.
     *
     * This method blocks while waiting for the FIDO2 device to sign.
     * The signature is returned in SSH SK format (including flags and counter).
     *
     * @param message The message to sign (session ID + auth request)
     * @param hashAlgorithm The hash algorithm (used for FIDO2 client data hash)
     * @return The encoded signature in SSH SK format
     * @throws IOException If signing fails
     */
    @Throws(IOException::class)
    override fun sign(message: ByteArray, hashAlgorithm: String): ByteArray {
        Timber.d("FIDO2 SignatureProxy.sign() called, algorithm=$algorithm, hashAlgorithm=$hashAlgorithm")

        // For FIDO2, we need to sign using the hardware device
        // This is a blocking call that waits for the device response
        val result = runBlocking {
            performFido2Signing(message)
        }

        return when (result) {
            is Fido2Result.Success -> {
                val signatureResult = result.value
                Timber.d("FIDO2 signing successful, encoding signature")
                encodeSignature(signatureResult)
            }

            is Fido2Result.PinInvalid -> {
                throw IOException("FIDO2 PIN is invalid. ${result.attemptsRemaining ?: "Unknown"} attempts remaining.")
            }

            is Fido2Result.PinLocked -> {
                throw IOException("FIDO2 PIN is locked. Please reset your security key.")
            }

            is Fido2Result.Error -> {
                throw IOException("FIDO2 signing failed: ${result.message}")
            }

            else -> {
                throw IOException("FIDO2 signing failed with unexpected result")
            }
        }
    }

    /**
     * Perform the actual FIDO2 signing operation.
     *
     * Uses the configured transport preference (USB or NFC).
     */
    private suspend fun performFido2Signing(challenge: ByteArray): Fido2Result<Fido2SignatureResult> = when (transport) {
        Fido2Transport.USB -> {
            Timber.d("Using USB signing (preferred transport)")
            performUsbSigning(challenge)
        }

        Fido2Transport.NFC -> {
            Timber.d("Using NFC signing (preferred transport)")
            performNfcSigning(challenge)
        }
    }

    /**
     * Perform signing via USB-connected device.
     */
    private suspend fun performUsbSigning(challenge: ByteArray): Fido2Result<Fido2SignatureResult> = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        fido2Manager.prepareSshSigning(credentialId, challenge) { result ->
            continuation.resumeWith(Result.success(result))
        }
        fido2Manager.connectAndSignUsb(pin)
    }

    /**
     * Perform signing via NFC tap.
     * This sets up the signing request and waits for the user to tap their NFC key.
     */
    private suspend fun performNfcSigning(challenge: ByteArray): Fido2Result<Fido2SignatureResult> = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        fido2Manager.prepareSshSigning(credentialId, challenge) { result ->
            continuation.resumeWith(Result.success(result))
        }
        // Request NFC tap - UI will observe waitingForNfcSigning and start NFC discovery
        fido2Manager.requestNfcSigning(pin)

        continuation.invokeOnCancellation {
            fido2Manager.cancelNfcSigning()
        }
    }

    /**
     * Encode the FIDO2 signature result to SSH SK signature format.
     */
    private fun encodeSignature(result: Fido2SignatureResult): ByteArray = when (algorithm) {
        Fido2Algorithm.EDDSA -> SkEd25519Verify.encodeSignature(result)
        Fido2Algorithm.ES256 -> SkEcdsaVerify.encodeSignature(result)
    }
}
