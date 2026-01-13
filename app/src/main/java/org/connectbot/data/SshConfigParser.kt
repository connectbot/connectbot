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
 * Parser for OpenSSH config format.
 *
 * Parses the standard ~/.ssh/config format and converts to intermediate
 * representations that can be converted to Host entities. Generates warnings
 * for unsupported directives.
 */
class SshConfigParser {

    companion object {
        /** Directives that generate explicit warnings when encountered. */
        private val UNSUPPORTED_WITH_WARNING = setOf(
            "proxycommand",
            "serveraliveinterval",
            "serveralivecountmax",
            "connecttimeout",
            "hostkeyalgorithms",
            "ciphers",
            "macs",
            "kexalgorithms",
            "forwardagent",
            "forwardx11",
            "permitlocalcommand",
            "localcommand",
            "sendenv",
            "setenv",
            "stricthostkeychecking",
            "userknownhostsfile",
            "controlmaster",
            "controlpath",
            "controlpersist"
        )
    }

    /**
     * Parse SSH config text into intermediate parsed hosts.
     *
     * @param configText The SSH config file contents
     * @return Pair of parsed hosts and any warnings generated
     */
    fun parse(configText: String): Pair<List<ParsedSshHost>, List<SshConfigWarning>> {
        val hosts = mutableListOf<ParsedSshHost>()
        val warnings = mutableListOf<SshConfigWarning>()

        var currentHost: HostBuilder? = null
        var inMatchBlock = false

        for (line in configText.lines()) {
            val trimmed = line.trim()

            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            // Parse directive and value
            val parsed = parseDirectiveLine(trimmed) ?: continue
            val (directive, value) = parsed

            when (directive.lowercase()) {
                "host" -> {
                    inMatchBlock = false
                    // Save previous host if exists
                    currentHost?.build()?.let { hosts.add(it) }
                    currentHost = HostBuilder(value)
                }
                "match" -> {
                    // Save previous host if exists
                    currentHost?.build()?.let { hosts.add(it) }
                    currentHost = null
                    inMatchBlock = true
                    warnings.add(
                        SshConfigWarning(
                            hostPattern = null,
                            directive = "Match",
                            message = "Match blocks are not supported",
                            type = SshConfigWarningType.MATCH_BLOCK_IGNORED
                        )
                    )
                }
                "include" -> {
                    warnings.add(
                        SshConfigWarning(
                            hostPattern = currentHost?.hostPattern,
                            directive = "Include",
                            message = "Include directive is not supported",
                            type = SshConfigWarningType.INCLUDE_IGNORED
                        )
                    )
                }
                else -> {
                    if (inMatchBlock) {
                        // Skip directives inside Match blocks
                        continue
                    }
                    if (currentHost != null) {
                        val warning = currentHost.setDirective(directive, value)
                        warning?.let { warnings.add(it) }
                    } else if (directive.lowercase() in UNSUPPORTED_WITH_WARNING) {
                        // Global unsupported directive
                        warnings.add(
                            SshConfigWarning(
                                hostPattern = null,
                                directive = directive,
                                message = "Directive '$directive' is not supported",
                                type = SshConfigWarningType.UNSUPPORTED_DIRECTIVE
                            )
                        )
                    }
                }
            }
        }

        // Don't forget the last host
        currentHost?.build()?.let { hosts.add(it) }

        return Pair(hosts, warnings)
    }

    /**
     * Parse a directive line into directive name and value.
     * Handles both "Directive value" and "Directive=value" formats.
     */
    private fun parseDirectiveLine(line: String): Pair<String, String>? {
        // Handle key=value format
        val equalsIndex = line.indexOf('=')
        if (equalsIndex > 0) {
            return Pair(
                line.substring(0, equalsIndex).trim(),
                line.substring(equalsIndex + 1).trim()
            )
        }

        // Handle "key value" format (first whitespace-separated token is directive)
        val parts = line.split(Regex("\\s+"), limit = 2)
        if (parts.size < 2) return null

        return Pair(parts[0], parts[1])
    }

