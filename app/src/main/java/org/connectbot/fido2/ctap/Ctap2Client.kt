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

package org.connectbot.fido2.ctap

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.NegativeInteger
import co.nstant.`in`.cbor.model.SimpleValue
import co.nstant.`in`.cbor.model.UnicodeString
import co.nstant.`in`.cbor.model.UnsignedInteger
import org.connectbot.fido2.Fido2Algorithm
import org.connectbot.fido2.Fido2AuthenticatorInfo
import org.connectbot.fido2.Fido2Credential
import org.connectbot.fido2.Fido2Result
import org.connectbot.fido2.Fido2SignatureResult
import org.connectbot.fido2.transport.Ctap2Commands
import org.connectbot.fido2.transport.Ctap2Status
import org.connectbot.fido2.transport.Ctap2Transport
import org.connectbot.fido2.transport.Ctap2TransportException
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CTAP2 protocol client implementation.
 *
 * Handles CBOR encoding/decoding and implements CTAP2 commands for:
 * - authenticatorGetInfo
 * - authenticatorClientPIN
 * - authenticatorCredentialManagement
 * - authenticatorGetAssertion
 */
class Ctap2Client(private val transport: Ctap2Transport) {

    private var pinToken: ByteArray? = null
    private var sharedSecret: ByteArray? = null

    /**
     * Get authenticator information.
     */
    suspend fun getInfo(): Fido2Result<Fido2AuthenticatorInfo> {
        return try {
            val command = byteArrayOf(Ctap2Commands.GET_INFO)
            val response = transport.sendCommand(command)

            if (response.isEmpty()) {
                return Fido2Result.Error("Empty response")
            }

            val status = response[0]
            if (status != Ctap2Status.OK) {
                return Fido2Result.Error(Ctap2Status.getMessage(status))
            }

            val cbor = decodeCbor(response.sliceArray(1 until response.size))
            Fido2Result.Success(parseAuthenticatorInfo(cbor))
        } catch (e: Exception) {
            Timber.e(e, "getInfo failed")
            Fido2Result.Error(e.message ?: "Unknown error", e)
        }
    }

    /**
     * Get PIN retries remaining.
     */
    suspend fun getPinRetries(): Fido2Result<Int> {
        return try {
            val params = CborBuilder()
                .addMap()
                .put(UnsignedInteger(1), UnsignedInteger(PIN_PROTOCOL_VERSION.toLong()))
                .put(UnsignedInteger(2), UnsignedInteger(CMD_GET_PIN_RETRIES.toLong()))
                .end()
                .build()

            val command = buildCommand(Ctap2Commands.CLIENT_PIN, params[0] as Map)
            val response = transport.sendCommand(command)

            val status = response[0]
            if (status != Ctap2Status.OK) {
                return Fido2Result.Error(Ctap2Status.getMessage(status))
            }

            val cbor = decodeCbor(response.sliceArray(1 until response.size)) as Map
            val retries = (cbor.get(UnsignedInteger(3)) as? UnsignedInteger)?.value?.toInt() ?: 8

            Fido2Result.Success(retries)
        } catch (e: Exception) {
            Timber.e(e, "getPinRetries failed")
            Fido2Result.Error(e.message ?: "Unknown error", e)
        }
    }

