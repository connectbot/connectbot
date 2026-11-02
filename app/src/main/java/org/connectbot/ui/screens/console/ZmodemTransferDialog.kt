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

package org.connectbot.ui.screens.console

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.connectbot.R
import org.connectbot.service.TerminalBridge
import java.util.UUID

@Composable
internal fun ZmodemTransferDialog(bridge: TerminalBridge) {
    val state by bridge.zmodem.state.collectAsState()
    // Save the request identity across recreation; results never target a different bridge.
    var launched by rememberSaveable { mutableStateOf<String?>(null) }
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        launched?.let { bridge.zmodem.selected(UUID.fromString(it), uris) }
    }
    val download = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        launched?.let { bridge.zmodem.selected(UUID.fromString(it), listOfNotNull(uri)) }
    }
    LaunchedEffect(state.picker) {
        state.picker?.let { id ->
            if (launched != id.toString()) {
                launched = id.toString()
                if (state.upload) upload.launch(arrayOf("*/*")) else download.launch(null)
            }
        }
    }
    if (state.error != null && !state.active) {
        AlertDialog(
            onDismissRequest = bridge.zmodem::dismissError,
            title = { Text(stringResource(R.string.zmodem_title)) },
            text = { Text(stringResource(R.string.zmodem_failed)) },
            confirmButton = {
                TextButton(onClick = bridge.zmodem::dismissError) { Text(stringResource(R.string.zmodem_dismiss)) }
            },
        )
    }
    if (state.active) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.zmodem_title)) },
            text = {
                Text(
                    if (state.cancelling) {
                        stringResource(R.string.zmodem_cancelling)
                    } else if (state.name.isEmpty()) {
                        stringResource(R.string.zmodem_select)
                    } else if (state.total != null) {
                        stringResource(R.string.zmodem_progress_total, state.name, state.bytes, state.total!!)
                    } else {
                        stringResource(R.string.zmodem_progress, state.name, state.bytes)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = bridge.zmodem::cancel, enabled = !state.cancelling) { Text(stringResource(R.string.zmodem_cancel)) }
            },
        )
    }
}
