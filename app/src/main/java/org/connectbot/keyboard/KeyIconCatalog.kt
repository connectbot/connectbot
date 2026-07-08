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

package org.connectbot.keyboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Curated set of Material icons a user can assign to a custom key, keyed by a
 * stable string id stored in [KeySpec.icon]. Only these ids are recognized;
 * unknown ids fall back to the key's text label.
 */
object KeyIconCatalog {
    private val icons: Map<String, ImageVector> = linkedMapOf(
        "arrow_up" to Icons.Default.KeyboardArrowUp,
        "arrow_down" to Icons.Default.KeyboardArrowDown,
        "arrow_left" to Icons.Default.KeyboardArrowLeft,
        "arrow_right" to Icons.Default.KeyboardArrowRight,
        "up" to Icons.Default.ArrowUpward,
        "down" to Icons.Default.ArrowDownward,
        "enter" to Icons.AutoMirrored.Filled.KeyboardReturn,
        "tab" to Icons.AutoMirrored.Filled.KeyboardTab,
        "home" to Icons.Default.Home,
        "search" to Icons.Default.Search,
        "folder" to Icons.Default.Folder,
        "terminal" to Icons.Default.Terminal,
        "code" to Icons.Default.Code,
        "run" to Icons.Default.PlayArrow,
        "copy" to Icons.Default.ContentCopy,
        "paste" to Icons.Default.ContentPaste,
        "delete" to Icons.Default.Delete,
        "clear" to Icons.Default.Clear,
        "bolt" to Icons.Default.Bolt,
        "tag" to Icons.Default.Tag,
        "star" to Icons.Default.Star,
        "settings" to Icons.Default.Settings,
    )

    /** All selectable (id, icon) pairs, in display order. */
    val entries: List<Pair<String, ImageVector>> = icons.toList()

    fun iconFor(id: String?): ImageVector? = id?.let { icons[it] }
}
