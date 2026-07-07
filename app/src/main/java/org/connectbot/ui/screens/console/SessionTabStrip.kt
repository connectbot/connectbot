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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.connectbot.ui.common.parseHostColor
import org.connectbot.ui.theme.ConnectBotTheme

/**
 * Everything the session tab strip needs to render one tab: a host's shell
 * console or a tmux session on it. Tabs are identified by [key]
 * (see [ConsoleTab]); tmux tabs share their host's color.
 */
data class SessionTabData(
    val key: String,
    val nickname: String,
    val color: String?,
    val isDisconnected: Boolean,
    /** True for tmux session tabs (rendered dimmed while detached). */
    val isTmux: Boolean = false,
    /** Control channel being established: shows a spinner in the dot slot. */
    val isAttaching: Boolean = false,
    /** A window in this session rang its bell. */
    val bellBadge: Boolean = false,
    /** A window in this session saw activity. */
    val activityBadge: Boolean = false,
)

/**
 * A persistent, horizontally scrollable strip of tabs: one per open host
 * connection plus one per tmux session, grouped by host. Shown under the
 * console title bar when more than one tab exists.
 *
 * Each tab shows the host's identification color as a dot (hollow while
 * disconnected/detached) and underlines the selected tab in that color. The
 * strip scrolls automatically to keep the selected tab visible.
 */
@Composable
fun SessionTabStrip(
    tabs: List<SessionTabData>,
    selectedKey: String?,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedKey, tabs.size) {
        val index = tabs.indexOfFirst { it.key == selectedKey }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .testTag("session_tab_strip"),
    ) {
        itemsIndexed(
            items = tabs,
            key = { _, tab -> tab.key },
        ) { _, tab ->
            SessionTab(
                tab = tab,
                selected = tab.key == selectedKey,
                onClick = { onSelectTab(tab.key) },
            )
        }
    }
}

@Composable
private fun SessionTab(
    tab: SessionTabData,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hostColor = parseHostColor(tab.color)
    val baseTextColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textColor = if (tab.isDisconnected) {
        baseTextColor.copy(alpha = 0.5f)
    } else {
        baseTextColor
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .widthIn(min = 72.dp, max = 200.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .testTag("session_tab_${tab.key}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (tab.isAttaching) {
                CircularProgressIndicator(
                    strokeWidth = 1.5.dp,
                    color = hostColor,
                    modifier = Modifier
                        .size(10.dp)
                        .testTag("tab_attaching_${tab.key}"),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .then(
                            if (tab.isDisconnected) {
                                Modifier.border(1.dp, hostColor, CircleShape)
                            } else {
                                Modifier.background(hostColor, CircleShape)
                            },
                        ),
                )
            }
            Text(
                text = tab.nickname,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
            if (tab.bellBadge || tab.activityBadge) {
                val badgeColor = if (tab.bellBadge) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(6.dp)
                        .background(badgeColor, CircleShape)
                        .testTag("tab_badge_${tab.key}"),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) hostColor else Color.Transparent),
        )
    }
}

@Preview
@Composable
private fun SessionTabStripPreview() {
    ConnectBotTheme {
        SessionTabStrip(
            tabs = listOf(
                SessionTabData(key = "host:1", nickname = "web-01", color = "#F44336", isDisconnected = false),
                SessionTabData(
                    key = "tmux:1:\$0",
                    nickname = "main",
                    color = "#F44336",
                    isDisconnected = false,
                    isTmux = true,
                    bellBadge = true,
                ),
                SessionTabData(
                    key = "tmux:1:\$1",
                    nickname = "deploy",
                    color = "#F44336",
                    isDisconnected = true,
                    isTmux = true,
                ),
                SessionTabData(key = "host:3", nickname = "staging", color = null, isDisconnected = true),
            ),
            selectedKey = "tmux:1:\$0",
            onSelectTab = {},
        )
    }
}
