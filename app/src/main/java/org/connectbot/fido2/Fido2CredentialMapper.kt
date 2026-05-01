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

import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.NegativeInteger
import co.nstant.`in`.cbor.model.UnsignedInteger
import com.yubico.yubikit.fido.ctap.CredentialManagement
import java.io.ByteArrayOutputStream

internal object Fido2CredentialMapper {
    private const val SSH_RP_ID = "ssh:"
    private const val COSE_ALGORITHM_KEY = 3
    private const val ERR_MISSING_CREDENTIAL_ID = "Missing credential ID"

    fun isSshRp(rpData: CredentialManagement.RpData): Boolean {
        val rpId = rpData.rp["id"] as? String
        return rpId == SSH_RP_ID || rpId?.startsWith(SSH_RP_ID) == true
    }

    fun fromCredentialData(
        credData: CredentialManagement.CredentialData,
        sshRp: CredentialManagement.RpData,
    ): Fido2Credential {
        val user = credData.user
        val credentialId = credData.credentialId["id"] as? ByteArray
            ?: error(ERR_MISSING_CREDENTIAL_ID)
        val publicKey = credData.publicKey

        @Suppress("UNCHECKED_CAST")
        val pubKeyMap = publicKey as? Map<Int, Any>
        val algValue = pubKeyMap?.get(COSE_ALGORITHM_KEY)
        val algorithm = when (algValue) {
            Fido2Algorithm.ES256.coseId, Fido2Algorithm.ES256.coseId.toLong() -> Fido2Algorithm.ES256
            Fido2Algorithm.EDDSA.coseId, Fido2Algorithm.EDDSA.coseId.toLong() -> Fido2Algorithm.EDDSA
            else -> error("Unsupported algorithm: $algValue")
        }

        return Fido2Credential(
            credentialId = credentialId,
            rpId = sshRp.rp["id"] as? String ?: SSH_RP_ID,
            userHandle = user["id"] as? ByteArray,
            userName = user["name"] as? String,
            publicKeyCose = encodeCoseKey(publicKey),
            algorithm = algorithm,
        )
    }

    private fun encodeCoseKey(publicKey: Map<*, *>): ByteArray {
        val output = ByteArrayOutputStream()
        val encoder = CborEncoder(output)
        val cborMap = co.nstant.`in`.cbor.model.Map()

        for ((key, value) in publicKey) {
            val cborKey = key?.toCborInteger() ?: continue
            val cborValue = when (value) {
                is ByteArray -> ByteString(value)
                is Int -> value.toLong().toCborInteger()
                is Long -> value.toCborInteger()
                else -> null
            } ?: continue

            cborMap.put(cborKey, cborValue)
        }

        encoder.encode(cborMap)
        return output.toByteArray()
    }

    private fun Any.toCborInteger() = when (this) {
        is Int -> toLong().toCborInteger()
        is Long -> toCborInteger()
        else -> null
    }

    private fun Long.toCborInteger() = if (this >= 0) {
        UnsignedInteger(this)
    } else {
        NegativeInteger(this)
    }
}
