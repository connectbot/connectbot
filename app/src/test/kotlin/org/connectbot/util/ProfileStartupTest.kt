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

package org.connectbot.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileStartupTest {

    @Test
    fun parseEnvironmentVariables_NullOrBlank_ReturnsEmpty() {
        assertEquals(emptyList<Pair<String, String>>(), ProfileStartup.parseEnvironmentVariables(null))
        assertEquals(emptyList<Pair<String, String>>(), ProfileStartup.parseEnvironmentVariables(""))
        assertEquals(emptyList<Pair<String, String>>(), ProfileStartup.parseEnvironmentVariables("  \n "))
    }

    @Test
    fun parseEnvironmentVariables_ValidEntries_ParsesAll() {
        val result = ProfileStartup.parseEnvironmentVariables("FOO=bar\nMY_VAR=hello world\n_X=1")
        assertEquals(
            listOf("FOO" to "bar", "MY_VAR" to "hello world", "_X" to "1"),
            result,
        )
    }

    @Test
    fun parseEnvironmentVariables_ValueWithEquals_KeepsRemainder() {
        val result = ProfileStartup.parseEnvironmentVariables("URL=http://x?a=b")
        assertEquals(listOf("URL" to "http://x?a=b"), result)
    }

    @Test
    fun parseEnvironmentVariables_SkipsBlankAndInvalidLines() {
        val result = ProfileStartup.parseEnvironmentVariables("FOO=bar\n\nnot a var\n=nope\n1BAD=x")
        assertEquals(listOf("FOO" to "bar"), result)
    }

    @Test
    fun firstInvalidEnvironmentLine_AllValid_ReturnsNull() {
        assertNull(ProfileStartup.firstInvalidEnvironmentLine(""))
        assertNull(ProfileStartup.firstInvalidEnvironmentLine("FOO=bar\n\nBAZ=qux"))
    }

    @Test
    fun firstInvalidEnvironmentLine_ReportsOneBasedLineNumber() {
        assertEquals(1, ProfileStartup.firstInvalidEnvironmentLine("no equals sign"))
        assertEquals(3, ProfileStartup.firstInvalidEnvironmentLine("FOO=bar\n\n1BAD=x"))
        assertEquals(2, ProfileStartup.firstInvalidEnvironmentLine("FOO=bar\n=empty name"))
    }

    @Test
    fun shellQuote_SimpleValue_WrapsInSingleQuotes() {
        assertEquals("'hello world'", ProfileStartup.shellQuote("hello world"))
    }

    @Test
    fun shellQuote_EmbeddedSingleQuote_EscapesIt() {
        assertEquals("'it'\\''s'", ProfileStartup.shellQuote("it's"))
    }

    @Test
    fun buildInjectString_NothingConfigured_ReturnsNull() {
        assertNull(ProfileStartup.buildInjectString(null, null))
        assertNull(ProfileStartup.buildInjectString("", "  \n"))
    }

    @Test
    fun buildInjectString_EnvVarsOnly_EmitsExportsWithTrailingNewline() {
        assertEquals(
            "export FOO='bar'\nexport BAZ='q x'\n",
            ProfileStartup.buildInjectString("FOO=bar\nBAZ=q x", null),
        )
    }

    @Test
    fun buildInjectString_CommandOnly_TerminatesEachLine() {
        assertEquals(
            "cd /var/www\ntmux attach || tmux new\n",
            ProfileStartup.buildInjectString(null, "cd /var/www\ntmux attach || tmux new"),
        )
    }

    @Test
    fun buildInjectString_EnvVarsBeforeCommand() {
        assertEquals(
            "export TERM_PROFILE='work'\ntmux attach\n",
            ProfileStartup.buildInjectString("TERM_PROFILE=work", "tmux attach"),
        )
    }

    @Test
    fun buildExecCommand_NoCommand_ReturnsNullEvenWithEnvVars() {
        assertNull(ProfileStartup.buildExecCommand("FOO=bar", null))
        assertNull(ProfileStartup.buildExecCommand("FOO=bar", "   "))
    }

    @Test
    fun buildExecCommand_CommandOnly_ReturnsCommand() {
        assertEquals("tmux attach", ProfileStartup.buildExecCommand(null, "tmux attach"))
    }

    @Test
    fun buildExecCommand_EnvVarsAndMultilineCommand_JoinsWithSemicolons() {
        assertEquals(
            "export FOO='bar'; cd /srv; ./run.sh",
            ProfileStartup.buildExecCommand("FOO=bar", "cd /srv\n./run.sh"),
        )
    }
}
