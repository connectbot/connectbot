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

import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.data.entity.Pubkey
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Exporter for converting ConnectBot hosts to OpenSSH config format.
 */
class SshConfigExporter {

    /**
     * Export hosts to SSH config format.
     *
     * @param hosts List of hosts with their port forwards
     * @param pubkeys Map of pubkey ID to pubkey (for IdentityFile resolution)
     * @param jumpHosts Map of host ID to host (for ProxyJump resolution)
     * @return SshConfigExportResult with config text and counts
     */
    fun export(
        hosts: List<Pair<Host, List<PortForward>>>,
        pubkeys: Map<Long, Pubkey>,
        jumpHosts: Map<Long, Host>
    ): SshConfigExportResult {
        val builder = StringBuilder()
        var exportedCount = 0
        var skippedCount = 0

        // Add header comment
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        builder.appendLine("# ConnectBot SSH Config Export")
        builder.appendLine("# Generated on $timestamp")
        builder.appendLine()

        for ((host, portForwards) in hosts) {
            // Only export SSH hosts
            if (host.protocol != "ssh") {
                skippedCount++
                continue
            }

            exportedCount++
            builder.append(exportHost(host, portForwards, pubkeys, jumpHosts))
            builder.appendLine()
        }

        return SshConfigExportResult(
            configText = builder.toString(),
            hostCount = exportedCount,
            skippedCount = skippedCount
        )
    }

    private fun exportHost(
        host: Host,
        portForwards: List<PortForward>,
        pubkeys: Map<Long, Pubkey>,
        jumpHosts: Map<Long, Host>
    ): String {
        val builder = StringBuilder()

        // Host pattern (use nickname)
        builder.appendLine("Host ${escapeHostPattern(host.nickname)}")

        // Required: HostName
        if (host.hostname.isNotEmpty()) {
            builder.appendLine("    HostName ${host.hostname}")
        }

        // User (if set)
        if (host.username.isNotEmpty()) {
            builder.appendLine("    User ${host.username}")
        }

        // Port (if non-default)
        if (host.port != 22) {
            builder.appendLine("    Port ${host.port}")
        }

        // IdentityFile (resolve pubkey nickname)
        if (host.pubkeyId > 0) {
            pubkeys[host.pubkeyId]?.let { pubkey ->
                builder.appendLine("    IdentityFile ~/.ssh/${pubkey.nickname}")
            }
        }

        // PubkeyAuthentication
        builder.appendLine("    PubkeyAuthentication ${if (host.useKeys) "yes" else "no"}")

        // AddKeysToAgent
        host.useAuthAgent?.let { agent ->
            val sshValue = when (agent) {
                "no" -> "no"
                "yes" -> "yes"
                "confirm" -> "confirm"
                else -> null
            }
            sshValue?.let { builder.appendLine("    AddKeysToAgent $it") }
        }

        // Compression
        if (host.compression) {
            builder.appendLine("    Compression yes")
        }

        // RequestTTY (wantSession)
        if (!host.wantSession) {
            builder.appendLine("    RequestTTY no")
        }

        // RemoteCommand (postLogin)
        host.postLogin?.takeIf { it.isNotEmpty() }?.let { cmd ->
            builder.appendLine("    RemoteCommand $cmd")
        }

        // ProxyJump (jumpHostId)
        host.jumpHostId?.let { jumpId ->
            jumpHosts[jumpId]?.let { jumpHost ->
                builder.appendLine("    ProxyJump ${jumpHost.nickname}")
            }
        }

        // Port forwards
        for (pf in portForwards) {
            when (pf.type) {
                "local" -> {
                    builder.appendLine("    LocalForward ${pf.sourcePort} ${pf.destAddr ?: "localhost"}:${pf.destPort}")
                }
                "remote" -> {
                    builder.appendLine("    RemoteForward ${pf.sourcePort} ${pf.destAddr ?: "localhost"}:${pf.destPort}")
                }
                "dynamic4", "dynamic5" -> {
                    builder.appendLine("    DynamicForward ${pf.sourcePort}")
                }
            }
        }

        // Add comment for ConnectBot-specific fields that have no SSH equivalent
        val connectbotSpecific = mutableListOf<String>()
        if (host.color != null) connectbotSpecific.add("Color: ${host.color}")
        if (host.stayConnected) connectbotSpecific.add("StayConnected: yes")
        if (host.quickDisconnect) connectbotSpecific.add("QuickDisconnect: yes")
        if (host.scrollbackLines != 140) connectbotSpecific.add("ScrollbackLines: ${host.scrollbackLines}")
        if (host.useCtrlAltAsMetaKey) connectbotSpecific.add("UseCtrlAltAsMetaKey: yes")

        if (connectbotSpecific.isNotEmpty()) {
            builder.appendLine("    # ConnectBot-specific settings not exported:")
            for (setting in connectbotSpecific) {
                builder.appendLine("    # $setting")
            }
        }

        return builder.toString()
    }

    /**
     * Escape host pattern if it contains spaces or special characters.
     */
    private fun escapeHostPattern(pattern: String): String {
        return if (pattern.contains(" ") || pattern.contains("\t")) {
            "\"$pattern\""
        } else {
            pattern
        }
    }
}
