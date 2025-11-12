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

package org.connectbot.ui.theme

import androidx.compose.ui.graphics.Color

val md_theme_light_primary = Color(0xFF03A9F4)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFF40C4FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001F2A)
val md_theme_light_secondary = Color(0xFF0288D1)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFB3E5FC)
val md_theme_light_onSecondaryContainer = Color(0xFF001F2A)
val md_theme_light_tertiary = Color(0xFF00B0FF)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFFCFCFF)
val md_theme_light_onBackground = Color(0xFF1A1C1E)
val md_theme_light_surface = Color(0xFFFCFCFF)
val md_theme_light_onSurface = Color(0xFF1A1C1E)
val md_theme_light_surfaceVariant = Color(0xFFDFE2EB)
val md_theme_light_onSurfaceVariant = Color(0xFF43474E)
val md_theme_light_outline = Color(0xFF73777F)
val md_theme_light_inverseOnSurface = Color(0xFFF1F0F4)
val md_theme_light_inverseSurface = Color(0xFF2F3033)
val md_theme_light_inversePrimary = Color(0xFF40C4FF)

val md_theme_dark_primary = Color(0xFF40C4FF)
val md_theme_dark_onPrimary = Color(0xFF003547)
val md_theme_dark_primaryContainer = Color(0xFF0288D1)
val md_theme_dark_onPrimaryContainer = Color(0xFFB3E5FC)
val md_theme_dark_secondary = Color(0xFF81D4FA)
val md_theme_dark_onSecondary = Color(0xFF00344F)
val md_theme_dark_secondaryContainer = Color(0xFF004D71)
val md_theme_dark_onSecondaryContainer = Color(0xFFB3E5FC)
val md_theme_dark_tertiary = Color(0xFF00B0FF)
val md_theme_dark_onTertiary = Color(0xFF003547)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF1A1C1E)
val md_theme_dark_onBackground = Color(0xFFE2E2E5)
val md_theme_dark_surface = Color(0xFF1A1C1E)
val md_theme_dark_onSurface = Color(0xFFE2E2E5)
val md_theme_dark_surfaceVariant = Color(0xFF43474E)
val md_theme_dark_onSurfaceVariant = Color(0xFFC3C6CF)
val md_theme_dark_outline = Color(0xFF8D9199)
val md_theme_dark_inverseOnSurface = Color(0xFF1A1C1E)
val md_theme_dark_inverseSurface = Color(0xFFE2E2E5)
val md_theme_dark_inversePrimary = Color(0xFF03A9F4)

val KeyBackgroundNormal = Color(0x55F0F0F0)
val KeyBackgroundPressed = Color(0xAAA0A0FF)
val KeyBackgroundLayout = Color(0x55000000)
val KeyboardBackground = Color(0x55B0B0F0)

// Terminal-specific colors (used for overlays over terminal)
// These are independent of light/dark theme since terminal background is always dark
val TerminalOverlayBackground = Color(0x80000000) // Semi-transparent black
val TerminalOverlayText = Color(0xFFFFFFFF) // White
val TerminalOverlayTextSecondary = Color(0xB3FFFFFF) // White at 70% opacity
