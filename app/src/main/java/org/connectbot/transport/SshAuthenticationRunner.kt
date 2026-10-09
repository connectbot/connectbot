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
import com.trilead.ssh2.crypto.Base64
import com.trilead.ssh2.crypto.PublicKeyUtils
import java.io.IOException
import java.security.KeyPair

internal enum class AuthenticationOutcome {
    Authenticated,
    Exhausted,
    Cancelled,
    ConnectionFailed,
}

internal class AuthenticationCancelledException : IOException("Authentication cancelled")

internal class AuthenticationIdentity(
    val nickname: String,
    val pair: KeyPair,
    val isAvailable: () -> Boolean = { true },
    val confirm: () -> Boolean = { true },
    val prepare: () -> Boolean = { true },
) {
    val keyId: String = String(Base64.encode(PublicKeyUtils.extractPublicKeyBlob(pair.public)))
}

/** Runs on the connection worker. Server method lists are refreshed after every exchange. */
internal class SshAuthenticationRunner(
    private val connection: Connection,
    private val username: String,
    private val identities: () -> List<AuthenticationIdentity>,
    private val savedPassword: () -> String?,
    private val passwordPrompt: () -> String?,
    private val interactive: InteractiveCallback,
    private val onNoneComplete: () -> Unit = {},
    private val onMethod: (String) -> Unit = {},
    private val onKeyRejected: (String) -> Unit = {},
    private val onMethodRejected: (String) -> Unit = {},
    private val onFailure: (Exception) -> Unit = {},
) {
    fun authenticate(): AuthenticationOutcome = try {
        runAuthentication()
    } catch (e: Exception) {
        if (generateSequence<Throwable>(e) { it.cause }.any { it is AuthenticationCancelledException }) {
            AuthenticationOutcome.Cancelled
        } else {
            onFailure(e)
            AuthenticationOutcome.ConnectionFailed
        }
    }

    private fun runAuthentication(): AuthenticationOutcome {
        val noneSucceeded = try {
            connection.authenticateWithNone(username)
        } finally {
            onNoneComplete()
        }
        if (noneSucceeded) return AuthenticationOutcome.Authenticated

        val candidates by lazy { identities().distinctBy { it.keyId } }
        val attempted = mutableSetOf<String>()
        val completed = mutableSetOf<String>()
        val denied = mutableSetOf<String>()
        val confirmed = mutableSetOf<String>()
        var interactiveAttempts = 0
        var passwordAttempts = 0
        var interactiveEnabled = true
        var interactiveSavedPasswordTried = false
        var passwordSavedPasswordTried = false

        while (true) {
            if (connection.isAuthenticationComplete) return AuthenticationOutcome.Authenticated
            val methods = connection.getRemainingAuthMethods(username).toSet()
            val candidate = if ("publickey" in methods) {
                candidates.firstOrNull {
                    it.keyId !in attempted && it.keyId !in completed &&
                        it.keyId !in denied && it.isAvailable()
                }
            } else {
                null
            }
            when {
                candidate != null -> {
                    attempted += candidate.keyId
                    if (candidate.keyId !in confirmed) {
                        if (!candidate.confirm()) {
                            denied += candidate.keyId
                            continue
                        }
                        confirmed += candidate.keyId
                    }
                    if (!candidate.isAvailable() || !candidate.prepare()) continue
                    onMethod("publickey")
                    if (!connection.probePublicKey(username, candidate.pair.public)) {
                        onKeyRejected(candidate.nickname)
                        continue
                    }
                    if (!candidate.isAvailable()) continue
                    if (connection.authenticateWithPublicKey(username, candidate.pair)) {
                        return AuthenticationOutcome.Authenticated
                    }
                    if (connection.isAuthenticationPartialSuccess) {
                        completed += candidate.keyId
                        attempted.clear()
                    } else {
                        onKeyRejected(candidate.nickname)
                    }
                }

                "keyboard-interactive" in methods && interactiveEnabled && interactiveAttempts < MAX_ATTEMPTS -> {
                    interactiveAttempts++
                    var challengeSeen = false
                    onMethod("keyboard-interactive")
                    val callback = InteractiveCallback { name, instruction, count, prompts, echo ->
                        challengeSeen = true
                        // Use a saved password only for the first hidden challenge in this method.
                        val responses = if (count > 0 && echo.any { !it } && !interactiveSavedPasswordTried) {
                            interactiveSavedPasswordTried = true
                            savedPassword()?.let { password ->
                                // Other challenge fields still need their normal responses.
                                val firstHidden = echo.indexOfFirst { !it }
                                val remainingPrompts = prompts.filterIndexed { index, _ -> index != firstHidden }.toTypedArray()
                                val remainingEcho = echo.filterIndexed { index, _ -> index != firstHidden }.toBooleanArray()
                                val remaining = if (count > 1) {
                                    interactive.replyToChallenge(name, instruction, count - 1, remainingPrompts, remainingEcho)
                                } else {
                                    emptyArray()
                                }
                                Array(count) { index ->
                                    if (index == firstHidden) password else remaining[if (index < firstHidden) index else index - 1]
                                }
                            }
                        } else {
                            null
                        }
                        responses ?: interactive.replyToChallenge(name, instruction, count, prompts, echo)
                    }
                    if (connection.authenticateWithKeyboardInteractive(username, callback)) {
                        return AuthenticationOutcome.Authenticated
                    }
                    interactiveEnabled = challengeSeen
                    if (connection.isAuthenticationPartialSuccess) {
                        attempted.clear()
                    } else {
                        onMethodRejected("keyboard-interactive")
                    }
                }

                "password" in methods && passwordAttempts < MAX_ATTEMPTS -> {
                    val stored = if (!passwordSavedPasswordTried) {
                        passwordSavedPasswordTried = true
                        savedPassword()
                    } else {
                        null
                    }
                    onMethod("password")
                    val password = stored ?: passwordPrompt() ?: return AuthenticationOutcome.Cancelled
                    passwordAttempts++
                    if (connection.authenticateWithPassword(username, password)) return AuthenticationOutcome.Authenticated
                    if (connection.isAuthenticationPartialSuccess) {
                        attempted.clear()
                    } else {
                        onMethodRejected("password")
                    }
                }

                else -> return AuthenticationOutcome.Exhausted
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
