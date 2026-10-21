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

package org.connectbot.data.entity

import androidx.room.ColumnInfo
import org.connectbot.sshlib.AlgorithmCategory
import org.connectbot.sshlib.CryptoPolicyPreset
import org.connectbot.sshlib.SshCryptoPolicy

/** Only user intent is persisted. Preset contents and capabilities belong to cbssh. */
data class HostCryptoConfig(
    @ColumnInfo(name = "crypto_policy", defaultValue = "'DEFAULT'")
    val preset: String = "DEFAULT",
    @ColumnInfo(name = "kex_algorithms")
    val kex: String? = null,
    @ColumnInfo(name = "cipher_algorithms")
    val cipher: String? = null,
    @ColumnInfo(name = "mac_algorithms")
    val mac: String? = null,
    @ColumnInfo(name = "host_key_algorithms")
    val hostKey: String? = null,
    @ColumnInfo(name = "pubkey_algorithms")
    val pubkey: String? = null,
    @ColumnInfo(name = "minimum_rsa_bits")
    val minimumRsaBits: Int? = null,
    @ColumnInfo(name = "minimum_dh_bits")
    val minimumDhBits: Int? = null,
) : java.io.Serializable {
    fun expression(category: AlgorithmCategory): String? = when (category) {
        AlgorithmCategory.KEX -> kex
        AlgorithmCategory.CIPHER -> cipher
        AlgorithmCategory.MAC -> mac
        AlgorithmCategory.HOST_KEY -> hostKey
        AlgorithmCategory.PUBKEY -> pubkey
    }

    fun withExpression(category: AlgorithmCategory, value: String?): HostCryptoConfig {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
        return when (category) {
            AlgorithmCategory.KEX -> copy(kex = normalized)
            AlgorithmCategory.CIPHER -> copy(cipher = normalized)
            AlgorithmCategory.MAC -> copy(mac = normalized)
            AlgorithmCategory.HOST_KEY -> copy(hostKey = normalized)
            AlgorithmCategory.PUBKEY -> copy(pubkey = normalized)
        }
    }

    /** Caller runs provider initialization and resolution off the main thread. Unknown preset IDs fail closed. */
    fun resolve(): SshCryptoPolicy = if (preset == "CUSTOM") {
        SshCryptoPolicy.resolve(
            overrides = AlgorithmCategory.entries.mapNotNull { category -> expression(category)?.let { category to it } }.toMap(),
            minimumRsaBits = minimumRsaBits,
            minimumDhBits = minimumDhBits,
        )
    } else {
        SshCryptoPolicy.resolve(CryptoPolicyPreset.valueOf(preset))
    }
}
