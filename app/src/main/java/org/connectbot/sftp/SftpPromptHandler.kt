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

package org.connectbot.sftp

/**
 * Interface for handling authentication prompts during SFTP connection.
 * Implementations should provide UI for user interaction.
 */
interface SftpPromptHandler {
    /**
     * Request a password from the user.
     *
     * @param message The prompt message to display
     * @return The password entered by the user, or null if cancelled
     */
    suspend fun requestPassword(message: String): String?

    /**
     * Request a passphrase for an encrypted private key.
     *
     * @param keyName The name/nickname of the key
     * @return The passphrase entered by the user, or null if cancelled
     */
    suspend fun requestKeyPassphrase(keyName: String): String?

    /**
     * Request user confirmation for a new or changed host key.
     *
     * @param hostname The hostname being connected to
     * @param keyType The type of the key (e.g., "RSA", "Ed25519")
     * @param fingerprint The key fingerprint
     * @param isNewKey True if this is a new key, false if the key has changed
     * @return True if the user accepts the key, false otherwise
     */
    suspend fun confirmHostKey(
        hostname: String,
        keyType: String,
        fingerprint: String,
        isNewKey: Boolean
    ): Boolean

    /**
     * Request biometric authentication for a key stored in Android Keystore.
     *
     * @param keyName The name/nickname of the key
     * @param keystoreAlias The Android Keystore alias
     * @return True if biometric authentication succeeded, false otherwise
     */
    suspend fun requestBiometricAuth(keyName: String, keystoreAlias: String): Boolean

    /**
     * Handle keyboard-interactive authentication prompts.
     *
     * @param name The name of the authentication request
     * @param instruction Instructions for the user
     * @param prompts The prompts to display
     * @param echoResponses Whether each response should be echoed
     * @return The responses to each prompt, or null if cancelled
     */
    suspend fun handleKeyboardInteractive(
        name: String,
        instruction: String,
        prompts: Array<String>,
        echoResponses: BooleanArray
    ): Array<String>?
}
