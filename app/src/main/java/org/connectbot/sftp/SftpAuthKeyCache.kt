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

package org.connectbot.sftp

import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import java.security.KeyPair

/**
 * Per-connection cache for keypairs unlocked while authenticating an SFTP
 * target and its ProxyJump chain.
 */
internal class SftpAuthKeyCache(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private data class CachedKeyPair(
        val pair: KeyPair,
        val expiresAtMillis: Long?,
    )

    private val keyPairs = mutableMapOf<String, CachedKeyPair>()

    fun get(pubkey: Pubkey): KeyPair? {
        val key = cacheKey(pubkey)
        val cached = keyPairs[key] ?: return null
        val expiresAtMillis = cached.expiresAtMillis

        if (expiresAtMillis != null && nowMillis() >= expiresAtMillis) {
            keyPairs.remove(key)
            return null
        }

        return cached.pair
    }

    fun put(pubkey: Pubkey, pair: KeyPair) {
        keyPairs[cacheKey(pubkey)] = CachedKeyPair(
            pair = pair,
            expiresAtMillis = if (pubkey.storageType == KeyStorageType.ANDROID_KEYSTORE) {
                nowMillis() + BIOMETRIC_AUTH_VALIDITY_MILLIS
            } else {
                null
            },
        )
    }

    fun remove(pubkey: Pubkey) {
        keyPairs.remove(cacheKey(pubkey))
    }

    private fun cacheKey(pubkey: Pubkey): String = when {
        pubkey.storageType == KeyStorageType.ANDROID_KEYSTORE && pubkey.keystoreAlias != null -> "keystore:${pubkey.keystoreAlias}"
        pubkey.id > 0 -> "id:${pubkey.id}"
        else -> "nickname:${pubkey.nickname}"
    }

    companion object {
        // Must match AUTH_VALIDITY_DURATION_SECONDS in BiometricKeyManager.
        private const val BIOMETRIC_AUTH_VALIDITY_MILLIS = 30_000L
    }
}
