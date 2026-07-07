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

package org.connectbot

import org.connectbot.util.SnippetVariables
import org.junit.Assert.assertEquals
import org.junit.Test

class SnippetVariablesTest {
    @Test
    fun extract_noPlaceholders_returnsEmpty() {
        assertEquals(emptyList<String>(), SnippetVariables.extract("ls -la"))
    }

    @Test
    fun extract_singlePlaceholder() {
        assertEquals(listOf("container"), SnippetVariables.extract("docker logs -f \${container}"))
    }

    @Test
    fun extract_multiplePlaceholders_inOrder() {
        assertEquals(
            listOf("user", "host"),
            SnippetVariables.extract("ssh \${user}@\${host}"),
        )
    }

    @Test
    fun extract_repeatedPlaceholder_returnsDistinct() {
        assertEquals(
            listOf("dir"),
            SnippetVariables.extract("mkdir \${dir} && cd \${dir}"),
        )
    }

    @Test
    fun extract_trimsWhitespaceAndSkipsBlank() {
        assertEquals(
            listOf("name"),
            SnippetVariables.extract("echo \${ name } \${ }"),
        )
    }

    @Test
    fun extract_ignoresUnclosedPlaceholder() {
        assertEquals(emptyList<String>(), SnippetVariables.extract("echo \${unclosed"))
    }

    @Test
    fun substitute_replacesAllOccurrences() {
        assertEquals(
            "mkdir /tmp/x && cd /tmp/x",
            SnippetVariables.substitute("mkdir \${dir} && cd \${dir}", mapOf("dir" to "/tmp/x")),
        )
    }

    @Test
    fun substitute_trimmedNameMatches() {
        assertEquals(
            "echo hi",
            SnippetVariables.substitute("echo \${ msg }", mapOf("msg" to "hi")),
        )
    }

    @Test
    fun substitute_missingValueLeavesPlaceholder() {
        assertEquals(
            "echo \${msg}",
            SnippetVariables.substitute("echo \${msg}", emptyMap()),
        )
    }

    @Test
    fun substitute_plainDollarSignsUntouched() {
        assertEquals(
            "echo \$HOME $1",
            SnippetVariables.substitute("echo \$HOME $1", mapOf("x" to "y")),
        )
    }
}
