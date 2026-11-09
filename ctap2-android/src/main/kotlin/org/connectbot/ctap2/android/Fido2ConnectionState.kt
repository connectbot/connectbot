/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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

package org.connectbot.ctap2.android

/**
 * Connection state of FIDO2 security key.
 */
sealed class Fido2ConnectionState {
    /** No security key is connected */
    data object Disconnected : Fido2ConnectionState()

    /** Attempting to connect to a security key */
    data object Connecting : Fido2ConnectionState()

    /** Security key is connected and ready */
    data class Connected(
        /** Transport type: "USB" or "NFC" */
        val transport: String,
        /** Device name if available */
        val deviceName: String? = null,
    ) : Fido2ConnectionState()

    /** An error occurred during connection or operation */
    data class Error(val message: String) : Fido2ConnectionState()
}
