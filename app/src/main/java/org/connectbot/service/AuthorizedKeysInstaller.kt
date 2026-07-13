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

import com.trilead.ssh2.crypto.PublicKeyUtils
import com.trilead.ssh2.crypto.keys.Ed25519Provider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.connectbot.data.HostRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.PubkeyUtils
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import javax.inject.Inject

sealed interface AuthorizedKeyInstallResult {
    data class Success(val pubkey: Pubkey) : AuthorizedKeyInstallResult
    data class Failure(val reason: String) : AuthorizedKeyInstallResult
}

class AuthorizedKeysInstaller @Inject constructor(
    private val pubkeyRepository: PubkeyRepository,
    private val hostRepository: HostRepository,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun install(
        bridge: TerminalBridge,
        terminalManager: TerminalManager,
    ): AuthorizedKeyInstallResult = withContext(dispatchers.io) {
        if (bridge.host.isTemporary || bridge.host.protocol != "ssh") {
            return@withContext AuthorizedKeyInstallResult.Failure("Key setup requires a saved SSH host")
        }
        val transport = bridge.transport
        if (transport?.canOpenExecChannels() != true) {
            return@withContext AuthorizedKeyInstallResult.Failure("The server does not support command channels")
        }

        var generatedPair: KeyPair? = null
        var generatedKey: Pubkey? = null
        val selected = configuredInstallableKey(bridge, terminalManager) ?: run {
            val pair = generateEd25519KeyPair()
            val pubkey = pubkeyRepository.save(
                Pubkey(
                    nickname = uniqueNickname("${bridge.host.nickname}-login"),
                    type = "Ed25519",
                    privateKey = PubkeyUtils.getEncodedPrivate(pair.private, null),
                    publicKey = pair.public.encoded,
                    encrypted = false,
                    startup = true,
                    confirmation = false,
                    createdDate = System.currentTimeMillis(),
                    storageType = KeyStorageType.EXPORTABLE,
                    allowBackup = false,
                ),
            )
            generatedPair = pair
            generatedKey = pubkey
            pubkey to pair
        }

        val (pubkey, pair) = selected
        val publicKey = pair?.public ?: runCatching {
            PubkeyUtils.decodePublic(pubkey.publicKey, pubkey.type)
        }.getOrElse {
            generatedKey?.let { pubkeyRepository.delete(it) }
            return@withContext AuthorizedKeyInstallResult.Failure("The selected public key could not be encoded")
        }
        val authorizedKey = PublicKeyUtils.toAuthorizedKeysFormat(publicKey, pubkey.nickname).trim()

        val failure = runCatching {
            withTimeout(INSTALL_TIMEOUT_MS) {
                val channel = runInterruptible(dispatchers.io) {
                    transport.openExecChannel(authorizedKeysInstallCommand(authorizedKey))
                }
                try {
                    val (stdout, stderr) = coroutineScope {
                        val stdoutRead = async(dispatchers.io) {
                            runInterruptible { channel.stdout.bufferedReader().use { it.readText() } }
                        }
                        val stderrRead = async(dispatchers.io) {
                            runInterruptible { channel.stderr.bufferedReader().use { it.readText() } }
                        }
                        stdoutRead.await() to stderrRead.await()
                    }
                    var status = channel.exitStatus()
                    repeat(EXIT_STATUS_POLLS) {
                        if (status != null) return@repeat
                        delay(EXIT_STATUS_POLL_MS)
                        status = channel.exitStatus()
                    }
                    if (status != 0) {
                        throw IllegalStateException(
                            stderr.trim().ifEmpty { stdout.trim().ifEmpty { "Remote command failed" } },
                        )
                    }
                } finally {
                    channel.close()
                }
            }
        }.exceptionOrNull()

        if (failure != null) {
            generatedKey?.let { pubkeyRepository.delete(it) }
            return@withContext AuthorizedKeyInstallResult.Failure(
                failure.message ?: "Could not install the public key",
            )
        }

        generatedPair?.let { terminalManager.addKey(pubkey, it, true) }
        val persistedHost = hostRepository.findHostById(bridge.host.id) ?: bridge.host
        val updatedHost = persistedHost.copy(useKeys = true, pubkeyId = pubkey.id)
        hostRepository.saveHost(updatedHost)
        bridge.host = updatedHost
        AuthorizedKeyInstallResult.Success(pubkey)
    }

    private suspend fun configuredInstallableKey(
        bridge: TerminalBridge,
        terminalManager: TerminalManager,
    ): Pair<Pubkey, KeyPair?>? {
        val id = bridge.host.pubkeyId.takeIf { it > 0L } ?: return null
        val pubkey = pubkeyRepository.getById(id) ?: return null
        val loaded = terminalManager.getKey(pubkey.nickname)
        if (loaded != null) return pubkey to loaded
        if (pubkey.type == "IMPORTED") return null
        return runCatching {
            val publicKey = PubkeyUtils.decodePublic(pubkey.publicKey, pubkey.type)
            pubkey to KeyPair(publicKey, null)
        }.getOrNull()
    }

    private suspend fun uniqueNickname(base: String): String {
        val normalized = base.trim().ifEmpty { "automatic-login" }
        var candidate = normalized
        var suffix = 2
        while (pubkeyRepository.getByNickname(candidate) != null) {
            candidate = "$normalized-$suffix"
            suffix++
        }
        return candidate
    }

    private fun generateEd25519KeyPair(): KeyPair {
        Ed25519Provider.insertIfNeeded()
        return KeyPairGenerator.getInstance("Ed25519").apply {
            initialize(255, SecureRandom())
        }.generateKeyPair()
    }

    private companion object {
        const val INSTALL_TIMEOUT_MS = 15_000L
        const val EXIT_STATUS_POLLS = 20
        const val EXIT_STATUS_POLL_MS = 25L
    }
}

internal fun authorizedKeysInstallCommand(authorizedKey: String): String {
    val quotedKey = shellSingleQuote(authorizedKey)
    return "umask 077 && " +
        "mkdir -p \"\$HOME/.ssh\" && " +
        "chmod 700 \"\$HOME/.ssh\" && " +
        "touch \"\$HOME/.ssh/authorized_keys\" && " +
        "chmod 600 \"\$HOME/.ssh/authorized_keys\" && " +
        "(grep -qxF $quotedKey \"\$HOME/.ssh/authorized_keys\" || " +
        "printf '%s\\n' $quotedKey >> \"\$HOME/.ssh/authorized_keys\")"
}

internal fun shellSingleQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
