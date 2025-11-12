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
}

/**
 * Represents a prompt response
 */
sealed class PromptResponse {
    data class BooleanResponse(val value: Boolean) : PromptResponse()
    data class StringResponse(val value: String?) : PromptResponse()
}
