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

package org.connectbot.transport

import android.content.res.Resources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.service.DisconnectReason
import org.connectbot.service.PromptManager
import org.connectbot.service.PromptResponse
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.sshlib.AuthHandler
import org.connectbot.sshlib.AuthResult
import org.connectbot.sshlib.ConnectionInfo
import org.connectbot.sshlib.KeyboardInteractiveCallback
import org.connectbot.sshlib.SessionExit
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshSession
import org.connectbot.util.SecurePasswordStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SSHMigrationTest {
    private val fallbackHandler = mock(AuthHandler::class.java)

    private fun matchingHandler(): AuthHandler = any(AuthHandler::class.java) ?: fallbackHandler

    private val client = mock(SshClient::class.java)
    private val session = mock(SshSession::class.java)
    private val bridge = mock(TerminalBridge::class.java)

    private open inner class TestSSH : SSH() {
        var starts = 0
        init {
            client = this@SSHMigrationTest.client
            host = Host(username = "user", hostname = "server")
            bridge = this@SSHMigrationTest.bridge
            connected = true
        }
        suspend fun startSession() = super.finishConnection()
        override suspend fun finishConnection() {
            starts++
        }
    }

    @Test
    fun startup_nullSession_doesNotReportConnected() = runTest {
        val ssh = TestSSH()
        ssh.startSession()
        assertFalse(ssh.isSessionOpen())
        verify(bridge, never()).onConnected()
        verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }

    @Test
    fun startup_rejectedPty_closesWithoutRequestingShell() = runTest {
        `when`(client.openSession()).thenReturn(session)
        `when`(session.requestPty(eq("xterm") ?: "xterm", eq(0), eq(0), eq(0), eq(0), any(ByteArray::class.java) ?: byteArrayOf(0))).thenReturn(false)
        val ssh = TestSSH()
        ssh.startSession()
        assertFalse(ssh.isSessionOpen())
        verify(session, never()).requestShell()
        verify(session).close()
        verify(bridge, never()).onConnected()
        verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }

    @Test
    fun startup_rejectedShell_closesSession() = runTest {
        `when`(session.requestShell()).thenReturn(false)
        `when`(client.openSession()).thenReturn(session)
        `when`(session.requestPty(eq("xterm") ?: "xterm", eq(0), eq(0), eq(0), eq(0), any(ByteArray::class.java) ?: byteArrayOf(0))).thenReturn(true)
        val ssh = TestSSH()
        ssh.startSession()
        assertFalse(ssh.isSessionOpen())
        verify(session).close()
        verify(bridge, never()).onConnected()
        verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }

    @Test
    fun startup_success_opensSession() = runTest {
        `when`(client.openSession()).thenReturn(session)
        `when`(session.requestPty(eq("xterm") ?: "xterm", eq(0), eq(0), eq(0), eq(0), any(ByteArray::class.java) ?: byteArrayOf(0))).thenReturn(true)
        `when`(session.requestShell()).thenReturn(true)
        val ssh = TestSSH()
        ssh.startSession()
        assertTrue(ssh.isSessionOpen())
        verify(bridge).onConnected()
        verify(session, never()).close()
    }

    @Test
    fun startup_forwardingOnly_doesNotOpenSession() = runTest {
        val ssh = TestSSH().apply { host = host!!.copy(wantSession = false) }
        ssh.startSession()
        assertFalse(ssh.isSessionOpen())
        verify(client, never()).openSession()
        verify(bridge).onConnected()
    }

    @Test
    fun authentication_success_startsImmediatelyOnce() = runTest {
        `when`(client.authenticate(eq("user") ?: "user", matchingHandler())).thenReturn(AuthResult.Success)
        val ssh = TestSSH()
        ssh.authenticateConnection()
        assertEquals(1, ssh.starts)
        assertEquals(0, testScheduler.currentTime)
        verify(client).authenticate(eq("user") ?: "user", matchingHandler())
    }

    @Test
    fun authentication_error_stopsImmediately() = runTest {
        `when`(client.authenticate(eq("user") ?: "user", matchingHandler())).thenReturn(AuthResult.Error("Protocol failure"))
        TestSSH().authenticateConnection()
        verify(client).authenticate(eq("user") ?: "user", matchingHandler())
        verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
        assertEquals(0, testScheduler.currentTime)
    }

    @Test
    fun authentication_publicKeyOnlyFailure_doesNotRetry() = runTest {
        `when`(client.authenticate(eq("user") ?: "user", matchingHandler())).thenReturn(AuthResult.Failure(setOf("publickey")))
        TestSSH().authenticateConnection()
        verify(client).authenticate(eq("user") ?: "user", matchingHandler())
        verify(bridge).dispatchDisconnect(DisconnectReason.AUTH_FAIL)
    }

    @Test
    fun authentication_passwordFailure_retriesThenSucceeds() = runTest {
        `when`(client.authenticate(eq("user") ?: "user", matchingHandler())).thenReturn(AuthResult.Failure(setOf("password")), AuthResult.Success)
        val ssh = TestSSH()
        ssh.authenticateConnection()
        verify(client, times(2)).authenticate(eq("user") ?: "user", matchingHandler())
        assertEquals(1, ssh.starts)
        assertEquals(1000, testScheduler.currentTime)
    }

    @Test
    fun authentication_repeatedRejection_stopsAtLimit() = runTest {
        `when`(client.authenticate(eq("user") ?: "user", matchingHandler())).thenReturn(AuthResult.Failure(setOf("keyboard-interactive")))
        TestSSH().authenticateConnection()
        verify(client, times(20)).authenticate(eq("user") ?: "user", matchingHandler())
        verify(bridge).dispatchDisconnect(DisconnectReason.AUTH_FAIL)
        assertEquals(19000, testScheduler.currentTime)
    }

    @Test
    fun authentication_exhaustedMethods_stopsImmediately() = runTest {
        `when`(client.authenticate(eq("user") ?: "user", matchingHandler())).thenReturn(AuthResult.Failure(emptySet()))
        TestSSH().authenticateConnection()
        verify(client).authenticate(eq("user") ?: "user", matchingHandler())
        verify(bridge).dispatchDisconnect(DisconnectReason.AUTH_FAIL)
    }

    @Test
    fun authentication_usernameCancel_reportsUserRequest() = runTest {
        val prompts = PromptManager()
        `when`(bridge.promptManager).thenReturn(prompts)
        val ssh = TestSSH().apply { host = host!!.copy(username = "") }
        val job = launch { ssh.authenticateConnection() }
        runCurrent()
        prompts.cancelPrompt()
        job.join()
        verify(bridge).dispatchDisconnect(DisconnectReason.USER_REQUESTED)
        verify(client, never()).authenticate(eq("user") ?: "user", matchingHandler())
    }

    @Test
    fun authentication_parentCancellation_doesNotDisconnect() = runTest {
        val prompts = PromptManager()
        `when`(bridge.promptManager).thenReturn(prompts)
        val ssh = TestSSH().apply { host = host!!.copy(username = "") }
        val job = launch { ssh.authenticateConnection() }
        runCurrent()
        job.cancel()
        job.join()
        verify(bridge, never()).dispatchDisconnect(any(DisconnectReason::class.java) ?: DisconnectReason.IO_ERROR)
    }

    @Test
    fun authentication_librarySwallowsParentCancellation_stillPropagates() = runTest {
        val ssh = object : TestSSH() {
            override suspend fun authenticate(): AuthResult = try {
                awaitCancellation()
            } catch (e: CancellationException) {
                AuthResult.Error("Cancelled", e)
            }
        }
        val job = launch { ssh.authenticateConnection() }
        runCurrent()
        job.cancel()
        job.join()
        verify(bridge, never()).dispatchDisconnect(any(DisconnectReason::class.java) ?: DisconnectReason.IO_ERROR)
    }

    @Test
    fun jumpAuthentication_error_doesNotRetry() = runTest {
        `when`(client.authenticate(eq("user") ?: "user", matchingHandler())).thenReturn(AuthResult.Error("Protocol failure"))
        assertFalse(TestSSH().authenticateJumpHost(client, Host(username = "user")))
        verify(client).authenticate(eq("user") ?: "user", matchingHandler())
    }

    @Test
    fun jumpAuthentication_passwordFailure_retries() = runTest {
        `when`(client.authenticate(eq("user") ?: "user", matchingHandler())).thenReturn(AuthResult.Failure(setOf("password")), AuthResult.Success)
        assertTrue(TestSSH().authenticateJumpHost(client, Host(username = "user")))
        verify(client, times(2)).authenticate(eq("user") ?: "user", matchingHandler())
    }
    private suspend fun captureHandler(ssh: SSH): AuthHandler {
        lateinit var handler: AuthHandler
        `when`(client.authenticate(eq("user") ?: "user", matchingHandler())).thenAnswer { invocation ->
            handler = invocation.getArgument(1)
            AuthResult.Failure(emptySet())
        }
        ssh.authenticate()
        return handler
    }

    @Test
    fun authentication_passwordCancel_stopsRatherThanRetrying() = runTest {
        val prompts = PromptManager()
        `when`(bridge.promptManager).thenReturn(prompts)
        val ssh = TestSSH()
        val handler = captureHandler(ssh)
        val prompt = async { runCatching { handler.onPasswordNeeded() } }
        runCurrent()
        prompts.cancelPrompt()
        assertTrue(prompt.await().isFailure)
        ssh.authenticateConnection()
        verify(bridge).dispatchDisconnect(DisconnectReason.USER_REQUESTED)
    }

    @Test
    fun authentication_keyboardInteractiveCancel_doesNotSubmitEmptyPassword() = runTest {
        val prompts = PromptManager()
        `when`(bridge.promptManager).thenReturn(prompts)
        val ssh = TestSSH()
        val handler = captureHandler(ssh)
        val prompt = async {
            runCatching {
                handler.onKeyboardInteractivePrompt("login", "", listOf(KeyboardInteractiveCallback.Prompt("Password", false)))
            }
        }
        runCurrent()
        prompts.respond(PromptResponse.StringResponse(null))
        assertTrue(prompt.await().isFailure)
        ssh.authenticateConnection()
        verify(bridge).dispatchDisconnect(DisconnectReason.USER_REQUESTED)
    }

    @Test
    fun authentication_savedPassword_isUsedOnceAcrossAttempts() = runTest {
        val prompts = PromptManager()
        val manager = mock(TerminalManager::class.java)
        val storage = mock(SecurePasswordStorage::class.java)
        `when`(manager.securePasswordStorage).thenReturn(storage)
        `when`(storage.getPassword(0)).thenReturn("saved")
        `when`(bridge.promptManager).thenReturn(prompts)
        val ssh = TestSSH().apply { setManager(manager) }
        assertEquals("saved", captureHandler(ssh).onPasswordNeeded())
        val secondHandler = captureHandler(ssh)
        val password = async { secondHandler.onPasswordNeeded() }
        runCurrent()
        prompts.respond(PromptResponse.StringResponse("fresh"))
        assertEquals("fresh", password.await())
    }

    @Test
    fun jumpAuthentication_promptCancellation_returnsFailureWithoutRetry() = runTest {
        val prompts = PromptManager()
        `when`(bridge.promptManager).thenReturn(prompts)
        val ssh = TestSSH()
        val job = async { ssh.authenticateJumpHost(client, Host(username = "")) }
        runCurrent()
        prompts.cancelPrompt()
        assertFalse(job.await())
    }

    @Test
    fun connectionLoss_waitsForRelayCompletionAndDoesNotDispatchTwice() = runTest {
        `when`(client.openSession()).thenReturn(session)
        `when`(session.requestPty(eq("xterm") ?: "xterm", eq(0), eq(0), eq(0), eq(0), any(ByteArray::class.java) ?: byteArrayOf(0))).thenReturn(true)
        `when`(session.requestShell()).thenReturn(true)
        `when`(session.exitInfo).thenReturn(CompletableDeferred<SessionExit?>(SessionExit.Status(0)))
        val ssh = TestSSH()
        ssh.startSession()
        val observer = launch { ssh.handleConnectionLost(null) }
        runCurrent()
        verify(bridge, never()).dispatchDisconnect(any(DisconnectReason::class.java) ?: DisconnectReason.IO_ERROR)
        ssh.onOutputComplete()
        observer.join()
        verify(bridge).dispatchDisconnect(DisconnectReason.SESSION_EXIT)
    }

    @Test
    fun authenticationFailure_localCloseDoesNotOverwriteReason() = runTest {
        `when`(client.authenticate(eq("user") ?: "user", matchingHandler())).thenReturn(AuthResult.Failure(emptySet()))
        val ssh = TestSSH()
        ssh.authenticateConnection()
        assertFalse(ssh.isConnected())
        verify(client).disconnect()
        ssh.handleConnectionLost(null)
        verify(bridge).dispatchDisconnect(DisconnectReason.AUTH_FAIL)
        verify(bridge, never()).dispatchDisconnect(DisconnectReason.REMOTE_EOF)
    }

    @Test
    fun authentication_connectionLossCancelsPrompt_preservesExistingReason() = runTest {
        val prompts = PromptManager()
        `when`(bridge.promptManager).thenReturn(prompts)
        val ssh = TestSSH().apply { host = host!!.copy(username = "") }
        val job = launch { ssh.authenticateConnection() }
        runCurrent()
        `when`(bridge.isDisconnected).thenReturn(true)
        prompts.cancelPrompt()
        job.join()
        assertFalse(ssh.isConnected())
        verify(client).disconnect()
        verify(bridge, never()).dispatchDisconnect(any(DisconnectReason::class.java) ?: DisconnectReason.IO_ERROR)
    }
    private fun prepareAuthMessages(ssh: SSH) {
        val manager = mock(TerminalManager::class.java)
        val resources = mock(Resources::class.java)
        `when`(manager.res).thenReturn(resources)
        `when`(resources.getString(R.string.terminal_auth_ki)).thenReturn("Attempting keyboard-interactive authentication")
        `when`(resources.getString(R.string.terminal_auth_success)).thenReturn("Authentication successful.")
        ssh.setManager(manager)
    }

    @Test
    fun keyboardInteractive_multipleRoundsAndEmptyRequest_announcesAttemptOnce() = runTest {
        val ssh = TestSSH()
        prepareAuthMessages(ssh)
        val prompts = PromptManager()
        `when`(bridge.promptManager).thenReturn(prompts)
        val handler = captureHandler(ssh)
        for (text in listOf("Password", "Verification code")) {
            val response = async {
                handler.onKeyboardInteractivePrompt("login", "", listOf(KeyboardInteractiveCallback.Prompt(text, true)))
            }
            runCurrent()
            prompts.respond(PromptResponse.StringResponse("response"))
            assertEquals(listOf("response"), response.await())
        }
        assertEquals(emptyList<String>(), handler.onKeyboardInteractivePrompt("login", "", emptyList()))
        verify(bridge).outputLine("Attempting keyboard-interactive authentication")
        verify(bridge, never()).outputLine("Authentication successful.")

        captureHandler(ssh).onKeyboardInteractivePrompt("login", "", emptyList())
        verify(bridge, times(2)).outputLine("Attempting keyboard-interactive authentication")
    }

    @Test
    fun authentication_success_announcesConfirmedResult() = runTest {
        val ssh = TestSSH()
        prepareAuthMessages(ssh)
        `when`(client.authenticate(eq("user") ?: "user", matchingHandler())).thenReturn(AuthResult.Success)
        ssh.authenticateConnection()
        verify(bridge).outputLine("Authentication successful.")
        assertEquals(1, ssh.starts)
    }

    @Test
    fun authentication_failure_doesNotAnnounceSuccess() = runTest {
        val ssh = TestSSH()
        prepareAuthMessages(ssh)
        `when`(client.authenticate(eq("user") ?: "user", matchingHandler())).thenReturn(AuthResult.Failure(emptySet()))
        ssh.authenticateConnection()
        verify(bridge, never()).outputLine("Authentication successful.")
    }

    @Test
    fun connectionInfo_retainsLatestNegotiationAfterClose() = runTest {
        val initial = ConnectionInfo("curve25519-sha256", "ssh-ed25519", "aes128-ctr", "aes128-ctr", "hmac-sha2-256", "hmac-sha2-256")
        val rekeyed = initial.copy(encryptionAlgorithmC2S = "aes256-ctr")
        `when`(client.connectionInfo).thenReturn(initial)
        val ssh = TestSSH()
        assertEquals(initial, ssh.getSshConnectionInfo())
        `when`(client.connectionInfo).thenReturn(rekeyed)
        ssh.close()
        assertEquals(rekeyed, ssh.getSshConnectionInfo())
    }
}
