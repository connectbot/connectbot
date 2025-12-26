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
import org.connectbot.data.entity.ColorScheme

/**
 * Get the localized description for a color scheme.
 * Returns localized descriptions for built-in schemes and the stored description for custom schemes.
 */
@Composable
fun getLocalizedColorSchemeDescription(scheme: ColorScheme): String {
    if (!scheme.isBuiltIn) {
        return scheme.description
    }

    return when (scheme.name) {
        "Default" -> stringResource(R.string.colorscheme_default_description)
        "Solarized Dark" -> stringResource(R.string.colorscheme_solarized_dark_description)
        "Solarized Light" -> stringResource(R.string.colorscheme_solarized_light_description)
        "Dracula" -> stringResource(R.string.colorscheme_dracula_description)
        "Nord" -> stringResource(R.string.colorscheme_nord_description)
        "Gruvbox Dark" -> stringResource(R.string.colorscheme_gruvbox_dark_description)
        "Monokai" -> stringResource(R.string.colorscheme_monokai_description)
        "Tomorrow Night" -> stringResource(R.string.colorscheme_tomorrow_night_description)
        else -> scheme.description // Fallback to stored description
    }
}
