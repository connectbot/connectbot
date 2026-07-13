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

package org.connectbot.ui.screens.console.tmux

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.service.tmux.TmuxAttachState
import org.connectbot.service.tmux.TmuxAvailability
import org.connectbot.service.tmux.TmuxHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TmuxHubSheet(
    state: TmuxHostState,
    onSelectSession: (String) -> Unit,
    onCreateSession: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("tmux_hub"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.tmux_hub_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            if (state.availability == TmuxAvailability.PROBING) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(24.dp),
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.tmux_hub_detecting))
                }
            } else {
                if (state.sessions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tmux_hub_empty),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                } else {
                    state.sessions.forEach { session ->
                        ListItem(
                            headlineContent = { Text(session.name) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        when (session.attachState) {
                                            TmuxAttachState.ATTACHED -> R.string.tmux_hub_attached
                                            TmuxAttachState.ATTACHING -> R.string.tmux_hub_attaching
                                            TmuxAttachState.DETACHED -> R.string.tmux_hub_detached
                                        },
                                    ),
                                )
                            },
                            modifier = Modifier
                                .clickable { onSelectSession(session.id) }
                                .testTag("tmux_hub_session_${session.id}"),
                        )
                        HorizontalDivider()
                    }
                }
                Button(
                    onClick = onCreateSession,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).testTag("tmux_hub_create"),
                ) {
                    Text(stringResource(R.string.tmux_hub_create))
                }
            }
        }
    }
}
