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
 * Everything the session tab strip needs to render one open session.
 */
data class SessionTabData(
    val hostId: Long,
    val nickname: String,
    val color: String?,
    val isDisconnected: Boolean,
)

/**
 * A persistent, horizontally scrollable strip of tabs, one per open session,
 * shown under the console title bar when more than one session is open.
 *
 * Each tab shows the host's identification color as a dot (hollow while the
 * session is disconnected) and underlines the selected session in that same
 * color. The strip scrolls automatically to keep the selected tab visible.
 */
@Composable
fun SessionTabStrip(
    tabs: List<SessionTabData>,
    selectedIndex: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex, tabs.size) {
        if (selectedIndex in tabs.indices) {
            listState.animateScrollToItem(selectedIndex)
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
            key = { _, tab -> tab.hostId },
        ) { index, tab ->
            SessionTab(
                tab = tab,
                selected = index == selectedIndex,
                onClick = { onSelectTab(index) },
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
            .testTag("session_tab_${tab.hostId}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
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
            Text(
                text = tab.nickname,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
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
                SessionTabData(hostId = 1L, nickname = "web-01", color = "#F44336", isDisconnected = false),
                SessionTabData(hostId = 2L, nickname = "db-primary", color = "#4CAF50", isDisconnected = false),
                SessionTabData(hostId = 3L, nickname = "staging", color = null, isDisconnected = true),
            ),
            selectedIndex = 1,
            onSelectTab = {},
        )
    }
}
