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

package org.connectbot.ui.screens.sftpbrowser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.connectbot.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SftpEntryItem(
    entry: SftpEntry,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        ListItem(
            headlineContent = {
                Text(
                    text = entry.filename,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                val details = buildString {
                    if (!entry.isDirectory) {
                        append(formatFileSize(entry.size))
                    }
                    entry.modifiedTime?.let { time ->
                        if (isNotEmpty()) append(" • ")
                        append(formatDate(time))
                    }
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            leadingContent = {
                Icon(
                    imageVector = when {
                        entry.isSymlink -> Icons.Default.Link
                        entry.isDirectory -> Icons.Default.Folder
                        else -> Icons.Default.Description
                    },
                    contentDescription = when {
                        entry.isSymlink -> stringResource(R.string.sftp_symlink)
                        entry.isDirectory -> stringResource(R.string.sftp_directory)
                        else -> stringResource(R.string.sftp_file)
                    },
                    tint = when {
                        entry.isSymlink -> MaterialTheme.colorScheme.tertiary
                        entry.isDirectory -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
            },
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (!entry.isDirectory) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sftp_download_file)) },
                    onClick = {
                        showMenu = false
                        onDownload()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sftp_delete)) },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            )
        }
    }
}

private fun formatFileSize(size: Long?): String {
    if (size == null) return ""
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024))
        else -> String.format(Locale.US, "%.1f GB", size / (1024.0 * 1024 * 1024))
    }
}

private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp * 1000) // SFTP timestamps are in seconds
    val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return format.format(date)
}
