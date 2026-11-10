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

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.connectbot.BuildConfig
import org.connectbot.R
import org.connectbot.ctap2.WebAuthnRegistration
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.service.PromptRequest
import org.connectbot.service.PromptResponse
import org.connectbot.sshlib.sk.SkAlgorithm
import org.connectbot.sshlib.sk.SkAuthHelpers
import org.connectbot.sshlib.webauthn.WebAuthnAuth
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

object PasskeySupport {
    const val RP_ID = BuildConfig.PASSKEY_RP_ID
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= 34 ||
            (Build.VERSION.SDK_INT >= 28 && BuildConfig.FLAVOR == "google")

    fun requireSupported(resources: Resources) {
        require(isSupported) { resources.getString(R.string.passkey_unsupported) }
    }

    fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, FLAGS)
    private fun decode(value: String): ByteArray = Base64.decode(value, FLAGS)

    fun origin(context: Context): String {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            ).signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        return "android:apk-key-hash:" + encode(
            MessageDigest.getInstance("SHA-256").digest(requireNotNull(signatures).first().toByteArray()),
        )
    }

    internal fun client(root: JSONObject, type: String, challenge: ByteArray, origin: String) {
        require(root.getString("type") == "public-key")
        val data = JSONObject(String(decode(root.getJSONObject("response").getString("clientDataJSON")), Charsets.UTF_8))
        require(data.getString("type") == type)
        require(decode(data.getString("challenge")).contentEquals(challenge)) { "Passkey challenge mismatch" }
        require(data.getString("origin") == origin) { "Passkey origin mismatch" }
        require(!data.optBoolean("crossOrigin", false))
    }

    fun registration(json: String, challenge: ByteArray, origin: String, nickname: String): Pubkey {
        val root = JSONObject(json)
        client(root, "webauthn.create", challenge, origin)
        val credential = WebAuthnRegistration.decode(
            decode(root.getJSONObject("response").getString("attestationObject")),
            RP_ID,
            decode(root.getString("rawId")),
        )
        return fromPublicKey(credential.credentialId, credential.publicKeyBytes, nickname)
    }

    internal fun fromPublicKey(credentialId: ByteArray, point: ByteArray, nickname: String): Pubkey {
        val ssh = SkAuthHelpers.buildAuthPublicKey(SkAlgorithm.ECDSA_P256, point, RP_ID)
        return Pubkey(
            nickname = nickname, type = "SK-ECDSA", privateKey = null, publicKey = ssh.publicKeyBlob,
            encrypted = false, startup = false, confirmation = false, createdDate = System.currentTimeMillis(),
            storageType = KeyStorageType.PASSKEY, allowBackup = false,
            credentialId = credentialId, fido2RpId = RP_ID,
        )
    }

    fun signature(json: String, key: Pubkey, challenge: ByteArray): ByteArray {
        val root = JSONObject(json)
        require(decode(root.getString("rawId")).contentEquals(requireNotNull(key.credentialId))) { "Passkey credential mismatch" }
        return WebAuthnAuth.signatureFromResponseJson(json, requireNotNull(key.fido2RpId), challenge)
    }

    suspend fun get(context: Context, requestJson: String): String {
        val result = CredentialManager.create(context).getCredential(
            context,
            GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson))),
        )
        return (result.credential as PublicKeyCredential).authenticationResponseJson
    }

    suspend fun create(context: Context, nickname: String): Pair<String, ByteArray> {
        val challenge = ByteArray(32).also(SecureRandom()::nextBytes)
        val user = ByteArray(32).also(SecureRandom()::nextBytes)
        val options = JSONObject()
            .put("challenge", encode(challenge))
            .put("rp", JSONObject().put("id", RP_ID).put("name", "ConnectBot"))
            .put("user", JSONObject().put("id", encode(user)).put("name", nickname).put("displayName", nickname))
            .put("pubKeyCredParams", JSONArray().put(JSONObject().put("type", "public-key").put("alg", -7)))
            .put("authenticatorSelection", JSONObject().put("residentKey", "required").put("requireResidentKey", true).put("userVerification", "required"))
            .put("attestation", "none")
        val result = CredentialManager.create(context).createCredential(context, CreatePublicKeyCredentialRequest(options.toString()))
        return (result as CreatePublicKeyCredentialResponse).registrationResponseJson to challenge
    }
}

@Composable
fun PasskeyPromptHandler(prompt: PromptRequest.PasskeyPrompt, onResult: (PromptResponse.PasskeyResponse) -> Unit) {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    LaunchedEffect(prompt.id) {
        try {
            val result = CredentialManager.create(context).getCredential(context, GetCredentialRequest(listOf(GetPublicKeyCredentialOption(prompt.requestJson))))
            val json = (result.credential as PublicKeyCredential).authenticationResponseJson
            val root = JSONObject(json)
            val client = JSONObject(String(Base64.decode(root.getJSONObject("response").getString("clientDataJSON"), Base64.URL_SAFE), Charsets.UTF_8))
            require(client.getString("origin") == withContext(Dispatchers.IO) { PasskeySupport.origin(context) })
            currentOnResult(PromptResponse.PasskeyResponse(prompt.id, json))
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            currentOnResult(PromptResponse.PasskeyResponse(prompt.id, null))
        } catch (e: Exception) {
            currentOnResult(PromptResponse.PasskeyResponse(prompt.id, null, e.message ?: "Passkey authentication failed"))
        }
    }
}
