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

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Configuration for E2E tests, read from instrumentation arguments.
 *
 * These values are passed via:
 *   adb shell am instrument -e sshHost <host> -e sshPort <port> ...
 */
object E2ETestConfig {
    private val args: Bundle by lazy {
        InstrumentationRegistry.getArguments()
    }

    /** SSH server hostname (default: 10.0.2.2 for emulator host access) */
    val sshHost: String
        get() = args.getString("sshHost", "10.0.2.2")

    /** SSH server port (default: 2222) */
    val sshPort: Int
        get() = args.getString("sshPort", "2222")?.toIntOrNull() ?: 2222

    /** SSH test user */
    val sshUser: String
        get() = args.getString("sshUser", "testuser")

    /** SSH test password */
    val sshPassword: String
        get() = args.getString("sshPassword", "testpass123")

    /** Test private key asset path */
    const val TEST_PRIVATE_KEY_ASSET = "test_keys/id_ed25519"

    /** Check if E2E test configuration is available */
    val isConfigured: Boolean
        get() = args.containsKey("sshHost")
}
