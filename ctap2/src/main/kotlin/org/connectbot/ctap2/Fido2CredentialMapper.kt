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

import com.yubico.yubikit.fido.ctap.CredentialManagement

internal object Fido2CredentialMapper {
    private const val COSE_ALGORITHM_KEY = 3
    private const val ERR_MISSING_CREDENTIAL_ID = "Missing credential ID"

    fun fromCredentialData(
        credData: CredentialManagement.CredentialData,
        rpData: CredentialManagement.RpData,
    ): Fido2Credential {
        val user = credData.user
        val credentialId = credData.credentialId["id"] as? ByteArray
            ?: error(ERR_MISSING_CREDENTIAL_ID)
        val publicKey = credData.publicKey

        val coseKey: Map<*, *> = publicKey
        val algValue = coseKey[COSE_ALGORITHM_KEY]
        val algorithm = when (algValue) {
            is Int -> Fido2Algorithm.fromCoseId(algValue.toLong())
            is Long -> Fido2Algorithm.fromCoseId(algValue)
            else -> null
        } ?: error("Unsupported algorithm: $algValue")

        return Fido2Credential(
            credentialId = credentialId,
            rpId = rpData.rp["id"] as? String ?: error("Missing relying party ID"),
            userHandle = user["id"] as? ByteArray,
            userName = user["name"] as? String,
            publicKeyBytes = extractPublicKey(publicKey, algorithm),
            algorithm = algorithm,
        )
    }

    private fun extractPublicKey(key: Map<*, *>, algorithm: Fido2Algorithm): ByteArray {
        fun number(label: Int): Long = (key[label] as? Number)?.toLong()
            ?: throw IllegalArgumentException("Missing or invalid COSE parameter $label")
        fun coordinate(label: Int): ByteArray = (key[label] as? ByteArray)?.also {
            require(it.size == 32) { "COSE coordinate $label must be 32 bytes" }
        } ?: throw IllegalArgumentException("Missing or invalid COSE coordinate $label")

        return when (algorithm) {
            Fido2Algorithm.EDDSA -> {
                require(number(1) == 1L && number(-1) == 6L) { "Expected Ed25519 COSE key" }
                coordinate(-2).copyOf()
            }

            Fido2Algorithm.ES256 -> {
                require(number(1) == 2L && number(-1) == 1L) { "Expected P-256 COSE key" }
                byteArrayOf(4) + coordinate(-2) + coordinate(-3)
            }
        }
    }
}
