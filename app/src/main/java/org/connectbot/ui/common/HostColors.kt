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

package org.connectbot.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.core.graphics.toColorInt
import org.connectbot.R

/**
 * Parse a host's stored identification color into a Compose [Color].
 *
 * Hosts store hex values (see [getIconColors]), but old database entries may
 * contain values [android.graphics.Color.parseColor] cannot handle, so any
 * unparseable or missing value falls back to the default host color.
 */
@Composable
fun parseHostColor(colorString: String?): Color {
    val fallback = colorResource(R.color.host_blue)
    if (colorString.isNullOrBlank()) {
        return fallback
    }
    return try {
        Color(colorString.toColorInt())
    } catch (_: IllegalArgumentException) {
        fallback
    }
}
