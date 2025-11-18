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

package org.connectbot.service

/**
 * Sealed class hierarchy representing errors that can occur in the TerminalManager service.
 * These errors are propagated to the UI layer via SharedFlow for proper user notification.
 */
sealed class ServiceError {
    /**
     * Failed to load a public key into memory at startup.
     *
     * @param keyName The nickname of the key that failed to load
     * @param reason Human-readable error message
     */
    data class KeyLoadFailed(
        val keyName: String,
        val reason: String
    ) : ServiceError()

    /**
     * Failed to establish a connection to a host.
     *
     * @param hostNickname The nickname of the host connection that failed
     * @param hostname The actual hostname or IP address
     * @param reason Human-readable error message
     */
    data class ConnectionFailed(
        val hostNickname: String,
        val hostname: String,
        val reason: String
    ) : ServiceError()

    /**
     * Failed to load port forwards for a host.
     *
     * @param hostNickname The nickname of the host
     * @param reason Human-readable error message
     */
    data class PortForwardLoadFailed(
        val hostNickname: String,
        val reason: String
    ) : ServiceError()

    /**
     * Failed to save host configuration changes.
     *
     * @param hostNickname The nickname of the host
     * @param reason Human-readable error message
     */
    data class HostSaveFailed(
        val hostNickname: String,
        val reason: String
    ) : ServiceError()

    /**
     * Failed to load color scheme.
     *
     * @param reason Human-readable error message
     */
    data class ColorSchemeLoadFailed(
        val reason: String
    ) : ServiceError()
}
