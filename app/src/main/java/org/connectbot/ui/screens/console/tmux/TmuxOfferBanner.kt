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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R

/**
 * The one-tap "start a persistent tmux session" offer, shown on hosts where
 * tmux is installed but no sessions exist. Dismissal is per-connection;
 * long-term opt-out lives in the host editor (tmux mode OFF) or via the
 * persistent dismiss action.
 */
@Composable
fun TmuxOfferBanner(
    onStart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("tmux_offer_banner"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.tmux_offer_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onStart,
                modifier = Modifier.testTag("tmux_offer_start"),
            ) {
                Text(stringResource(R.string.tmux_offer_start))
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("tmux_offer_dismiss"),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.tmux_offer_dismiss),
                )
            }
        }
    }
}
