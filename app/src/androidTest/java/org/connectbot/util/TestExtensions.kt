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

package org.connectbot.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.ui.AppUiState
import org.connectbot.ui.MainActivity

/**
 * Wait until TerminalManager service is bound and AppUiState is Ready for a standalone MainActivity.
 * Returns the Ready state when bound.
 */
suspend fun MainActivity.waitUntilServiceBound(
    timeoutMillis: Long = 5000
): AppUiState.Ready {
    return withTimeout(timeoutMillis) {
        while (true) {
            val state = appViewModel.uiState.value
            if (state is AppUiState.Ready) {
                return@withTimeout state
            }
            delay(100)
        }
        throw IllegalStateException("Should never reach here")
    }
}

/**
 * Wait for a specific bridge to appear in the bridges flow.
 */
suspend fun TerminalManager.waitForBridge(
    predicate: (TerminalBridge) -> Boolean,
    timeoutMillis: Long = 5000
): TerminalBridge {
    return withTimeout(timeoutMillis) {
        var foundBridge: TerminalBridge? = null
        while (foundBridge == null) {
            foundBridge = bridgesFlow.value.firstOrNull(predicate)
            if (foundBridge == null) delay(100)
        }
        foundBridge
    }
}

/**
 * Wait for a bridge with the specified nickname to be created.
 */
suspend fun TerminalManager.waitForBridgeByNickname(
    nickname: String,
    timeoutMillis: Long = 5000
): TerminalBridge {
    return waitForBridge({ it.host.nickname == nickname }, timeoutMillis)
}
