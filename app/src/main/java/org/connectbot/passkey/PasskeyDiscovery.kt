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

package org.connectbot.passkey

import android.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.connectbot.ctap2.P256PublicKeyRecovery
import org.connectbot.data.entity.Pubkey
import org.connectbot.sshlib.webauthn.WebAuthnAuth
import org.json.JSONObject
import java.security.SecureRandom

/** Invoked only by an explicit import. The second request is pinned to the selected credential. */
object PasskeyDiscovery {
    private data class Response(val id: ByteArray, val assertion: P256PublicKeyRecovery.Assertion)

    suspend fun discover(
        origin: String,
        dispatcher: CoroutineDispatcher,
        request: suspend (String) -> String,
    ): Pubkey {
        val firstChallenge = withContext(dispatcher) { challenge() }
        val options = withContext(dispatcher) {
            JSONObject()
                .put("rpId", PasskeySupport.RP_ID)
                .put("challenge", PasskeySupport.encode(firstChallenge))
                .put("userVerification", "required")
                .toString()
        }
        val firstJson = request(options)
        val first = withContext(dispatcher) { parse(firstJson, firstChallenge, origin) }
        val secondChallenge = withContext(dispatcher) {
            challenge().also { require(!it.contentEquals(firstChallenge)) }
        }
        val secondOptions = withContext(dispatcher) {
            WebAuthnAuth.createRequestJson(PasskeySupport.RP_ID, first.id, secondChallenge)
        }
        val secondJson = request(secondOptions)
        return withContext(dispatcher) {
            val second = parse(secondJson, secondChallenge, origin)
            require(first.id.contentEquals(second.id)) { "The selected passkey changed" }
            val point = P256PublicKeyRecovery.recover(first.assertion, second.assertion)
            PasskeySupport.fromPublicKey(first.id, point, "")
        }
    }

    private fun challenge(): ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)

    private fun parse(json: String, challenge: ByteArray, origin: String): Response {
        val root = JSONObject(json)
        PasskeySupport.client(root, "webauthn.get", challenge, origin)
        // Also reject signed JSON encodings that cannot be used for OpenSSH authentication.
        WebAuthnAuth.signatureFromResponseJson(json, PasskeySupport.RP_ID, challenge)
        fun decode(value: String) = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP)
        val id = decode(root.getString("rawId"))
        require(id.isNotEmpty()) { "Missing credential ID" }
        if (root.has("id")) require(decode(root.getString("id")).contentEquals(id))
        val response = root.getJSONObject("response")
        val assertion = P256PublicKeyRecovery.assertion(
            decode(response.getString("authenticatorData")),
            decode(response.getString("clientDataJSON")),
            decode(response.getString("signature")),
            PasskeySupport.RP_ID,
        )
        return Response(id, assertion)
    }
}
