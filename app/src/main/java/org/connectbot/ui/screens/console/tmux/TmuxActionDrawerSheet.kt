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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.keyboard.TmuxAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TmuxActionDrawerSheet(
    onAction: (TmuxAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("tmux_action_drawer"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.tmux_actions_title))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(136.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .padding(vertical = 12.dp),
            ) {
                items(TmuxAction.entries) { action ->
                    FilledTonalButton(
                        onClick = { onAction(action) },
                        modifier = Modifier.testTag("tmux_action_${action.name}"),
                    ) {
                        Text(tmuxActionLabel(action))
                    }
                }
            }
        }
    }
}

@Composable
private fun tmuxActionLabel(action: TmuxAction): String = stringResource(
    when (action) {
        TmuxAction.SPLIT_H -> R.string.tmux_action_split_h
        TmuxAction.SPLIT_V -> R.string.tmux_action_split_v
        TmuxAction.ZOOM -> R.string.tmux_action_zoom
        TmuxAction.KILL_PANE -> R.string.tmux_action_kill_pane
        TmuxAction.BREAK_PANE -> R.string.tmux_action_break_pane
        TmuxAction.NEW_WINDOW -> R.string.tmux_action_new_window
        TmuxAction.NEXT_WINDOW -> R.string.tmux_action_next_window
        TmuxAction.PREV_WINDOW -> R.string.tmux_action_prev_window
        TmuxAction.NEXT_PANE -> R.string.tmux_action_next_pane
        TmuxAction.PREV_PANE -> R.string.tmux_action_prev_pane
        TmuxAction.COPY_MODE -> R.string.tmux_action_copy_mode
        TmuxAction.PALETTE -> R.string.tmux_action_palette
        TmuxAction.PREFIX -> R.string.tmux_action_prefix
        TmuxAction.OPEN_DRAWER -> R.string.tmux_action_open_drawer
    },
)
