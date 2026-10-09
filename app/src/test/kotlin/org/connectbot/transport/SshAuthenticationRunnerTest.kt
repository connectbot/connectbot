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

import com.trilead.ssh2.Connection
import com.trilead.ssh2.InteractiveCallback
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.IOException
import java.security.KeyPair
import java.security.KeyPairGenerator

class SshAuthenticationRunnerTest {
    private val connection = mock(Connection::class.java)
    private var methods = arrayOf("publickey")
    private var passwordPrompts = 0
    private var interactivePrompts = 0
    private var storedPassword: String? = null
    private var promptedPassword: String? = "entered"
    private var candidates = emptyList<AuthenticationIdentity>()
    private var identityLoads = 0

    private fun runner(): SshAuthenticationRunner {
        `when`(connection.getRemainingAuthMethods("alice")).thenAnswer { methods }
        return SshAuthenticationRunner(
            connection,
            "alice",
            identities = {
                identityLoads++
                candidates
            },
            savedPassword = { storedPassword },
            passwordPrompt = {
                passwordPrompts++
                promptedPassword
            },
            interactive = InteractiveCallback { _, _, count, _, _ ->
                interactivePrompts++
                Array(count) { "answer" }
            },
        )
    }

    @Test
    fun publicKeyOnlyWithoutKeys_exhaustsWithoutPasswordOrInteractiveRequests() {
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        verify(connection, times(1)).authenticateWithNone("alice")
        verify(connection, never()).authenticateWithPassword(anyString(), anyString())
        verify(connection, never()).authenticateWithKeyboardInteractive(anyString(), any(InteractiveCallback::class.java))
        assertThat(passwordPrompts).isZero()
    }

    @Test
    fun unsupportedMethods_doNotLoadOrOfferIdentities() {
        methods = arrayOf("hostbased")
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        assertThat(identityLoads).isZero()
    }

    @Test
    fun probesEachUniqueIdentity_andSignsOnlyAcceptedKey() {
        candidates = listOf(identity("first", firstKey), identity("duplicate", firstKey), identity("second", secondKey))
        `when`(connection.probePublicKey("alice", secondKey.public)).thenReturn(true)
        `when`(connection.authenticateWithPublicKey("alice", secondKey)).thenReturn(true)

        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Authenticated)