    /**
     * Get PIN token for authenticated operations.
     */
    suspend fun getPinToken(pin: String): Fido2Result<Unit> {
        return try {
            // Step 1: Get authenticator's key agreement key
            val keyAgreementResult = getKeyAgreement()
            if (keyAgreementResult is Fido2Result.Error) {
                return Fido2Result.Error(keyAgreementResult.message)
            }
            val authenticatorPublicKey = (keyAgreementResult as Fido2Result.Success).value

            // Step 2: Generate platform key pair and compute shared secret
            val keyPair = generateP256KeyPair()
            sharedSecret = computeSharedSecret(keyPair.private, authenticatorPublicKey)

            // Step 3: Encrypt PIN hash
            val pinHash = sha256(pin.toByteArray(Charsets.UTF_8)).sliceArray(0 until 16)
            val encryptedPinHash = encryptAesCbc(sharedSecret!!, pinHash)

            // Step 4: Get PIN token
            val platformPublicKey = encodeCosePublicKey(keyPair.public)

            val params = CborBuilder()
                .addMap()
                .put(UnsignedInteger(1), UnsignedInteger(PIN_PROTOCOL_VERSION.toLong()))
                .put(UnsignedInteger(2), UnsignedInteger(CMD_GET_PIN_TOKEN.toLong()))
                .put(UnsignedInteger(3), platformPublicKey)
                .put(UnsignedInteger(6), ByteString(encryptedPinHash))
                .end()
                .build()

            val command = buildCommand(Ctap2Commands.CLIENT_PIN, params[0] as Map)
            val response = transport.sendCommand(command)

            val status = response[0]
            when (status) {
                Ctap2Status.OK -> {
                    val cbor = decodeCbor(response.sliceArray(1 until response.size)) as Map
                    val encryptedPinToken = (cbor.get(UnsignedInteger(2)) as ByteString).bytes
                    pinToken = decryptAesCbc(sharedSecret!!, encryptedPinToken)
                    Fido2Result.Success(Unit)
                }
                Ctap2Status.PIN_INVALID -> {
                    val retries = getPinRetries()
                    val remaining = if (retries is Fido2Result.Success) retries.value else null
                    Fido2Result.PinInvalid(remaining)
                }
                Ctap2Status.PIN_BLOCKED -> Fido2Result.PinLocked("PIN is blocked")
                Ctap2Status.PIN_AUTH_BLOCKED -> Fido2Result.PinLocked("PIN auth blocked, reinsert authenticator")
                else -> Fido2Result.Error(Ctap2Status.getMessage(status))
            }
        } catch (e: Exception) {
            Timber.e(e, "getPinToken failed")
            Fido2Result.Error(e.message ?: "Unknown error", e)
        }
    }

    /**
     * Enumerate resident credentials for a specific relying party.
     */
    suspend fun enumerateCredentials(rpId: String): Fido2Result<List<Fido2Credential>> {
        if (pinToken == null) {
            return Fido2Result.PinRequired(null)
        }

        return try {
            val rpIdHash = sha256(rpId.toByteArray(Charsets.UTF_8))

            // Build credential management command for enumerate credentials
            val subParams = CborBuilder()
                .addMap()
                .put(UnsignedInteger(1), ByteString(rpIdHash))
                .end()
                .build()[0] as Map

            val subParamsBytes = encodeCbor(subParams)
            val pinAuth = computePinAuth(
                byteArrayOf(CMD_ENUMERATE_CREDENTIALS_BEGIN.toByte()) + subParamsBytes
            )

            val params = CborBuilder()
                .addMap()
                .put(UnsignedInteger(1), UnsignedInteger(CMD_ENUMERATE_CREDENTIALS_BEGIN.toLong()))
                .put(UnsignedInteger(2), subParams)
                .put(UnsignedInteger(3), UnsignedInteger(PIN_PROTOCOL_VERSION.toLong()))
                .put(UnsignedInteger(4), ByteString(pinAuth))
                .end()
                .build()

            val command = buildCommand(Ctap2Commands.CREDENTIAL_MANAGEMENT, params[0] as Map)
            val response = transport.sendCommand(command)

            val status = response[0]
            when (status) {
                Ctap2Status.OK -> {
                    val credentials = mutableListOf<Fido2Credential>()
                    val cbor = decodeCbor(response.sliceArray(1 until response.size)) as Map

                    // Parse first credential
                    parseCredential(cbor, rpId)?.let { credentials.add(it) }

                    // Get total count
                    val totalCount = (cbor.get(UnsignedInteger(9)) as? UnsignedInteger)?.value?.toInt() ?: 1

                    // Enumerate remaining credentials
                    for (i in 1 until totalCount) {
                        val nextResult = enumerateCredentialsNext()
                        if (nextResult is Fido2Result.Success) {
                            nextResult.value?.let { credentials.add(it) }
                        }
                    }

                    Fido2Result.Success(credentials)
                }
                Ctap2Status.NO_CREDENTIALS -> Fido2Result.Success(emptyList())
                Ctap2Status.PIN_AUTH_INVALID -> {
                    pinToken = null
                    Fido2Result.PinRequired(null)
                }
                else -> Fido2Result.Error(Ctap2Status.getMessage(status))
            }
        } catch (e: Exception) {
            Timber.e(e, "enumerateCredentials failed")
            Fido2Result.Error(e.message ?: "Unknown error", e)
        }
    }

