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

/**
 * Color data for visual identification of hosts/profiles.
 * English names are kept for backward compatibility with old database entries.
 * New entries always store hex values.
 */
data class ColorOption(val englishName: String, val hexValue: String, val localizedName: String)

/**
 * 16 icon colors for visual identification of hosts/profiles.
 * Returns list with English names (for backward compatibility), hex values (for DB storage),
 * and localized names (for UI display).
 */
@Composable
fun getIconColors(): List<ColorOption> = listOf(
    ColorOption("Red", "#F44336", stringResource(R.string.color_red)),
    ColorOption("Pink", "#E91E63", stringResource(R.string.color_pink)),
    ColorOption("Purple", "#9C27B0", stringResource(R.string.color_purple)),
    ColorOption("Deep Purple", "#673AB7", stringResource(R.string.color_deep_purple)),
    ColorOption("Indigo", "#3F51B5", stringResource(R.string.color_indigo)),
    ColorOption("Blue", "#2196F3", stringResource(R.string.color_blue)),
    ColorOption("Light Blue", "#03A9F4", stringResource(R.string.color_light_blue)),
    ColorOption("Cyan", "#00BCD4", stringResource(R.string.color_cyan)),
    ColorOption("Teal", "#009688", stringResource(R.string.color_teal)),
    ColorOption("Green", "#4CAF50", stringResource(R.string.color_green)),
    ColorOption("Light Green", "#8BC34A", stringResource(R.string.color_light_green)),
    ColorOption("Lime", "#CDDC39", stringResource(R.string.color_lime)),
    ColorOption("Yellow", "#FFEB3B", stringResource(R.string.color_yellow)),
    ColorOption("Amber", "#FFC107", stringResource(R.string.color_amber)),
    ColorOption("Orange", "#FF9800", stringResource(R.string.color_orange)),
    ColorOption("Gray", "#9E9E9E", stringResource(R.string.color_gray))
)
