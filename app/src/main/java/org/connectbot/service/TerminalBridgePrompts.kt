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

package org.connectbot.service

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Extension methods for TerminalBridge to handle prompts.
 * These methods bridge between the old promptHelper (semaphore-based)
 * and the new promptManager (coroutine-based with StateFlow).
 */

/**
 * Request a boolean prompt (yes/no dialog).
 * This method updates both promptManager (for modern UI) and promptHelper (for backward compatibility).
 *
 * @param instructions Optional instructions to display
 * @param message The prompt message
 * @return Boolean response from user, null if cancelled by user or no answer
 */
fun TerminalBridge.requestBooleanPrompt(
    instructions: String?,
    message: String,
): Boolean? {
    return try {
        runBlocking {
            promptManager.requestBooleanPrompt(instructions, message)
        }
    } catch (e: CancellationException) {
        // Prompt was cancelled due to connection loss
        Log.w("TerminalBridge", "Attempted prompt on a closed stream.")
        return null
    }
}

/**
 * Request a string prompt (text input dialog).
 * This method updates both promptManager (for modern UI) and promptHelper (for backward compatibility).
 *
 * @param instructions Optional instructions to display
 * @param hint Hint text for the input field
 * @param isPassword Whether to mask the input
 * @return String response from user, or null if cancelled by user or no response
 */
fun TerminalBridge.requestStringPrompt(
    instructions: String?,
    hint: String?,
    isPassword: Boolean = false,
): String? {
    return try {
        runBlocking {
            promptManager.requestStringPrompt(instructions, hint, isPassword)
        }
    } catch (e: CancellationException) {
        // Prompt was cancelled due to connection loss - throw IOException to propagate error
        Log.w("TerminalBridge", "Attempted prompt on a closed stream.")
        return null
    }
}

/**
 * Request biometric authentication for an Android Keystore key.
 *
 * @param keyNickname The nickname of the key for display
 * @param keystoreAlias The alias of the key in Android Keystore
 * @return true if biometric authentication succeeded, false otherwise
 */
fun TerminalBridge.requestBiometricAuth(
    keyNickname: String,
    keystoreAlias: String
): Boolean {
    return try {
        runBlocking {
            promptManager.requestBiometricAuth(keyNickname, keystoreAlias)
        }
    } catch (e: CancellationException) {
        // Prompt was cancelled due to connection loss - throw IOException to propagate error
        Log.w("TerminalBridge", "Attempted prompt on a closed stream.")
        return false
    }
}

/**
 * Request host key fingerprint verification prompt.
 *
 * @param hostname The hostname being connected to
 * @param keyType The type of the key (e.g., "RSA", "ED25519")
 * @param keySize The size of the key in bits
 * @param serverHostKey The raw public key blob
 * @param randomArt The OpenSSH RandomArt ASCII visualization
 * @param bubblebabble The Bubble-Babble phonetic encoding
 * @param sha256 The SHA-256 fingerprint
 * @param md5 The MD5 fingerprint
 * @return Boolean response from user, or null if cancelled by user or no answer
 */
fun TerminalBridge.requestHostKeyFingerprintPrompt(
    hostname: String,
    keyType: String,
    keySize: Int,
    serverHostKey: ByteArray,
    randomArt: String,
    bubblebabble: String,
    sha256: String,
    md5: String
): Boolean? {
    return try {
        runBlocking {
            promptManager.requestHostKeyFingerprintPrompt(
                hostname, keyType, keySize, serverHostKey,
                randomArt, bubblebabble, sha256, md5
            )
        }
    } catch (e: CancellationException) {
        // Prompt was cancelled due to connection loss - throw IOException to propagate error
        Log.w("TerminalBridge", "Attempted prompt on a closed stream.")
        return null
    }
}