    private suspend fun enumerateCredentialsNext(): Fido2Result<Fido2Credential?> {
        val params = CborBuilder()
            .addMap()
            .put(UnsignedInteger(1), UnsignedInteger(CMD_ENUMERATE_CREDENTIALS_NEXT.toLong()))
            .end()
            .build()

        val command = buildCommand(Ctap2Commands.CREDENTIAL_MANAGEMENT, params[0] as Map)
        val response = transport.sendCommand(command)

        val status = response[0]
        if (status != Ctap2Status.OK) {
            return Fido2Result.Error(Ctap2Status.getMessage(status))
        }

        val cbor = decodeCbor(response.sliceArray(1 until response.size)) as Map
        return Fido2Result.Success(parseCredential(cbor, ""))
    }

    /**
     * Get assertion (sign a challenge).
     */
    suspend fun getAssertion(
        rpId: String,
        clientDataHash: ByteArray,
        credentialId: ByteArray,
        requireUserVerification: Boolean = false
    ): Fido2Result<Fido2SignatureResult> {
        return try {
            val allowList = Array()
            val credDescriptor = Map()
            credDescriptor.put(UnicodeString("type"), UnicodeString("public-key"))
            credDescriptor.put(UnicodeString("id"), ByteString(credentialId))
            allowList.add(credDescriptor)

            val options = Map()
            options.put(UnicodeString("up"), SimpleValue.TRUE)
            if (requireUserVerification) {
                options.put(UnicodeString("uv"), SimpleValue.TRUE)
            }

            val paramsBuilder = CborBuilder()
                .addMap()
                .put(UnsignedInteger(1), UnicodeString(rpId))
                .put(UnsignedInteger(2), ByteString(clientDataHash))
                .put(UnsignedInteger(3), allowList)
                .put(UnsignedInteger(5), options)

            // Add PIN auth if we have a token and UV is required
            if (requireUserVerification && pinToken != null) {
                val pinAuth = computePinAuth(clientDataHash)
                paramsBuilder.put(UnsignedInteger(6), ByteString(pinAuth))
                paramsBuilder.put(UnsignedInteger(7), UnsignedInteger(PIN_PROTOCOL_VERSION.toLong()))
            }

            val params = paramsBuilder.end().build()
            val command = buildCommand(Ctap2Commands.GET_ASSERTION, params[0] as Map)
            val response = transport.sendCommand(command)

            val status = response[0]
            when (status) {
                Ctap2Status.OK -> {
                    val cbor = decodeCbor(response.sliceArray(1 until response.size)) as Map
                    Fido2Result.Success(parseAssertionResponse(cbor))
                }
                Ctap2Status.UP_REQUIRED -> Fido2Result.Error("Touch your security key")
                Ctap2Status.PIN_REQUIRED -> Fido2Result.PinRequired(null)
                Ctap2Status.PIN_AUTH_INVALID -> {
                    pinToken = null
                    Fido2Result.PinRequired(null)
                }
                Ctap2Status.NO_CREDENTIALS -> Fido2Result.Error("Credential not found on device")
                else -> Fido2Result.Error(Ctap2Status.getMessage(status))
            }
        } catch (e: Exception) {
            Timber.e(e, "getAssertion failed")
            Fido2Result.Error(e.message ?: "Unknown error", e)
        }
    }

    private suspend fun getKeyAgreement(): Fido2Result<java.security.PublicKey> {
        val params = CborBuilder()
            .addMap()
            .put(UnsignedInteger(1), UnsignedInteger(PIN_PROTOCOL_VERSION.toLong()))
            .put(UnsignedInteger(2), UnsignedInteger(CMD_GET_KEY_AGREEMENT.toLong()))
            .end()
            .build()

        val command = buildCommand(Ctap2Commands.CLIENT_PIN, params[0] as Map)
        val response = transport.sendCommand(command)

        val status = response[0]
        if (status != Ctap2Status.OK) {
            return Fido2Result.Error(Ctap2Status.getMessage(status))
        }

        val cbor = decodeCbor(response.sliceArray(1 until response.size)) as Map
        val coseKey = cbor.get(UnsignedInteger(1)) as Map

        return Fido2Result.Success(decodeCosePublicKey(coseKey))
    }

