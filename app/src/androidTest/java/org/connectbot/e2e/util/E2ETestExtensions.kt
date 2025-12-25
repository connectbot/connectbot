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

package org.connectbot.e2e.util

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.util.HostConstants

/**
 * Wait for a bridge to reach connected state (transport connected).
 */
suspend fun TerminalManager.waitForConnectionState(
    nickname: String,
    timeoutMillis: Long = 30000
): TerminalBridge {
    return withTimeout(timeoutMillis) {
        var bridge: TerminalBridge? = null
        while (bridge == null || bridge.transport?.isConnected() != true) {
            bridge = bridgesFlow.value.firstOrNull { it.host.nickname == nickname }
            if (bridge?.transport?.isConnected() != true) {
                delay(500)
            }
        }
        bridge
    }
}

/**
 * Wait for a bridge to have an open session (authenticated and shell ready).
 */
suspend fun TerminalManager.waitForSessionOpen(
    nickname: String,
    timeoutMillis: Long = 30000
): TerminalBridge {
    return withTimeout(timeoutMillis) {
        var bridge: TerminalBridge? = null
        while (bridge == null || bridge.transport?.isSessionOpen() != true) {
            bridge = bridgesFlow.value.firstOrNull { it.host.nickname == nickname }
            if (bridge?.transport?.isSessionOpen() != true) {
                delay(500)
            }
        }
        bridge
    }
}

/**
 * Wait for a bridge to disconnect.
 */
suspend fun TerminalManager.waitForDisconnect(
    nickname: String,
    timeoutMillis: Long = 10000
) {
    withTimeout(timeoutMillis) {
        while (bridgesFlow.value.any { it.host.nickname == nickname }) {
            delay(500)
        }
    }
}

/**
 * Create a test SSH host configuration using E2E test config.
 */
fun createE2ETestHost(
    nickname: String = "E2E-Test-Host",
    username: String = E2ETestConfig.sshUser,
    hostname: String = E2ETestConfig.sshHost,
    port: Int = E2ETestConfig.sshPort,
    useKeys: Boolean = true,
    pubkeyId: Long = HostConstants.PUBKEYID_ANY
): Host {
    return Host.createSshHost(
        nickname = nickname,
        hostname = hostname,
        port = port,
        username = username
    ).copy(
        useKeys = useKeys,
        pubkeyId = pubkeyId
    )
}

/**
 * Create a password-only test host (disables key authentication).
 */
fun createPasswordOnlyTestHost(
    nickname: String = "E2E-Password-Host"
): Host {
    return createE2ETestHost(
        nickname = nickname,
        useKeys = false,
        pubkeyId = HostConstants.PUBKEYID_NEVER
    )
}

/**
 * Load test private key from assets.
 * Note: Uses instrumentation context to access test APK assets.
 */
@Suppress("UNUSED_PARAMETER")
fun loadTestPrivateKey(context: Context): ByteArray {
    // Test assets are in the test APK, not the app APK
    // Use instrumentation context to access them
    val testContext = InstrumentationRegistry.getInstrumentation().context
    return testContext.assets.open(E2ETestConfig.TEST_PRIVATE_KEY_ASSET).use {
        it.readBytes()
    }
}

/**
 * Create a test pubkey entity from assets.
 * The key is in OpenSSH format, so type must be "IMPORTED" to use PEMDecoder.
 */
fun createTestPubkey(
    context: Context,
    nickname: String = "E2E-Test-Key"
): Pubkey {
    val privateKeyBytes = loadTestPrivateKey(context)
    return Pubkey(
        id = 0L,
        nickname = nickname,
        type = "IMPORTED", // OpenSSH format keys must use IMPORTED type
        privateKey = privateKeyBytes,
        publicKey = ByteArray(0), // Public key derived from private
        encrypted = false,
        startup = false,
        confirmation = false,
        createdDate = System.currentTimeMillis(),
        storageType = KeyStorageType.EXPORTABLE,
        allowBackup = false,
        keystoreAlias = null
    )
}