        val order = inOrder(connection)
        order.verify(connection).probePublicKey("alice", firstKey.public)
        order.verify(connection).probePublicKey("alice", secondKey.public)
        order.verify(connection).authenticateWithPublicKey("alice", secondKey)
        verify(connection, times(1)).probePublicKey("alice", firstKey.public)
        verify(connection, never()).authenticateWithPublicKey("alice", firstKey)
    }

    @Test
    fun methodRemovedAfterRejection_stopsOfferingKeys() {
        candidates = listOf(identity("first", firstKey), identity("second", secondKey))
        `when`(connection.probePublicKey("alice", firstKey.public)).thenAnswer {
            methods = emptyArray()
            false
        }
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        verify(connection, never()).probePublicKey("alice", secondKey.public)
    }

    @Test
    fun deniedAndExpiredIdentities_areSkipped() {
        candidates = listOf(
            AuthenticationIdentity("denied", firstKey, confirm = { false }),
            AuthenticationIdentity("expired", secondKey, isAvailable = { false }),
        )
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        verify(connection, never()).probePublicKey("alice", firstKey.public)
        verify(connection, never()).probePublicKey("alice", secondKey.public)
    }

    @Test
    fun identityExpiresAfterAcceptedProbe_doesNotSign() {
        var available = true
        candidates = listOf(AuthenticationIdentity("expiring", firstKey, isAvailable = { available }))
        `when`(connection.probePublicKey("alice", firstKey.public)).thenAnswer {
            available = false
            true
        }
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        verify(connection, never()).authenticateWithPublicKey("alice", firstKey)
    }

    @Test
    fun savedPassword_isTriedOnceAndCountsTowardLimit() {
        methods = arrayOf("password")
        storedPassword = "stored"
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        verify(connection, times(1)).authenticateWithPassword("alice", "stored")
        verify(connection, times(2)).authenticateWithPassword("alice", "entered")
        assertThat(passwordPrompts).isEqualTo(2)
    }

    @Test
    fun passwordRemovedAfterSavedPasswordFailure_doesNotPrompt() {
        methods = arrayOf("password")
        storedPassword = "stored"
        `when`(connection.authenticateWithPassword("alice", "stored")).thenAnswer {
            methods = emptyArray()
            false
        }
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        assertThat(passwordPrompts).isZero()
    }

    @Test
    fun interactiveWithoutChallenge_fallsBackToPasswordAfterOneAttempt() {
        methods = arrayOf("keyboard-interactive", "password")
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        verify(connection, times(1)).authenticateWithKeyboardInteractive(anyString(), any(InteractiveCallback::class.java))
        verify(connection, times(3)).authenticateWithPassword("alice", "entered")
    }

    @Test
    fun interactiveWithChallenges_stopsAfterThreeAndFallsBack() {
        methods = arrayOf("keyboard-interactive", "password")
        `when`(connection.authenticateWithKeyboardInteractive(anyString(), any(InteractiveCallback::class.java))).thenAnswer {
            it.getArgument<InteractiveCallback>(1).replyToChallenge("", "", 1, arrayOf("Password"), booleanArrayOf(false))
            false
        }
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        assertThat(interactivePrompts).isEqualTo(3)
        verify(connection, times(3)).authenticateWithPassword("alice", "entered")
    }

    @Test
    fun publicKeyPartialSuccess_continuesWithPassword() {
        candidates = listOf(identity("first", firstKey))
        `when`(connection.probePublicKey("alice", firstKey.public)).thenReturn(true)
        `when`(connection.authenticateWithPublicKey("alice", firstKey)).thenAnswer {
            methods = arrayOf("password")
            false
        }
        `when`(connection.isAuthenticationPartialSuccess).thenReturn(true)
        `when`(connection.authenticateWithPassword("alice", "entered")).thenReturn(true)
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Authenticated)
        verify(connection, times(1)).authenticateWithPublicKey("alice", firstKey)
    }

    @Test
    fun twoKeyAuthentication_reconsidersPreviouslyRejectedKeyAfterPartialSuccess() {
        candidates = listOf(identity("first", firstKey), identity("second", secondKey))
        `when`(connection.probePublicKey("alice", firstKey.public)).thenReturn(false, true)
        `when`(connection.probePublicKey("alice", secondKey.public)).thenReturn(true)
        `when`(connection.isAuthenticationPartialSuccess).thenReturn(true)
        `when`(connection.authenticateWithPublicKey("alice", firstKey)).thenReturn(true)
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Authenticated)
        verify(connection, times(2)).probePublicKey("alice", firstKey.public)
        verify(connection, times(1)).authenticateWithPublicKey("alice", secondKey)
    }

    @Test
    fun cancelledPassword_doesNotSendAuthenticationRequest() {
        methods = arrayOf("password")
        promptedPassword = null
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Cancelled)
        verify(connection, never()).authenticateWithPassword(anyString(), anyString())
    }

    @Test
    fun transportFailure_stopsWithoutTryingNextIdentity() {
        candidates = listOf(identity("first", firstKey), identity("second", secondKey))
        `when`(connection.probePublicKey("alice", firstKey.public)).thenThrow(IOException("closed"))
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.ConnectionFailed)
        verify(connection, never()).probePublicKey("alice", secondKey.public)
    }

    @Test
    fun newRunner_hasFreshAttemptBudget() {
        methods = arrayOf("password")
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        verify(connection, times(6)).authenticateWithPassword("alice", "entered")
    }

    @Test
    fun noneAuthenticationSuccess_doesNotLoadKeysOrQueryMethods() {
        `when`(connection.authenticateWithNone("alice")).thenReturn(true)
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Authenticated)
        assertThat(identityLoads).isZero()
        verify(connection, never()).getRemainingAuthMethods("alice")
    }

    @Test
    fun cancelledInteractiveChallenge_stopsBeforePasswordFallback() {
        methods = arrayOf("keyboard-interactive", "password")
        `when`(connection.authenticateWithKeyboardInteractive(anyString(), any(InteractiveCallback::class.java)))
            .thenThrow(IOException("Callback failed", AuthenticationCancelledException()))
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Cancelled)
        verify(connection, never()).authenticateWithPassword(anyString(), anyString())
    }

    @Test
    fun interactiveSavedPassword_preservesOtherFieldsAndIsSubmittedOnce() {
        methods = arrayOf("keyboard-interactive")
        storedPassword = "stored"
        val replies = mutableListOf<List<String>>()
        `when`(connection.authenticateWithKeyboardInteractive(anyString(), any(InteractiveCallback::class.java))).thenAnswer {
            val callback = it.getArgument<InteractiveCallback>(1)
            replies += callback.replyToChallenge(
                "",
                "",
                3,
                arrayOf("Name", "Password", "OTP"),
                booleanArrayOf(true, false, false),
            ).toList()
            false
        }
        assertThat(runner().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        assertThat(replies).containsExactly(
            listOf("answer", "stored", "answer"),
            listOf("answer", "answer", "answer"),
            listOf("answer", "answer", "answer"),
        )
    }

    private fun identity(nickname: String, pair: KeyPair) = AuthenticationIdentity(nickname, pair)

    companion object {
        private val generator = KeyPairGenerator.getInstance("EC").apply { initialize(256) }
        private val firstKey = generator.generateKeyPair()
        private val secondKey = generator.generateKeyPair()
    }
}