    private fun parseAuthenticatorInfo(cbor: DataItem): Fido2AuthenticatorInfo {
        val map = cbor as Map

        val versions = (map.get(UnsignedInteger(1)) as? Array)?.dataItems
            ?.filterIsInstance<UnicodeString>()
            ?.map { it.string }
            ?: emptyList()

        val aaguid = (map.get(UnsignedInteger(3)) as? ByteString)?.bytes

        val options = map.get(UnsignedInteger(4)) as? Map
        val pinConfigured = options?.get(UnicodeString("clientPin"))
            ?.let { it == SimpleValue.TRUE } ?: false
        val residentKeySupported = options?.get(UnicodeString("rk"))
            ?.let { it == SimpleValue.TRUE } ?: false
        val credMgmtSupported = options?.get(UnicodeString("credMgmt"))
            ?.let { it == SimpleValue.TRUE } ?: false

        val maxCredentialCount = (map.get(UnsignedInteger(7)) as? UnsignedInteger)?.value?.toInt()

        return Fido2AuthenticatorInfo(
            versions = versions,
            aaguid = aaguid,
            pinConfigured = pinConfigured,
            credentialManagementSupported = credMgmtSupported,
            residentKeySupported = residentKeySupported,
            maxCredentialCount = maxCredentialCount,
            remainingCredentialCount = null
        )
    }

    private fun parseCredential(cbor: Map, rpId: String): Fido2Credential? {
        val user = cbor.get(UnsignedInteger(6)) as? Map
        val credentialId = cbor.get(UnsignedInteger(7)) as? Map
        val publicKey = cbor.get(UnsignedInteger(8)) as? Map

        if (credentialId == null || publicKey == null) {
            return null
        }

        val credId = (credentialId.get(UnicodeString("id")) as? ByteString)?.bytes ?: return null
        val userHandle = (user?.get(UnicodeString("id")) as? ByteString)?.bytes
        val userName = (user?.get(UnicodeString("name")) as? UnicodeString)?.string

        // Get algorithm from public key
        val algValue = when (val alg = publicKey.get(UnsignedInteger(3))) {
            is NegativeInteger -> -1 - alg.value.toInt()
            is UnsignedInteger -> alg.value.toInt()
            else -> -7 // Default to ES256
        }
        val algorithm = Fido2Algorithm.fromCoseId(algValue) ?: Fido2Algorithm.ES256

        return Fido2Credential(
            credentialId = credId,
            rpId = rpId,
            userHandle = userHandle,
            userName = userName,
            publicKeyCose = encodeCbor(publicKey),
            algorithm = algorithm
        )
    }

    private fun parseAssertionResponse(cbor: Map): Fido2SignatureResult {
        val authData = (cbor.get(UnsignedInteger(2)) as ByteString).bytes
        val signature = (cbor.get(UnsignedInteger(3)) as ByteString).bytes

        // Parse flags from authenticator data (byte 32, after rpIdHash)
        val flags = if (authData.size > 32) authData[32] else 0
        val userPresence = (flags.toInt() and 0x01) != 0
        val userVerification = (flags.toInt() and 0x04) != 0

        // Parse counter (bytes 33-36, big endian)
        val counter = if (authData.size >= 37) {
            ((authData[33].toInt() and 0xFF) shl 24) or
            ((authData[34].toInt() and 0xFF) shl 16) or
            ((authData[35].toInt() and 0xFF) shl 8) or
            (authData[36].toInt() and 0xFF)
        } else {
            0
        }

        return Fido2SignatureResult(
            authenticatorData = authData,
            signature = signature,
            userPresenceVerified = userPresence,
            userVerified = userVerification,
            counter = counter
        )
    }

