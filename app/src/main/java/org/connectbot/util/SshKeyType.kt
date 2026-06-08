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

package org.connectbot.util

enum class SshKeyType(
    val storedName: String,
    val keyFactoryAlgorithm: String?,
) {
    RSA("RSA", "RSA"),
    DSA("DSA", "DSA"),
    EC("EC", "EC"),
    ED25519("Ed25519", null),
    LEGACY_IMPORTED("IMPORTED", null),
    ;

    companion object {
        fun fromStoredType(value: String?): SshKeyType? = when (val normalizedValue = value?.lowercase()) {
            "rsa", "ssh-rsa", "rsa-sha2-256", "rsa-sha2-512" -> RSA
            "dsa", "ssh-dss" -> DSA
            "ec", "ecdsa" -> EC
            "ed25519", "eddsa", "ssh-ed25519" -> ED25519
            "imported" -> LEGACY_IMPORTED
            else -> if (normalizedValue?.startsWith("ecdsa-sha2-") == true) EC else null
        }

        fun fromOpenSshType(value: String): SshKeyType? = when (value) {
            "ssh-rsa", "rsa-sha2-256", "rsa-sha2-512" -> RSA
            "ssh-dss" -> DSA
            "ssh-ed25519" -> ED25519
            else -> if (value.startsWith("ecdsa-sha2-")) EC else null
        }

        fun fromJavaAlgorithm(value: String): SshKeyType? = when (value.lowercase()) {
            "rsa" -> RSA
            "dsa" -> DSA
            "ec", "ecdsa" -> EC
            "ed25519", "eddsa" -> ED25519
            else -> null
        }
    }
}
