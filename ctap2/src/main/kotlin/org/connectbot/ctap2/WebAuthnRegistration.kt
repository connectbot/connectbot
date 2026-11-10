/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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

import com.yubico.yubikit.fido.Cbor
import com.yubico.yubikit.fido.webauthn.AuthenticatorData
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.MessageDigest
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec

/** Decodes and validates public ES256 credential data from WebAuthn registration. */
object WebAuthnRegistration {
    fun decode(attestationObject: ByteArray, rpId: String, credentialId: ByteArray): Fido2Credential {
        val attestation = Cbor.decode(attestationObject) as Map<*, *>
        val auth = AuthenticatorData.parseFrom(ByteBuffer.wrap(attestation["authData"] as ByteArray))
        require(auth.rpIdHash.contentEquals(MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray())))
        require(auth.isUp && auth.isUv)
        val credential = requireNotNull(auth.attestedCredentialData)
        require(credential.credentialId.isNotEmpty())
        require(credentialId.contentEquals(credential.credentialId))
        val key = credential.cosePublicKey
        fun number(label: Int) = (key[label] as? Number)?.toInt()
        require(number(1) == 2 && number(3) == -7 && number(-1) == 1) { "Expected ES256 P-256 passkey" }
        fun coordinate(label: Int) = (key[label] as ByteArray).also { require(it.size == 32) }
        val x = coordinate(-2)
        val y = coordinate(-3)
        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)
        val pointX = BigInteger(1, x)
        val pointY = BigInteger(1, y)
        val prime = (params.curve.field as ECFieldFp).p
        require(
            pointX < prime && pointY < prime &&
                pointY.pow(2).mod(prime) == (pointX.pow(3) + params.curve.a * pointX + params.curve.b).mod(prime),
        ) { "Invalid P-256 public point" }
        val point = byteArrayOf(4) + x + y
        return Fido2Credential(
            credentialId = credential.credentialId,
            rpId = rpId,
            userHandle = null,
            userName = null,
            publicKeyBytes = point,
            algorithm = Fido2Algorithm.ES256,
        )
    }
}
