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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedKeysInstallerTest {
    @Test
    fun shellSingleQuote_escapesEmbeddedQuote() {
        assertEquals("'host'\"'\"'s key'", shellSingleQuote("host's key"))
    }

    @Test
    fun installCommand_isIdempotentAndSecuresSshFiles() {
        val command = authorizedKeysInstallCommand("ssh-ed25519 AAAA test")

        assertTrue(command.contains("mkdir -p \"\$HOME/.ssh\""))
        assertTrue(command.contains("chmod 700 \"\$HOME/.ssh\""))
        assertTrue(command.contains("chmod 600 \"\$HOME/.ssh/authorized_keys\""))
        assertTrue(command.contains("grep -qxF 'ssh-ed25519 AAAA test'"))
        assertTrue(command.contains("|| printf '%s\\n' 'ssh-ed25519 AAAA test'"))
    }
}
