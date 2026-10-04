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

import org.connectbot.data.entity.Host
import org.junit.Assert.assertEquals
import org.junit.Test

class MoshServerCommandTest {
    @Test
    fun defaultServerAdvertises256Colors() {
        assertEquals(
            "sh -c '[ -n \"\$SSH_CONNECTION\" ] && printf \"\\nMOSH SSH_CONNECTION %s\\n\" \"\$SSH_CONNECTION\"; " +
                "exec env LANG=en_US.UTF-8 LC_ALL=en_US.UTF-8 MOSH_SERVER_NETWORK_TMOUT=\${MOSH_SERVER_NETWORK_TMOUT:-604800} mosh-server new -c 256'",
            Mosh().buildMoshServerCommand(Host()),
        )
    }

    @Test
    fun configuredPortFollowsNewSubcommand() {
        val command = Mosh().buildMoshServerCommand(
            Host(moshServer = "/usr/local/bin/mosh-server", moshPort = 60042, locale = "de_DE.UTF-8"),
        )
        assertEquals(
            "exec env LANG=de_DE.UTF-8 LC_ALL=de_DE.UTF-8 MOSH_SERVER_NETWORK_TMOUT=\${MOSH_SERVER_NETWORK_TMOUT:-604800} /usr/local/bin/mosh-server new -c 256 -p 60042'",
            command.substringAfter("; "),
        )
    }

    @Test
    fun hostTimeoutOverridesServerSettingIncludingZero() {
        for (timeout in listOf(0, 86400, Int.MAX_VALUE)) {
            val command = Mosh().buildMoshServerCommand(Host(moshNetworkTimeout = timeout))
            org.junit.Assert.assertTrue(command.contains("MOSH_SERVER_NETWORK_TMOUT=$timeout "))
        }
    }

    @Test
    fun defaultTimeoutPreservesRemoteSetting() {
        val command = Mosh().buildMoshServerCommand(Host())
        for ((remote, expected) in listOf(null to "604800", "" to "604800", "0" to "0", "86400" to "86400")) {
            val builder = ProcessBuilder("sh", "-c", command.removePrefix("sh -c '").removeSuffix("'"))
            builder.environment().remove("MOSH_SERVER_NETWORK_TMOUT")
            if (remote != null) builder.environment()["MOSH_SERVER_NETWORK_TMOUT"] = remote
            // Replace the actual server with a command that prints the expanded environment.
            val script = builder.command()[2].substringBefore("mosh-server new") + "sh -c 'printf %s \"\$MOSH_SERVER_NETWORK_TMOUT\"'"
            builder.command("sh", "-c", script)
            builder.environment().remove("SSH_CONNECTION")
            val process = builder.start()
            assertEquals(expected, process.inputStream.bufferedReader().readText())
            assertEquals(0, process.waitFor())
        }
    }
}
