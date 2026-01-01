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

import kotlinx.coroutines.CompletableDeferred
import org.connectbot.data.entity.Fido2Transport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Modern prompt manager using Kotlin coroutines instead of semaphores and blocking.
 * Manages prompts for password, host verification, etc.
 */
class PromptManager {
    private val _promptState = MutableStateFlow<PromptRequest?>(null)
    val promptState: StateFlow<PromptRequest?> = _promptState.asStateFlow()

    private var currentDeferred: CompletableDeferred<PromptResponse>? = null

    /**
     * Request a boolean prompt (yes/no dialog)
     */
    suspend fun requestBooleanPrompt(
        instructions: String?,
        message: String
    ): Boolean {
        val deferred = CompletableDeferred<PromptResponse>()
        currentDeferred = deferred

        _promptState.update {
            PromptRequest.BooleanPrompt(
                instructions = instructions,
                message = message
            )
        }

        val response = deferred.await()
        _promptState.update { null }

        return (response as? PromptResponse.BooleanResponse)?.value ?: false
    }

    /**
     * Request a string prompt (text input dialog)
     */
    suspend fun requestStringPrompt(
        instructions: String?,
        hint: String?,
        isPassword: Boolean = false
    ): String? {
        val deferred = CompletableDeferred<PromptResponse>()
        currentDeferred = deferred

        _promptState.update {
            PromptRequest.StringPrompt(
                instructions = instructions,
                hint = hint,
                isPassword = isPassword
            )
        }

        val response = deferred.await()
        _promptState.update { null }

        return (response as? PromptResponse.StringResponse)?.value
    }

    /**
     * Request biometric authentication for a key stored in Android Keystore
     */
    suspend fun requestBiometricAuth(
        keyNickname: String,
        keystoreAlias: String
    ): Boolean {
        val deferred = CompletableDeferred<PromptResponse>()
        currentDeferred = deferred

        _promptState.update {
            PromptRequest.BiometricPrompt(
                keyNickname = keyNickname,
                keystoreAlias = keystoreAlias
            )
        }

        val response = deferred.await()
        _promptState.update { null }

        return (response as? PromptResponse.BiometricResponse)?.success ?: false
    }

    /**
     * Request FIDO2 security key connection for authentication
     */
    suspend fun requestFido2Connect(
        keyNickname: String,
        credentialId: ByteArray,
        transport: Fido2Transport
    ): Boolean {
        val deferred = CompletableDeferred<PromptResponse>()
        currentDeferred = deferred

        _promptState.update {
            PromptRequest.Fido2ConnectPrompt(
                keyNickname = keyNickname,
                credentialId = credentialId,
                transport = transport
            )
        }

        val response = deferred.await()
        _promptState.update { null }

        return (response as? PromptResponse.Fido2Response)?.success ?: false
    }

    /**
     * Request FIDO2 PIN entry
     */
    suspend fun requestFido2Pin(
        keyNickname: String,
        attemptsRemaining: Int? = null
    ): String? {
        val deferred = CompletableDeferred<PromptResponse>()
        currentDeferred = deferred

        _promptState.update {
            PromptRequest.Fido2PinPrompt(
                keyNickname = keyNickname,
                attemptsRemaining = attemptsRemaining
            )
        }

        val response = deferred.await()
        _promptState.update { null }

        return (response as? PromptResponse.Fido2PinResponse)?.pin
    }

    /**
     * Request user to touch FIDO2 security key for confirmation
     */
    suspend fun requestFido2Touch(
        keyNickname: String
    ): Boolean {
        val deferred = CompletableDeferred<PromptResponse>()
        currentDeferred = deferred

        _promptState.update {
            PromptRequest.Fido2TouchPrompt(
                keyNickname = keyNickname
            )
        }

        val response = deferred.await()
        _promptState.update { null }

        return (response as? PromptResponse.Fido2Response)?.success ?: false
    }

