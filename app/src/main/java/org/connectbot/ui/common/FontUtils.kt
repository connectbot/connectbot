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

package org.connectbot.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.connectbot.R
import org.connectbot.util.TerminalFont

/**
 * Get the localized display name for a font family value.
 * Handles null (default), SYSTEM_DEFAULT, custom fonts, and local fonts.
 */
@Composable
fun getLocalizedFontDisplayName(fontFamily: String?): String {
    val systemDefaultName = stringResource(R.string.font_system_default)

    if (fontFamily == null) {
        return systemDefaultName
    }

    val displayName = TerminalFont.getDisplayName(fontFamily)
    // If the stored value equals SYSTEM_DEFAULT, use localized name
    return if (displayName == TerminalFont.SYSTEM_DEFAULT.displayName) {
        systemDefaultName
    } else {
        displayName
    }
}
