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

package org.connectbot.ui.screens.keyboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.data.keyboard.KeyboardDraft
import org.connectbot.service.TerminalBridge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConsoleKeyboardSettings(bridge: TerminalBridge, onNavigateBack: () -> Unit, viewModel: KeyboardViewModel = hiltViewModel()) {
    BackHandler(onBack = onNavigateBack)
    val host = bridge.host
    val context by remember(bridge) { viewModel.hostContext(host) }.collectAsState(KeyboardHostContext())
    val session by remember(bridge) { viewModel.repository.sessionSelection(bridge) }.collectAsState()
    var editable by remember(bridge) { mutableStateOf<Boolean?>(if (host.isTemporary) true else null) }
    var pending by rememberSaveable { mutableStateOf<KeyboardDraft?>(null) }
    val error by viewModel.error.collectAsState()
    LaunchedEffect(bridge) {
        viewModel.perform {
            try {
                editable = host.isTemporary || viewModel.repository.canEditHost(host.id)
            } finally {
                if (editable == null) editable = false
            }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.keyboard_settings_title)) }, navigationIcon = {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.button_navigate_up)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            error?.let { Text(stringResource(R.string.keyboard_error, it)) }
            if (host.isTemporary) Text(stringResource(R.string.keyboard_session_only))
            if (editable == null) {
                CircularProgressIndicator()
            } else if (editable == false) {
                Text(stringResource(R.string.keyboard_read_only))
            } else {
                KeyboardLayoutSelection(
                    library = remember(viewModel) { viewModel.libraryState() },
                    selected = if (host.isTemporary) session else context.hostLayoutId,
                    inheritedId = context.profileLayoutId,
                    source = context.profileName?.let { stringResource(R.string.keyboard_from_profile, it) },
                    ownerName = host.nickname.ifBlank { host.hostname },
                    pendingDraft = pending,
                    onDraft = { pending = it },
                    onSelect = {},
                    onApply = { id, draft ->
                        if (host.isTemporary) {
                            draft?.let { viewModel.repository.saveDraft(it) }
                            viewModel.repository.sessionSelection(bridge).value = id
                        } else {
                            viewModel.repository.setHostLayout(host.id, id, draft)
                        }
                        pending = null
                    },
                )
            }
        }
    }
}