    /**
     * Builder class for accumulating host directives during parsing.
     */
    private inner class HostBuilder(val hostPattern: String) {
        private var hostname: String? = null
        private var user: String? = null
        private var port: Int? = null
        private var identityFile: String? = null
        private var proxyJump: String? = null
        private var compression: Boolean? = null
        private var requestTty: Boolean? = null
        private var pubkeyAuthentication: Boolean? = null
        private var addKeysToAgent: String? = null
        private var remoteCommand: String? = null
        private val localForwards = mutableListOf<ParsedPortForward>()
        private val remoteForwards = mutableListOf<ParsedPortForward>()
        private val dynamicForwards = mutableListOf<Int>()

        /**
         * Set a directive value. Returns warning if directive is unsupported.
         */
        fun setDirective(directive: String, value: String): SshConfigWarning? {
            when (directive.lowercase()) {
                "hostname" -> hostname = value
                "user" -> user = value
                "port" -> {
                    val parsed = value.toIntOrNull()
                    if (parsed != null && parsed in 1..65535) {
                        port = parsed
                    } else {
                        return SshConfigWarning(
                            hostPattern = hostPattern,
                            directive = directive,
                            message = "Invalid port value: $value",
                            type = SshConfigWarningType.INVALID_VALUE
                        )
                    }
                }
                "identityfile" -> identityFile = extractKeyName(value)
                "proxyjump" -> proxyJump = value
                "compression" -> compression = parseYesNo(value)
                "requesttty" -> requestTty = parseYesNoAuto(value)
                "pubkeyauthentication" -> pubkeyAuthentication = parseYesNo(value)
                "addkeystoagent" -> addKeysToAgent = value.lowercase()
                "remotecommand" -> remoteCommand = value
                "localforward" -> parsePortForward(value)?.let { localForwards.add(it) }
                "remoteforward" -> parsePortForward(value)?.let { remoteForwards.add(it) }
                "dynamicforward" -> {
                    val parsed = value.toIntOrNull()
                    if (parsed != null && parsed in 1..65535) {
                        dynamicForwards.add(parsed)
                    }
                }
                else -> {
                    if (directive.lowercase() in UNSUPPORTED_WITH_WARNING) {
                        return SshConfigWarning(
                            hostPattern = hostPattern,
                            directive = directive,
                            message = "Directive '$directive' is not supported",
                            type = SshConfigWarningType.UNSUPPORTED_DIRECTIVE
                        )
                    }
                    // Silently ignore other unknown directives
                }
            }
            return null
        }

        fun build(): ParsedSshHost? {
            // Skip wildcard host patterns (e.g., "Host *")
            if (hostPattern == "*" || hostPattern.contains("*") || hostPattern.contains("?")) {
                return null
            }

            return ParsedSshHost(
                hostPattern = hostPattern,
                hostname = hostname,
                user = user,
                port = port,
                identityFile = identityFile,
                proxyJump = proxyJump,
                compression = compression,
                requestTty = requestTty,
                pubkeyAuthentication = pubkeyAuthentication,
                addKeysToAgent = addKeysToAgent,
                remoteCommand = remoteCommand,
                localForwards = localForwards.toList(),
                remoteForwards = remoteForwards.toList(),
                dynamicForwards = dynamicForwards.toList()
            )
        }

        private fun parseYesNo(value: String): Boolean? {
            return when (value.lowercase()) {
                "yes", "true" -> true
                "no", "false" -> false
                else -> null
            }
        }

        private fun parseYesNoAuto(value: String): Boolean? {
            return when (value.lowercase()) {
                "yes", "true", "force" -> true
                "no", "false" -> false
                "auto" -> null
                else -> null
            }
        }

        /**
         * Extract key name from IdentityFile path.
         * ~/.ssh/id_rsa -> id_rsa
         * /path/to/mykey -> mykey
         */
        private fun extractKeyName(path: String): String {
            return path.substringAfterLast("/")
                .substringAfterLast("\\")
        }

        /**
         * Parse port forward in format: "localport host:remoteport" or "localport remotehost remoteport"
         * Also handles bind_address:port format.
         */
        private fun parsePortForward(value: String): ParsedPortForward? {
            val parts = value.split(Regex("\\s+"))

            return when {
                parts.size == 2 -> {
                    // Format: "8080 localhost:80" or "*:8080 localhost:80"
                    val sourcePort = parseSourcePort(parts[0])
                    val destParts = parts[1].split(":")
                    if (sourcePort == null || destParts.size != 2) return null
                    val destPort = destParts[1].toIntOrNull() ?: return null
                    ParsedPortForward(sourcePort, destParts[0], destPort)
                }
                parts.size == 3 -> {
                    // Format: "8080 localhost 80"
                    val sourcePort = parseSourcePort(parts[0])
                    val destPort = parts[2].toIntOrNull()
                    if (sourcePort == null || destPort == null) return null
                    ParsedPortForward(sourcePort, parts[1], destPort)
                }
                else -> null
            }
        }

        /**
         * Parse source port, handling bind_address:port format.
         */
        private fun parseSourcePort(value: String): Int? {
            // Handle formats like "*:8080" or "localhost:8080" or just "8080"
            val port = if (value.contains(":")) {
                value.substringAfterLast(":").toIntOrNull()
            } else {
                value.toIntOrNull()
            }
            return if (port != null && port in 1..65535) port else null
        }
    }
}