    /**
     * Request host key fingerprint verification prompt
     */
    suspend fun requestHostKeyFingerprintPrompt(
        hostname: String,
        keyType: String,
        keySize: Int,
        serverHostKey: ByteArray,
        randomArt: String,
        bubblebabble: String,
        sha256: String,
        md5: String
    ): Boolean {
        val deferred = CompletableDeferred<PromptResponse>()
        currentDeferred = deferred

        _promptState.update {
            PromptRequest.HostKeyFingerprintPrompt(
                hostname = hostname,
                keyType = keyType,
                keySize = keySize,
                serverHostKey = serverHostKey,
                randomArt = randomArt,
                bubblebabble = bubblebabble,
                sha256 = sha256,
                md5 = md5
            )
        }

        val response = deferred.await()
        _promptState.update { null }

        return (response as? PromptResponse.BooleanResponse)?.value ?: false
    }

    /**
     * Respond to the current prompt
     */
    fun respond(response: PromptResponse) {
        currentDeferred?.complete(response)
        currentDeferred = null
    }

    /**
     * Cancel the current prompt
     */
    fun cancelPrompt() {
        currentDeferred?.cancel()
        currentDeferred = null
        _promptState.update { null }
    }
}

/**
 * Represents a prompt request
 */
sealed class PromptRequest {
    data class BooleanPrompt(
        val instructions: String?,
        val message: String
    ) : PromptRequest()

    data class StringPrompt(
        val instructions: String?,
        val hint: String?,
        val isPassword: Boolean
    ) : PromptRequest()

    data class BiometricPrompt(
        val keyNickname: String,
        val keystoreAlias: String
    ) : PromptRequest()

    /** Prompt to connect a FIDO2 security key */
    data class Fido2ConnectPrompt(
        val keyNickname: String,
        val credentialId: ByteArray,
        val transport: Fido2Transport
    ) : PromptRequest() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Fido2ConnectPrompt) return false
            return keyNickname == other.keyNickname &&
                credentialId.contentEquals(other.credentialId) &&
                transport == other.transport
        }

        override fun hashCode(): Int {
            var result = keyNickname.hashCode()
            result = 31 * result + credentialId.contentHashCode()
            result = 31 * result + transport.hashCode()
            return result
        }
    }

    /** Prompt for FIDO2 PIN entry */
    data class Fido2PinPrompt(
        val keyNickname: String,
        val attemptsRemaining: Int?
    ) : PromptRequest()

    /** Prompt to touch FIDO2 security key */
    data class Fido2TouchPrompt(
        val keyNickname: String
    ) : PromptRequest()

    data class HostKeyFingerprintPrompt(
        val hostname: String,
        val keyType: String,
        val keySize: Int,
        val serverHostKey: ByteArray,
        val randomArt: String,
        val bubblebabble: String,
        val sha256: String,
        val md5: String
    ) : PromptRequest() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is HostKeyFingerprintPrompt) return false
            return hostname == other.hostname &&
                keyType == other.keyType &&
                keySize == other.keySize &&
                serverHostKey.contentEquals(other.serverHostKey) &&
                randomArt == other.randomArt &&
                bubblebabble == other.bubblebabble &&
                sha256 == other.sha256 &&
                md5 == other.md5
        }

        override fun hashCode(): Int {
            var result = hostname.hashCode()
            result = 31 * result + keyType.hashCode()
            result = 31 * result + keySize
            result = 31 * result + serverHostKey.contentHashCode()
            result = 31 * result + randomArt.hashCode()
            result = 31 * result + bubblebabble.hashCode()
            result = 31 * result + sha256.hashCode()
            result = 31 * result + md5.hashCode()
            return result
        }
    }
}

/**
 * Represents a prompt response
 */
sealed class PromptResponse {
    data class BooleanResponse(val value: Boolean) : PromptResponse()
    data class StringResponse(val value: String?) : PromptResponse()
    data class BiometricResponse(val success: Boolean) : PromptResponse()

    /** Response for FIDO2 connect/touch prompts */
    data class Fido2Response(val success: Boolean) : PromptResponse()

    /** Response for FIDO2 PIN prompt */
    data class Fido2PinResponse(val pin: String?) : PromptResponse()
}
