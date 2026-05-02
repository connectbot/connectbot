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

package org.connectbot.ui.screens.portforwardlist

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.util.HostConstants

enum class SourceAddressOption(val sshValue: String, @StringRes val labelRes: Int) {
    LOCALHOST("localhost", R.string.portforward_source_addr_localhost),
    LOCALHOST_IPV4("127.0.0.1", R.string.portforward_source_addr_localhost_ipv4),
    LOCALHOST_IPV6("::1", R.string.portforward_source_addr_localhost_ipv6),
    ALL("", R.string.portforward_source_addr_all),
    ALL_IPV4("0.0.0.0", R.string.portforward_source_addr_all_ipv4),
    ALL_IPV6("::", R.string.portforward_source_addr_all_ipv6),
    SPECIFIC("", R.string.portforward_source_addr_specific),
    ;

    companion object {
        fun fromSshValue(value: String?): SourceAddressOption = when (value) {
            "localhost" -> LOCALHOST
            "127.0.0.1" -> LOCALHOST_IPV4
            "::1" -> LOCALHOST_IPV6
            "" -> ALL
            "0.0.0.0" -> ALL_IPV4
            "::" -> ALL_IPV6
            null -> LOCALHOST
            else -> SPECIFIC
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardEditorDialog(
    onDismiss: () -> Unit,
    onSave: (nickname: String, type: String, sourcePort: String, sourceAddr: String, destination: String) -> Unit,
    initialNickname: String = "",
    initialType: String = HostConstants.PORTFORWARD_LOCAL,
    initialSourcePort: String = "",
    initialSourceAddr: String? = "localhost",
    initialDestination: String = "",
    isEditing: Boolean = false,
) {
    var nickname by remember { mutableStateOf(initialNickname) }
    var sourcePort by remember { mutableStateOf(initialSourcePort) }
    var destination by remember { mutableStateOf(initialDestination) }

    val initialTypeIndex = when (initialType) {
        HostConstants.PORTFORWARD_LOCAL -> 0
        HostConstants.PORTFORWARD_REMOTE -> 1
        HostConstants.PORTFORWARD_DYNAMIC5 -> 2
        else -> 0
    }
    var typeIndex by remember { mutableIntStateOf(initialTypeIndex) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    val portForwardTypes = stringArrayResource(R.array.list_portforward_types)

    val initialSourceOption = SourceAddressOption.fromSshValue(initialSourceAddr)
    var sourceAddressOption by remember { mutableStateOf(initialSourceOption) }
    var sourceAddressMenuExpanded by remember { mutableStateOf(false) }
    var specificAddress by remember {
        mutableStateOf(
            if (initialSourceOption == SourceAddressOption.SPECIFIC) initialSourceAddr ?: "" else "",
        )
    }

    val typeString = when (typeIndex) {
        0 -> HostConstants.PORTFORWARD_LOCAL
        1 -> HostConstants.PORTFORWARD_REMOTE
        2 -> HostConstants.PORTFORWARD_DYNAMIC5
        else -> HostConstants.PORTFORWARD_LOCAL
    }

    val isRemote = typeIndex == 1
    val needsDestination = typeIndex != 2

    val sourcePortValue = sourcePort.ifEmpty { "8080" }.toIntOrNull()
    val isSourcePortValid = sourcePortValue != null && sourcePortValue in 1..65535

    val isDestinationValid = if (needsDestination) {
        val dest = destination.ifEmpty { "localhost:80" }
        val parts = dest.split(":")
        parts.size == 2 && parts[0].isNotEmpty() && parts[1].toIntOrNull() != null
    } else {
        true
    }

    val isSpecificAddressValid = sourceAddressOption != SourceAddressOption.SPECIFIC || specificAddress.isNotEmpty()

    val canSave = isSourcePortValid && isDestinationValid && (!isRemote || isSpecificAddressValid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.portforward_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text(stringResource(R.string.prompt_nickname)) },
                    placeholder = { Text(stringResource(R.string.portforward_nickname_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = portForwardTypes[typeIndex],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.prompt_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false },
                    ) {
                        portForwardTypes.forEachIndexed { index, type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    typeIndex = index
                                    typeMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isRemote,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))

                        ExposedDropdownMenuBox(
                            expanded = sourceAddressMenuExpanded,
                            onExpandedChange = { sourceAddressMenuExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = stringResource(sourceAddressOption.labelRes),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.portforward_host_address)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceAddressMenuExpanded) },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = sourceAddressMenuExpanded,
                                onDismissRequest = { sourceAddressMenuExpanded = false },
                            ) {
                                SourceAddressOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.labelRes)) },
                                        onClick = {
                                            sourceAddressOption = option
                                            sourceAddressMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        if (sourceAddressOption == SourceAddressOption.SPECIFIC) {
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = specificAddress,
                                onValueChange = { specificAddress = it },
                                label = { Text(stringResource(R.string.portforward_host_address)) },
                                placeholder = { Text(stringResource(R.string.portforward_source_addr_specific_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = specificAddress.isEmpty(),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = sourcePort,
                    onValueChange = { sourcePort = it },
                    label = { Text(stringResource(R.string.prompt_source_port)) },
                    placeholder = { Text(stringResource(R.string.portforward_source_port_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = sourcePort.isNotEmpty() && !isSourcePortValid,
                    supportingText = if (sourcePort.isNotEmpty() && !isSourcePortValid) {
                        { Text(stringResource(R.string.portforward_port_range_error)) }
                    } else {
                        null
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text(stringResource(R.string.prompt_destination)) },
                    placeholder = { Text(stringResource(R.string.portforward_destination_placeholder)) },
                    enabled = needsDestination,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = needsDestination && destination.isNotEmpty() && !isDestinationValid,
                    supportingText = if (needsDestination && destination.isNotEmpty() && !isDestinationValid) {
                        { Text(stringResource(R.string.portforward_destination_format_error)) }
                    } else {
                        null
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalSourcePort = sourcePort.ifEmpty { "8080" }
                    val finalSourceAddr = if (isRemote) {
                        if (sourceAddressOption == SourceAddressOption.SPECIFIC) {
                            specificAddress
                        } else {
                            sourceAddressOption.sshValue
                        }
                    } else {
                        "localhost"
                    }
                    val finalDestination = if (needsDestination) {
                        destination.ifEmpty { "localhost:80" }
                    } else {
                        destination
                    }
                    onSave(nickname, typeString, finalSourcePort, finalSourceAddr, finalDestination)
                },
                enabled = canSave,
            ) {
                Text(stringResource(if (isEditing) R.string.portforward_save else R.string.portforward_pos))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_neg))
            }
        },
    )
}
