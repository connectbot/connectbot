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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.util.HostConstants

internal object PortForwardQuickToggleTestTags {
    const val SHEET = "port_forward_quick_toggle_sheet"
    const val MANAGE_BUTTON = "port_forward_quick_toggle_manage"
    fun switchRow(portForwardId: Long): String = "port_forward_quick_toggle_${portForwardId}_switch"
}

/**
 * Bottom sheet listing a host's port forwards with switches to enable or
 * disable each one on the live connection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardQuickToggleSheet(
    host: Host,
    portForwards: List<PortForward>,
    hasLiveConnection: Boolean,
    onToggle: (PortForward, Boolean) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(PortForwardQuickToggleTestTags.SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.title_port_forwards_list),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = host.nickname,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onManage,
                    modifier = Modifier.testTag(PortForwardQuickToggleTestTags.MANAGE_BUTTON),
                ) {
                    Text(stringResource(R.string.portforward_quick_manage))
                }
            }

            if (!hasLiveConnection) {
                Text(
                    text = stringResource(R.string.portforward_quick_connect_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            portForwards.forEach { portForward ->
                PortForwardToggleRow(
                    portForward = portForward,
                    hasLiveConnection = hasLiveConnection,
                    onToggle = { enable -> onToggle(portForward, enable) },
                )
            }
        }
    }
}

@Composable
private fun PortForwardToggleRow(
    portForward: PortForward,
    hasLiveConnection: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = portForward.nickname,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = portForwardSummary(portForward),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = portForward.isEnabled(),
            onCheckedChange = onToggle,
            enabled = hasLiveConnection,
            modifier = Modifier.testTag(PortForwardQuickToggleTestTags.switchRow(portForward.id)),
        )
    }
}

@Composable
private fun portForwardSummary(portForward: PortForward): String {
    val typeLabel = when (portForward.type) {
        HostConstants.PORTFORWARD_LOCAL -> stringResource(R.string.portforward_local)

        HostConstants.PORTFORWARD_REMOTE -> stringResource(R.string.portforward_remote)

        HostConstants.PORTFORWARD_DYNAMIC4,
        HostConstants.PORTFORWARD_DYNAMIC5,
        -> stringResource(R.string.portforward_dynamic)

        else -> portForward.type
    }

    return when (portForward.type) {
        HostConstants.PORTFORWARD_DYNAMIC4,
        HostConstants.PORTFORWARD_DYNAMIC5,
        -> "$typeLabel • ${portForward.sourcePort}"

        else -> "$typeLabel • ${portForward.sourcePort} → ${portForward.destAddr}:${portForward.destPort}"
    }
}
