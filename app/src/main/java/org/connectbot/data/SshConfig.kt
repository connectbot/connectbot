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

package org.connectbot.data

/**
 * Result of exporting hosts to SSH config format.
 *
 * @param configText The generated SSH config text
 * @param hostCount Number of SSH hosts exported
 * @param skippedCount Number of non-SSH hosts (telnet/local) that were skipped
 */
data class SshConfigExportResult(
    val configText: String,
    val hostCount: Int,
    val skippedCount: Int
)

/**
 * Result of importing hosts from SSH config format.
 *
 * @param hostsImported Number of hosts newly imported
 * @param hostsSkipped Number of hosts skipped (duplicates)
 * @param warnings List of warnings generated during import
 */
data class SshConfigImportResult(
    val hostsImported: Int,
    val hostsSkipped: Int,
    val warnings: List<SshConfigWarning>
)

/**
 * Warning generated during SSH config import.
 *
 * @param hostPattern The host pattern this warning applies to (null for global)
 * @param directive The directive that caused the warning
 * @param message Human-readable warning message
 * @param type The type of warning
 */
data class SshConfigWarning(
    val hostPattern: String?,
    val directive: String,
    val message: String,
    val type: SshConfigWarningType
)

/**
 * Types of warnings that can be generated during SSH config import.
 */
enum class SshConfigWarningType {
    /** Directive is not supported (e.g., ProxyCommand) */
    UNSUPPORTED_DIRECTIVE,

    /** Match blocks are not supported */
    MATCH_BLOCK_IGNORED,

    /** Include directive is ignored */
    INCLUDE_IGNORED,

    /** Value could not be parsed */
    INVALID_VALUE,

    /** IdentityFile references unknown key */
    PUBKEY_NOT_FOUND
}

/**
 * Intermediate representation of a parsed SSH config host block.
 * Used during import to hold parsed data before converting to Host entity.
 */
data class ParsedSshHost(
    val hostPattern: String,
    val hostname: String?,
    val user: String?,
    val port: Int?,
    val identityFile: String?,
    val proxyJump: String?,
    val compression: Boolean?,
    val requestTty: Boolean?,
    val pubkeyAuthentication: Boolean?,
    val addKeysToAgent: String?,
    val remoteCommand: String?,
    val localForwards: List<ParsedPortForward>,
    val remoteForwards: List<ParsedPortForward>,
    val dynamicForwards: List<Int>
)

/**
 * Parsed port forward from SSH config.
 *
 * @param sourcePort The local port to bind
 * @param destHost The destination host
 * @param destPort The destination port
 */
data class ParsedPortForward(
    val sourcePort: Int,
    val destHost: String,
    val destPort: Int
)
