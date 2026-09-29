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

package org.connectbot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.ui.screens.console.ConnectionDetails
import org.connectbot.ui.screens.console.ConnectionStatus
import org.connectbot.util.HostConstants
import java.util.Locale

@Composable
fun ConnectionInfoDialog(details: ConnectionDetails, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_menu_connection_info)) },
        text = {
            SelectionContainer {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).testTag("connection-info-fields"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { ConnectionInfoField(stringResource(R.string.connection_info_protocol), details.host.protocol.uppercase(Locale.ROOT)) }
                    item { ConnectionInfoField(stringResource(R.string.connection_info_host), details.host.hostname) }
                    item { ConnectionInfoField(stringResource(if (details.host.protocol == "mosh") R.string.connection_info_ssh_port else R.string.connection_info_port), details.host.port.toString()) }
                    if (details.host.username.isNotEmpty()) {
                        item { ConnectionInfoField(stringResource(R.string.connection_info_username), details.host.username) }
                    }
                    item {
                        ConnectionInfoField(
                            stringResource(R.string.connection_info_status),
                            stringResource(
                                when (details.status) {
                                    ConnectionStatus.CONNECTING -> R.string.connection_info_connecting
                                    ConnectionStatus.CONNECTED -> R.string.connection_info_connected
                                    ConnectionStatus.DISCONNECTED -> R.string.connection_info_disconnected
                                },
                            ),
                        )
                    }
                    details.localAddress?.let { address ->
                        item { ConnectionInfoField(stringResource(R.string.connection_info_local_address), address) }
                    }
                    if (details.host.protocol == "ssh" || details.host.protocol == "mosh") {
                        if (details.portForwards.isNotEmpty()) {
                            item { Text(stringResource(R.string.connection_info_port_forwards), style = MaterialTheme.typography.titleSmall) }
                            details.portForwards.forEach { forward ->
                                item {
                                    val type = stringResource(
                                        when (forward.type) {
                                            HostConstants.PORTFORWARD_LOCAL -> R.string.portforward_local
                                            HostConstants.PORTFORWARD_REMOTE -> R.string.portforward_remote
                                            else -> R.string.portforward_dynamic
                                        },
                                    )
                                    ConnectionInfoField(forward.nickname, type)
                                }
                                item { ConnectionInfoField(stringResource(R.string.connection_info_configured_source), "${forward.sourceAddress}:${forward.configuredPort}") }
                                item { ConnectionInfoField(stringResource(R.string.connection_info_bound_port), forward.boundPort?.toString() ?: stringResource(R.string.connection_info_not_bound)) }
                                item { ConnectionInfoField(stringResource(R.string.connection_info_forward_status), stringResource(if (forward.isActive) R.string.connection_info_forward_active else R.string.connection_info_forward_inactive)) }
                                forward.destinationAddress?.let { address ->
                                    item { ConnectionInfoField(stringResource(R.string.connection_info_forward_destination), "$address:${forward.destinationPort}") }
                                }
                            }
                        }
                        item {
                            Text(
                                stringResource(if (details.host.protocol == "mosh") R.string.connection_info_ssh_bootstrap else R.string.connection_info_ssh_negotiation),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        val ssh = details.ssh
                        if (ssh == null) {
                            item { Text(stringResource(R.string.connection_info_unavailable)) }
                        } else {
                            item { ConnectionInfoField(stringResource(R.string.connection_info_key_exchange), ssh.kexAlgorithm) }
                            item { ConnectionInfoField(stringResource(R.string.connection_info_host_key), ssh.serverHostKeyAlgorithm) }
                            item { ConnectionInfoField(stringResource(R.string.connection_info_cipher_outbound), ssh.encryptionAlgorithmC2S) }
                            item { ConnectionInfoField(stringResource(R.string.connection_info_cipher_inbound), ssh.encryptionAlgorithmS2C) }
                            item { ConnectionInfoField(stringResource(R.string.connection_info_mac_outbound), ssh.macAlgorithmC2S ?: stringResource(R.string.connection_info_integrated_mac)) }
                            item { ConnectionInfoField(stringResource(R.string.connection_info_mac_inbound), ssh.macAlgorithmS2C ?: stringResource(R.string.connection_info_integrated_mac)) }
                            item {
                                ConnectionInfoField(
                                    stringResource(R.string.connection_info_post_quantum),
                                    stringResource(if (ssh.isPostQuantumSecure) R.string.connection_info_yes else R.string.connection_info_no),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_close)) }
        },
    )
}

@Composable
private fun ConnectionInfoField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
