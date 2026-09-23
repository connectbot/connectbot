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

package org.connectbot.service.automation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.connectbot.R
import org.connectbot.data.entity.AutomationAction
import org.connectbot.data.entity.AutomationActionType
import org.connectbot.data.entity.AutomationFailurePolicy
import org.connectbot.util.TerminalKeyModifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationRunnerTest {
    private class Session : AutomationSession {
        override var sessionOpen = true
        val events = mutableListOf<String>()
        var reply: ((String) -> Unit)? = null
        var fail = false
        override suspend fun sendText(text: String) {
            if (fail) throw IllegalStateException()
            events.add(text)
            reply?.invoke(text)
        }
        override suspend fun sendKey(key: Int, modifiers: Int) {
            events.add("key:$key:$modifiers")
        }
        override suspend fun setForward(id: Long, enabled: Boolean) {
            events.add("forward:$id:$enabled")
        }
        override fun disconnect() {
            events.add("disconnect")
        }
    }

    private fun action(type: AutomationActionType, text: String = "", duration: Long = 1000) = AutomationAction(hostId = 1, type = type, text = text, durationMs = duration)

    @Test
    fun capturesImmediateReplyAndPreservesVerbatimSends() = runTest {
        val session = Session()
        val runner = AutomationRunner(session)
        session.reply = { runner.output.append("Ready: ") }
        runner.run(
            listOf(
                action(AutomationActionType.SEND_TEXT, "a\r\n\t"),
                action(AutomationActionType.WAIT_FOR_TEXT, "Ready: "),
                action(AutomationActionType.SEND_KEY).copy(key = -'C'.code, modifiers = TerminalKeyModifiers.CTRL),
                action(AutomationActionType.ENABLE_FORWARD).copy(forwardId = 9),
                action(AutomationActionType.DISABLE_FORWARD).copy(forwardId = 9),
                action(AutomationActionType.DISCONNECT),
                action(AutomationActionType.SEND_TEXT, "never"),
            ),
        )
        assertEquals(listOf("a\r\n\t", "key:${-'C'.code}:${TerminalKeyModifiers.CTRL}", "forward:9:true", "forward:9:false", "disconnect"), session.events)
        assertTrue(runner.state.value.completed)
    }

    @Test
    fun timeoutStopsByDefaultAndContinueRunsNextAction() = runTest {
        for (policy in AutomationFailurePolicy.entries) {
            val session = Session()
            val runner = AutomationRunner(session)
            runner.run(
                listOf(
                    action(AutomationActionType.WAIT_FOR_TEXT, "absent").copy(failurePolicy = policy),
                    action(AutomationActionType.SEND_TEXT, "next"),
                ),
            )
            assertEquals(if (policy == AutomationFailurePolicy.CONTINUE) listOf("next") else emptyList<String>(), session.events)
            assertEquals(R.string.automation_timed_out, runner.state.value.error)
            assertEquals(1, runner.state.value.failedStep)
        }
    }

    @Test
    fun initialPromptAndSplitAnsiAreMatchedAndConsumed() = runTest {
        val runner = AutomationRunner(Session())
        runner.output.append("\u001b[3")
        runner.output.append("1mPass")
        runner.output.append("word:\u001b[0m ")
        runner.run(listOf(action(AutomationActionType.WAIT_FOR_TEXT, "Password: ")))
        assertTrue(runner.state.value.completed)
        assertEquals("", runner.output.snapshot())
    }

    @Test
    fun sendDoesNotReuseStalePrompt() = runTest {
        val runner = AutomationRunner(Session())
        runner.output.append("old prompt")
        runner.run(listOf(action(AutomationActionType.SEND_TEXT, "go"), action(AutomationActionType.WAIT_FOR_TEXT, "old prompt")))
        assertEquals(R.string.automation_timed_out, runner.state.value.error)
    }

    @Test
    fun delayRetainsOutputForFollowingRegexWait() = runTest {
        val runner = AutomationRunner(Session())
        val job = launch { runner.run(listOf(action(AutomationActionType.DELAY), action(AutomationActionType.WAIT_FOR_TEXT, "hello.*世界").copy(regex = true))) }
        runCurrent()
        runner.output.append("hello 世界")
        advanceTimeBy(1000)
        runCurrent()
        assertTrue(runner.state.value.completed)
        job.join()
    }

    @Test
    fun cancellationStopsWaitAndPreventsSubsequentSend() = runTest {
        val session = Session()
        val runner = AutomationRunner(session)
        val job = launch { runner.run(listOf(action(AutomationActionType.WAIT_FOR_TEXT, "prompt"), action(AutomationActionType.SEND_TEXT, "never"))) }
        runCurrent()
        job.cancel()
        runCurrent()
        assertFalse(runner.state.value.running)
        assertTrue(session.events.isEmpty())
        assertNull(runner.state.value.error)
    }

    @Test
    fun overflowInterruptsDelayEvenWhenContinueIsSelected() = runTest {
        val runner = AutomationRunner(Session(), AutomationOutput(4))
        val job = launch { runner.run(listOf(action(AutomationActionType.DELAY).copy(failurePolicy = AutomationFailurePolicy.CONTINUE))) }
        runCurrent()
        runner.output.append("12345")
        runCurrent()
        assertEquals(R.string.automation_buffer_overflow, runner.state.value.error)
        assertFalse(runner.state.value.completed)
        job.join()
    }

    @Test
    fun writeFailureAndUnavailableSessionAreReported() = runTest {
        val session = Session().apply { fail = true }
        val runner = AutomationRunner(session)
        runner.run(listOf(action(AutomationActionType.SEND_TEXT, "secret")))
        assertEquals(R.string.automation_action_failed, runner.state.value.error)
        session.sessionOpen = false
        runner.run(listOf(action(AutomationActionType.SEND_TEXT, "secret")))
        assertEquals(R.string.automation_no_session, runner.state.value.error)
    }

    @Test
    fun ansiStringControlsAndCrLfAreNormalizedAcrossChunks() {
        val output = AutomationOutput()
        output.append("\u001b]0;hidden")
        output.append("\u001b\\shown\r")
        output.append("\n世界")
        assertEquals("shown\n世界", output.snapshot())
    }
}
