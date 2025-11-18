/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

/**
 * Constants for SSH public/private key management.
 */
object PubkeyConstants {
    /**
     * Legacy database names for backup purposes.
     * Note: The actual database is now managed by Room as ConnectBot.db,
     * but this constant is kept for backward compatibility with backups.
     */
    const val LEGACY_PUBKEYS_DB_NAME: String = "pubkeys"

    /**
     * Key types supported by ConnectBot.
     */
    const val KEY_TYPE_RSA: String = "RSA"
    const val KEY_TYPE_DSA: String = "DSA"
    const val KEY_TYPE_EC: String = "EC"
    const val KEY_TYPE_ED25519: String = "Ed25519"
    const val KEY_TYPE_IMPORTED: String = "IMPORTED"
}
