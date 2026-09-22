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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.connectbot.terminal.InlineImageProtocolType
import org.connectbot.terminal.InlineImageRequest
import org.connectbot.terminal.InlineImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionInlineImagesTest {
    private val kitty = InlineImageRequest(InlineImageProtocolType.KITTY, "t")
    private val iterm = InlineImageRequest(InlineImageProtocolType.ITERM2, "File")

    @Test
    fun explicitPoliciesDoNotPrompt() {
        val session = SessionInlineImages { error("Unexpected prompt") }
        assertEquals(InlineImages.Off, session.policy("off"))
        assertTrue(session.policy("on") is InlineImages.On)
    }

    @Test
    fun answerIsRememberedAcrossProtocolsAndPolicyUpdates() = runTest {
        for (answer in listOf(true, false)) {
            var prompts = 0
            val session = SessionInlineImages {
                prompts++
                answer
            }
            assertEquals(answer, (session.policy("ask") as InlineImages.Ask).confirm(kitty))
            assertEquals(answer, (session.policy("ask") as InlineImages.Ask).confirm(iterm))
            assertEquals(1, prompts)
        }
    }

    @Test
    fun concurrentImagesShareOnePrompt() = runTest {
        var prompts = 0
        val response = CompletableDeferred<Boolean>()
        val session = SessionInlineImages {
            prompts++
            response.await()
        }
        val policy = session.policy("ask") as InlineImages.Ask
        val first = async { policy.confirm(kitty) }
        val second = async { policy.confirm(iterm) }
        response.complete(true)
        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(1, prompts)
    }

    @Test
    fun newSessionAsksAgainAndUnknownSettingsAsk() = runTest {
        val first = SessionInlineImages { true }
        assertTrue((first.policy("ask") as InlineImages.Ask).confirm(kitty))
        val second = SessionInlineImages { false }
        assertFalse((second.policy("unknown") as InlineImages.Ask).confirm(kitty))
    }
}
