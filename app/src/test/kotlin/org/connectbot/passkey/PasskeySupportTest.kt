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
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import com.yubico.yubikit.fido.Cbor
import org.connectbot.BuildConfig
import org.connectbot.R
import org.connectbot.data.entity.KeyStorageType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey

@RunWith(RobolectricTestRunner::class)
class PasskeySupportTest {
    private val challenge = byteArrayOf(1, 2, 3)
    private val credentialId = byteArrayOf(4, 5, 6)
    private val origin = "android:apk-key-hash:test"

    private fun response(algorithm: Int = -7, flags: Byte = 0x45): String {
        val key = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public as ECPublicKey
        fun coordinate(value: BigInteger) = value.toByteArray().takeLast(32).toByteArray().let {
            ByteArray(32 - it.size) + it
        }
        val cose = Cbor.encode(mapOf(1 to 2, 3 to algorithm, -1 to 1, -2 to coordinate(key.w.affineX), -3 to coordinate(key.w.affineY)))
        val auth = MessageDigest.getInstance("SHA-256").digest(PasskeySupport.RP_ID.toByteArray()) +
            byteArrayOf(flags) + ByteArray(4) + ByteArray(16) +
            ByteBuffer.allocate(2).putShort(credentialId.size.toShort()).array() + credentialId + cose
        val client = JSONObject().put("type", "webauthn.create").put("challenge", PasskeySupport.encode(challenge)).put("origin", origin)
        return JSONObject().put("type", "public-key").put("rawId", PasskeySupport.encode(credentialId))
            .put(
                "response",
                JSONObject()
                    .put("clientDataJSON", PasskeySupport.encode(client.toString().toByteArray()))
                    .put("attestationObject", PasskeySupport.encode(Cbor.encode(mapOf("fmt" to "none", "authData" to auth, "attStmt" to emptyMap<String, Any>())))),
            )
            .toString()
    }

    @Test
    @Config(sdk = [24, 28, 34])
    fun supportAndErrorMatchFlavorAndAndroidVersion() {
        val resources = RuntimeEnvironment.getApplication().resources
        val minimumSdk = if (BuildConfig.FLAVOR == "google") 28 else 34
        val minimumAndroid = if (BuildConfig.FLAVOR == "google") 9 else 14
        assertEquals(
            "Passkeys require Android $minimumAndroid or later.",
            resources.getString(R.string.passkey_unsupported),
        )
        assertEquals(Build.VERSION.SDK_INT >= minimumSdk, PasskeySupport.isSupported)
        if (Build.VERSION.SDK_INT >= minimumSdk) {
            PasskeySupport.requireSupported(resources)
        } else {
            val error = assertThrows(IllegalArgumentException::class.java) {
                PasskeySupport.requireSupported(resources)
            }
            assertEquals(resources.getString(R.string.passkey_unsupported), error.message)
        }
    }

    @Test
    @Config(sdk = [28])
    @Suppress("DEPRECATION")
    fun originUsesCurrentSignerRatherThanLegacyCertificate() {
        val context = Mockito.mock(Context::class.java)
        val manager = Mockito.mock(PackageManager::class.java)
        val signingInfo = Mockito.mock(SigningInfo::class.java)
        val current = Signature(byteArrayOf(8))
        val info = PackageInfo().apply {
            signatures = arrayOf(Signature(byteArrayOf(9)))
            this.signingInfo = signingInfo
        }
        Mockito.`when`(context.packageName).thenReturn("org.connectbot")
        Mockito.`when`(context.packageManager).thenReturn(manager)
        Mockito.`when`(manager.getPackageInfo("org.connectbot", PackageManager.GET_SIGNING_CERTIFICATES)).thenReturn(info)
        Mockito.`when`(signingInfo.apkContentsSigners).thenReturn(arrayOf(current))
        assertEquals(
            "android:apk-key-hash:" + PasskeySupport.encode(MessageDigest.getInstance("SHA-256").digest(current.toByteArray())),
            PasskeySupport.origin(context),
        )
    }

    @Test
    fun manifestAssociationMatchesBuildRp() {
        val app = RuntimeEnvironment.getApplication()
        val association = JSONArray(app.getString(R.string.asset_statements))
        assertEquals(
            "https://" + PasskeySupport.RP_ID + "/.well-known/assetlinks.json",
            association.getJSONObject(0).getString("include"),
        )
        assertEquals(
            if (BuildConfig.DEBUG) "webauthn-debug.connectbot.org" else "webauthn.connectbot.org",
            PasskeySupport.RP_ID,
        )
    }

    @Test fun registrationStoresOnlyPublicSecurityKeyMetadata() {
        val key = PasskeySupport.registration(response(), challenge, origin, "SSH")
        assertEquals(KeyStorageType.PASSKEY, key.storageType)
        assertTrue(key.isSecurityKey)
        assertFalse(key.isFido2)
        assertNull(key.privateKey)
        assertFalse(key.allowBackup)
        assertArrayEquals(credentialId, key.credentialId)
        assertEquals(PasskeySupport.RP_ID, key.fido2RpId)
    }

    @Test fun registrationRejectsMismatches() {
        val json = response()
        assertThrows(IllegalArgumentException::class.java) { PasskeySupport.registration(json, byteArrayOf(9), origin, "SSH") }
        assertThrows(IllegalArgumentException::class.java) { PasskeySupport.registration(json, challenge, "other", "SSH") }
        assertThrows(IllegalArgumentException::class.java) { PasskeySupport.registration(response(-8), challenge, origin, "SSH") }
        assertThrows(IllegalArgumentException::class.java) { PasskeySupport.registration(response(flags = 0x41), challenge, origin, "SSH") }
    }
}
