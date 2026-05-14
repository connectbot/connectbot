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

import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.junit.Test
import java.security.KeyPairGenerator

class SftpAuthKeyCacheTest {

    private var nowMillis = 1_000L
    private val cache = SftpAuthKeyCache { nowMillis }
    private val keyPair = KeyPairGenerator.getInstance("RSA").run {
        initialize(1024)
        generateKeyPair()
    }

    @Test
    fun biometricKey_isReusedByKeystoreAliasBeforeAuthWindowExpires() {
        val pubkey = biometricPubkey(id = 1, nickname = "jump-key", keystoreAlias = "shared-alias")
        val sameKeystoreKey = biometricPubkey(id = 2, nickname = "target-key", keystoreAlias = "shared-alias")

        cache.put(pubkey, keyPair)

        assertThat(cache.get(sameKeystoreKey)).isSameAs(keyPair)
    }

    @Test
    fun biometricKey_expiresWithAuthWindow() {
        val pubkey = biometricPubkey(id = 1, nickname = "key", keystoreAlias = "alias")

        cache.put(pubkey, keyPair)
        nowMillis += 30_000L

        assertThat(cache.get(pubkey)).isNull()
    }

    @Test
    fun exportableKey_isKeptUntilRemoved() {
        val pubkey = exportablePubkey(id = 1, nickname = "key")

        cache.put(pubkey, keyPair)
        nowMillis += 60_000L

        assertThat(cache.get(pubkey)).isSameAs(keyPair)

        cache.remove(pubkey)

        assertThat(cache.get(pubkey)).isNull()
    }

    private fun biometricPubkey(id: Long, nickname: String, keystoreAlias: String) = Pubkey(
        id = id,
        nickname = nickname,
        type = "RSA",
        privateKey = null,
        publicKey = byteArrayOf(1),
        encrypted = false,
        startup = false,
        confirmation = false,
        createdDate = 0L,
        storageType = KeyStorageType.ANDROID_KEYSTORE,
        allowBackup = false,
        keystoreAlias = keystoreAlias,
    )

    private fun exportablePubkey(id: Long, nickname: String) = Pubkey(
        id = id,
        nickname = nickname,
        type = "RSA",
        privateKey = byteArrayOf(1),
        publicKey = byteArrayOf(1),
        encrypted = false,
        startup = false,
        confirmation = false,
        createdDate = 0L,
        storageType = KeyStorageType.EXPORTABLE,
        allowBackup = true,
        keystoreAlias = null,
    )
}
