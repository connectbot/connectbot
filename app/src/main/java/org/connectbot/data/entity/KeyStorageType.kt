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

package org.connectbot.data.entity

/**
 * Defines how an SSH key is stored and managed.
 */
enum class KeyStorageType {
    /**
     * Traditional encrypted key stored in the database.
     * Private key material is stored encrypted in the database.
     * Can be exported and backed up (if allowBackup=true).
     */
    EXPORTABLE,

    /**
     * Key stored in Android Keystore (future Phase 2).
     * Only the key alias is stored in the database.
     * Private key material never leaves the secure hardware.
     * Cannot be backed up or exported.
     */
    ANDROID_KEYSTORE,

    /**
     * FIDO2 resident key stored on an external security key (YubiKey, SoloKey, etc.).
     * Only the credential ID and public key are stored in the database.
     * Private key material never leaves the security key hardware.
     * Signing operations require the security key to be connected via USB or NFC.
     * Cannot be backed up or exported.
     */
    FIDO2_RESIDENT_KEY
}
