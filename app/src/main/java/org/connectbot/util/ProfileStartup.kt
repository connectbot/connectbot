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
 * Helpers for a profile's startup command and environment variables.
 *
 * Environment variables are stored as one `KEY=VALUE` entry per line. Because
 * sshlib has no support for the SSH `env` channel request (and sshd's default
 * `AcceptEnv` would reject most names anyway), variables are applied through
 * the remote shell instead: as `export` statements typed into the interactive
 * shell, or as a prefix of the session exec command.
 */
object ProfileStartup {
    private val ENV_NAME_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")

    /**
     * Parse environment variable text into (name, value) pairs.
     * Blank lines and invalid entries are skipped.
     */
    fun parseEnvironmentVariables(text: String?): List<Pair<String, String>> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val name = line.substring(0, index).trim()
                if (!ENV_NAME_REGEX.matches(name)) return@mapNotNull null
                name to line.substring(index + 1)
            }
    }

    /**
     * Find the first invalid environment variable entry.
     *
     * @return The 1-based line number of the first non-blank line that is not
     *   a valid `KEY=VALUE` entry, or null if all entries are valid.
     */
    fun firstInvalidEnvironmentLine(text: String): Int? {
        text.lines().forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed
            val eq = line.indexOf('=')
            if (eq <= 0 || !ENV_NAME_REGEX.matches(line.substring(0, eq).trim())) {
                return index + 1
            }
        }
        return null
    }

    /**
     * Build the string to type into the interactive shell after login:
     * one `export` statement per environment variable followed by the startup
     * command lines, each terminated with a newline so the shell runs them.
     *
     * @return The string to inject, or null if there is nothing to send.
     */
    fun buildInjectString(environmentVariables: String?, startupCommand: String?): String? {
        val lines = mutableListOf<String>()
        parseEnvironmentVariables(environmentVariables).forEach { (name, value) ->
            lines.add("export $name=${shellQuote(value)}")
        }
        startupCommand?.lines()?.filter { it.isNotBlank() }?.forEach { lines.add(it) }
        if (lines.isEmpty()) return null
        return lines.joinToString("\n", postfix = "\n")
    }

    /**
     * Build a single command string for the SSH session exec request
     * (`ssh host command`): `export` statements and startup command lines
     * joined with `;` so the remote shell runs them in order.
     *
     * @return The exec command, or null if there is no startup command. A
     *   session command is only requested when the user configured one;
     *   environment variables alone do not switch the session to exec mode.
     */
    fun buildExecCommand(environmentVariables: String?, startupCommand: String?): String? {
        val commandLines = startupCommand?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        if (commandLines.isEmpty()) return null
        val exports = parseEnvironmentVariables(environmentVariables).map { (name, value) ->
            "export $name=${shellQuote(value)}"
        }
        return (exports + commandLines).joinToString("; ")
    }

    /**
     * Quote a value for POSIX shells: wrap in single quotes, with embedded
     * single quotes escaped as `'\''`.
     */
    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
