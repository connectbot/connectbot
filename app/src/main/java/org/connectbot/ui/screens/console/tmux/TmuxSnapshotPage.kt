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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.connectbot.R
import org.connectbot.ui.theme.terminal

/**
 * The dimmed, non-interactive face of a detached tmux session tab: the last
 * captured screen of its active pane, with a tap-to-attach affordance (or a
 * spinner while the control channel is being established).
 */
@Composable
fun TmuxSnapshotPage(
    sessionName: String,
    snapshot: List<String>?,
    isAttaching: Boolean,
    onAttach: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val terminalColors = MaterialTheme.colorScheme.terminal

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !isAttaching, onClick = onAttach)
            .testTag("tmux_snapshot_page"),
    ) {
        if (!snapshot.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                snapshot.forEach { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .background(
                    terminalColors.overlayBackground,
                    MaterialTheme.shapes.medium,
                )
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            if (isAttaching) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("tmux_attaching_indicator"),
                )
                Text(
                    text = stringResource(R.string.tmux_attaching, sessionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = terminalColors.overlayText,
                )
            } else {
                Text(
                    text = stringResource(R.string.tmux_tap_to_attach, sessionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = terminalColors.overlayText,
                    modifier = Modifier.testTag("tmux_tap_to_attach"),
                )
            }
        }
    }
}
