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

package org.connectbot.passkey

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.connectbot.service.PromptManager
import org.connectbot.service.PromptRequest
import org.connectbot.service.PromptResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class PasskeyPromptTest {
    @Test
    fun ignoresStaleResponsesAndClearsCompletedPrompt() = runBlocking {
        val manager = PromptManager()
        val result = async { manager.requestPasskey("{}") }
        val prompt = manager.promptState.first { it is PromptRequest.PasskeyPrompt } as PromptRequest.PasskeyPrompt
        manager.respond(PromptResponse.PasskeyResponse(UUID.randomUUID(), "stale"))
        assertFalse(result.isCompleted)
        manager.respondPasskey(prompt.id, "assertion")
        assertEquals("assertion", result.await())
        assertNull(manager.promptState.value)
    }

    @Test
    fun cancellationClearsPrompt() = runBlocking {
        val manager = PromptManager()
        val result = async { manager.requestPasskey("{}") }
        manager.promptState.first { it is PromptRequest.PasskeyPrompt }
        result.cancel()
        result.join()
        assertNull(manager.promptState.value)
    }
}