    private fun computePinAuth(message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pinToken, "HmacSHA256"))
        return mac.doFinal(message).sliceArray(0 until 16)
    }

    private fun buildCommand(cmd: Byte, params: Map): ByteArray {
        val cborBytes = encodeCbor(params)
        return byteArrayOf(cmd) + cborBytes
    }

    private fun encodeCbor(item: DataItem): ByteArray {
        val baos = ByteArrayOutputStream()
        CborEncoder(baos).encode(item)
        return baos.toByteArray()
    }

    private fun decodeCbor(data: ByteArray): DataItem {
        val bais = ByteArrayInputStream(data)
        return CborDecoder(bais).decode()[0]
    }

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    private fun generateP256KeyPair(): java.security.KeyPair {
        val keyGen = java.security.KeyPairGenerator.getInstance("EC")
        keyGen.initialize(java.security.spec.ECGenParameterSpec("secp256r1"), SecureRandom())
        return keyGen.generateKeyPair()
    }

    private fun computeSharedSecret(
        privateKey: java.security.PrivateKey,
        publicKey: java.security.PublicKey
    ): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        val secret = keyAgreement.generateSecret()
        return sha256(secret)
    }

    private fun encryptAesCbc(key: ByteArray, data: ByteArray): ByteArray {
        val iv = ByteArray(16) // Zero IV for PIN protocol
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun decryptAesCbc(key: ByteArray, data: ByteArray): ByteArray {
        val iv = ByteArray(16)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun encodeCosePublicKey(publicKey: java.security.PublicKey): Map {
        val ecKey = publicKey as java.security.interfaces.ECPublicKey
        val point = ecKey.w

        val xBytes = point.affineX.toByteArray().let { bytes ->
            if (bytes.size > 32) bytes.sliceArray(bytes.size - 32 until bytes.size)
            else if (bytes.size < 32) ByteArray(32 - bytes.size) + bytes
            else bytes
        }
        val yBytes = point.affineY.toByteArray().let { bytes ->
            if (bytes.size > 32) bytes.sliceArray(bytes.size - 32 until bytes.size)
            else if (bytes.size < 32) ByteArray(32 - bytes.size) + bytes
            else bytes
        }

        val coseKey = Map()
        coseKey.put(UnsignedInteger(1), UnsignedInteger(2)) // kty: EC2
        coseKey.put(UnsignedInteger(3), NegativeInteger(-7)) // alg: ES256 (-7)
        coseKey.put(NegativeInteger(-1), UnsignedInteger(1)) // crv: P-256
        coseKey.put(NegativeInteger(-2), ByteString(xBytes)) // x
        coseKey.put(NegativeInteger(-3), ByteString(yBytes)) // y

        return coseKey
    }

    private fun decodeCosePublicKey(coseKey: Map): java.security.PublicKey {
        val xBytes = (coseKey.get(NegativeInteger(-2)) as ByteString).bytes
        val yBytes = (coseKey.get(NegativeInteger(-3)) as ByteString).bytes

        val x = java.math.BigInteger(1, xBytes)
        val y = java.math.BigInteger(1, yBytes)

        val ecSpec = java.security.spec.ECGenParameterSpec("secp256r1")
        val keyFactory = java.security.KeyFactory.getInstance("EC")
        val params = java.security.AlgorithmParameters.getInstance("EC")
        params.init(ecSpec)
        val ecParams = params.getParameterSpec(java.security.spec.ECParameterSpec::class.java)

        val pubPoint = java.security.spec.ECPoint(x, y)
        val pubSpec = java.security.spec.ECPublicKeySpec(pubPoint, ecParams)

        return keyFactory.generatePublic(pubSpec)
    }

    companion object {
        private const val PIN_PROTOCOL_VERSION = 1

        // ClientPIN subcommands
        private const val CMD_GET_PIN_RETRIES = 0x01
        private const val CMD_GET_KEY_AGREEMENT = 0x02
        private const val CMD_SET_PIN = 0x03
        private const val CMD_CHANGE_PIN = 0x04
        private const val CMD_GET_PIN_TOKEN = 0x05

        // CredentialManagement subcommands
        private const val CMD_GET_CREDS_METADATA = 0x01
        private const val CMD_ENUMERATE_RPS_BEGIN = 0x02
        private const val CMD_ENUMERATE_RPS_NEXT = 0x03
        private const val CMD_ENUMERATE_CREDENTIALS_BEGIN = 0x04
        private const val CMD_ENUMERATE_CREDENTIALS_NEXT = 0x05
        private const val CMD_DELETE_CREDENTIAL = 0x06
    }
}
