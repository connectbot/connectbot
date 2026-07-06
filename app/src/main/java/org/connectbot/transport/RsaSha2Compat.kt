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

package org.connectbot.transport

import com.trilead.ssh2.Connection
import com.trilead.ssh2.ExtensionInfo
import com.trilead.ssh2.packets.PacketExtInfo
import com.trilead.ssh2.transport.TransportManager
import timber.log.Timber
import java.lang.reflect.Field
import java.security.KeyPair
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * Works around SSH authentication failures with RSA keys held in the Android Keystore
 * (upstream issues connectbot#1974 and connectbot#2189).
 *
 * Keystore-backed RSA keys cannot produce the legacy "ssh-rsa" (SHA-1) signature: the keys
 * are generated with SHA-2 digests only, and recent KeyMint implementations reject SHA-1
 * outright, so signing fails with KeyStoreException ("Incompatible digest" or
 * "Invalid argument"). sshlib only signs with rsa-sha2-256/512 (RFC 8332) when the server
 * advertises support through the "server-sig-algs" extension and otherwise falls back to
 * SHA-1, which can never succeed for these keys.
 *
 * When authenticating with an opaque (non-exportable) RSA private key against a server that
 * did not advertise rsa-sha2 support, this shim temporarily injects a synthetic
 * "server-sig-algs" extension so sshlib signs with SHA-2 instead. Servers too old to verify
 * rsa-sha2 signatures would fail either way, since SHA-1 signing is impossible for these
 * keys. Software keys are left untouched and keep the legacy SHA-1 fallback.
 */
object RsaSha2Compat {
    private const val SERVER_SIG_ALGS = "server-sig-algs"
    private val RSA_SHA2_ALGORITHMS = setOf("rsa-sha2-256", "rsa-sha2-512")

    /**
     * Runs [block] (typically a Connection.authenticateWithPublicKey call) with [connection]
     * treated as rsa-sha2-capable when [pair] requires it. No-op for software keys and for
     * servers that already advertised rsa-sha2 support; any previously seen extension info is
     * restored afterwards so other keys are negotiated exactly as before.
     */
    fun <T> withRsaSha2Preference(connection: Connection, pair: KeyPair, block: () -> T): T {
        val injection = injectRsaSha2ExtensionInfo(connection, pair) ?: return block()
        try {
            return block()
        } finally {
            injection.restore()
        }
    }

    /**
     * An RSA key needs the rsa-sha2 workaround when its private half is opaque (an Android
     * Keystore key exposes neither its encoding nor the [RSAPrivateKey] interface), because
     * SHA-1 signing is not possible with it.
     */
    fun needsRsaSha2(pair: KeyPair): Boolean {
        val privateKey = pair.private ?: return false
        return pair.public is RSAPublicKey && privateKey !is RSAPrivateKey
    }

    private fun injectRsaSha2ExtensionInfo(connection: Connection, pair: KeyPair): Injection? {
        if (!needsRsaSha2(pair)) {
            return null
        }

        return try {
            val tm = findField(Connection::class.java, "tm", TransportManager::class.java)
                .get(connection) as? TransportManager ?: return null

            val original = tm.extensionInfo
            if (original != null && original.signatureAlgorithmsAccepted.any { it in RSA_SHA2_ALGORITHMS }) {
                return null
            }

            val extensionInfoField = findField(TransportManager::class.java, "extensionInfo", ExtensionInfo::class.java)
            val synthetic = ExtensionInfo.fromPacketExtInfo(
                PacketExtInfo(mapOf(SERVER_SIG_ALGS to RSA_SHA2_ALGORITHMS.joinToString(","))),
            )
            extensionInfoField.set(tm, synthetic)

            Timber.i("Server did not advertise rsa-sha2 support; preferring rsa-sha2 for Android Keystore-backed RSA key")
            Injection(tm, extensionInfoField, original)
        } catch (e: Exception) {
            Timber.w(e, "Could not apply rsa-sha2 workaround for Android Keystore-backed RSA key")
            null
        }
    }

    private fun findField(clazz: Class<*>, name: String, type: Class<*>): Field {
        val field = try {
            clazz.getDeclaredField(name)
        } catch (e: NoSuchFieldException) {
            clazz.declaredFields.singleOrNull { it.type == type } ?: throw e
        }
        field.isAccessible = true
        return field
    }

    private class Injection(
        private val tm: TransportManager,
        private val field: Field,
        private val original: ExtensionInfo?,
    ) {
        fun restore() {
            try {
                field.set(tm, original)
            } catch (e: Exception) {
                Timber.w(e, "Could not restore server extension info after rsa-sha2 workaround")
            }
        }
    }
}
