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

package org.connectbot.service

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.connectbot.data.entity.Fido2Transport
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class Fido2BridgePromptTest {
    @Test
    fun bridgePromptsDeliverValuesAndTreatCancelledPromptsAsFailure() = runBlocking {
        val bridge = mock(TerminalBridge::class.java)
        val prompts = PromptManager()
        `when`(bridge.promptManager).thenReturn(prompts)
        for (cancelled in listOf(false, true)) {
            val connection = CompletableFuture.supplyAsync { bridge.requestFido2Connect("key", byteArrayOf(1), Fido2Transport.USB) }
            prompts.promptState.first { it is PromptRequest.Fido2ConnectPrompt }
            if (cancelled) prompts.cancelPrompt() else prompts.respond(PromptResponse.Fido2Response(true))
            assertEquals(!cancelled, connection.get(10, TimeUnit.SECONDS))
            val pin = CompletableFuture.supplyAsync { bridge.requestFido2Pin("key", 3) }
            prompts.promptState.first { it is PromptRequest.Fido2PinPrompt }
            if (cancelled) prompts.cancelPrompt() else prompts.respond(PromptResponse.Fido2PinResponse("1234"))
            assertEquals(if (cancelled) null else "1234", pin.get(10, TimeUnit.SECONDS))
            val touch = CompletableFuture.supplyAsync { bridge.requestFido2Touch("key") }
            prompts.promptState.first { it is PromptRequest.Fido2TouchPrompt }
            if (cancelled) prompts.cancelPrompt() else prompts.respond(PromptResponse.Fido2Response(true))
            assertEquals(!cancelled, touch.get(10, TimeUnit.SECONDS))
        }
    }
}
