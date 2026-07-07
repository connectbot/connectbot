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

/**
 * Parsing and substitution of `${variable}` placeholders in snippet commands.
 *
 * A placeholder is `${name}` where the name is any non-empty text without a
 * closing brace. Names are trimmed, so `${ host }` and `${host}` refer to the
 * same variable.
 */
object SnippetVariables {
    private val PLACEHOLDER = Regex("""\$\{([^}]+)}""")

    /**
     * Extract the distinct variable names from a command, in order of first
     * appearance. Blank names (e.g. `${ }`) are ignored.
     */
    fun extract(command: String): List<String> = PLACEHOLDER.findAll(command)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

    /**
     * Replace each `${name}` placeholder with its value from [values].
     * Placeholders whose name is missing from [values] are left as-is.
     */
    fun substitute(command: String, values: Map<String, String>): String = PLACEHOLDER.replace(command) { match ->
        val name = match.groupValues[1].trim()
        values[name] ?: match.value
    }
}
