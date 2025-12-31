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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.connectbot.data.entity.Fido2Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Fido2PromptManagerTest {
    @Test
    fun connectionAndPinPromptsResumeWithResponsesAndClearState() = runTest {
        val manager = PromptManager()
        for (success in listOf(true, false)) {
            val connection = async { manager.requestFido2Connect("key", byteArrayOf(1), Fido2Transport.USB) }
            runCurrent()
            assertTrue(manager.promptState.value is PromptRequest.Fido2ConnectPrompt)
            manager.respond(PromptResponse.Fido2Response(success))
            assertEquals(success, connection.await())
            assertNull(manager.promptState.value)
            val pin = async { manager.requestFido2Pin("key", 3) }
            runCurrent()
            if (success) manager.respond(PromptResponse.Fido2PinResponse("1234")) else manager.respond(PromptResponse.BooleanResponse(false))
            assertEquals(if (success) "1234" else null, pin.await())
        }
        val mismatch = async { manager.requestFido2Connect("key", byteArrayOf(1), Fido2Transport.NFC) }
        runCurrent()
        manager.respond(PromptResponse.Fido2PinResponse("1234"))
        assertEquals(false, mismatch.await())
    }
}
