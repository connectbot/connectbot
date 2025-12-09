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

package org.connectbot.util

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * State representing font loading progress.
 */
sealed class FontLoadingState {
    data object Loading : FontLoadingState()
    data class Loaded(val typeface: Typeface) : FontLoadingState()
    data class Error(val fallback: Typeface, val reason: String? = null) : FontLoadingState()
}

/**
 * Remember a terminal font state that handles async loading.
 *
 * @param font The font to load
 * @param fallback Fallback typeface while loading or on error
 * @return Current font loading state
 */
@Composable
fun rememberTerminalFontState(
    font: TerminalFont,
    fallback: Typeface = Typeface.MONOSPACE
): FontLoadingState {
    val context = LocalContext.current
    val fontProvider = remember { TerminalFontProvider(context) }
    var state by remember(font) { mutableStateOf<FontLoadingState>(FontLoadingState.Loading) }

    LaunchedEffect(font) {
        // Check cache first
        val cached = fontProvider.getCachedTypeface(font)
        if (cached != null) {
            state = FontLoadingState.Loaded(cached)
            return@LaunchedEffect
        }

        // Load font
        state = FontLoadingState.Loading
        fontProvider.loadFont(font) { typeface ->
            state = if (typeface == Typeface.MONOSPACE && font != TerminalFont.SYSTEM_DEFAULT) {
                FontLoadingState.Error(fallback, "Failed to load font")
            } else {
                FontLoadingState.Loaded(typeface)
            }
        }
    }

    return state
}

/**
 * Remember a terminal typeface, loading it asynchronously.
 * Returns the fallback immediately while loading.
 *
 * @param font The font to load
 * @param fallback Fallback typeface while loading or on error
 * @return The loaded typeface or fallback
 */
@Composable
fun rememberTerminalTypeface(
    font: TerminalFont,
    fallback: Typeface = Typeface.MONOSPACE
): Typeface {
    val state = rememberTerminalFontState(font, fallback)
    return when (state) {
        is FontLoadingState.Loading -> fallback
        is FontLoadingState.Loaded -> state.typeface
        is FontLoadingState.Error -> state.fallback
    }
}

/**
 * Remember a terminal typeface from a stored value (preset enum name or custom:FontName).
 * Returns the fallback immediately while loading.
 *
 * @param storedValue The stored font value (e.g., "JETBRAINS_MONO" or "custom:Cascadia Code")
 * @param fallback Fallback typeface while loading or on error
 * @return The loaded typeface or fallback
 */
@Composable
fun rememberTerminalTypefaceFromStoredValue(
    storedValue: String?,
    fallback: Typeface = Typeface.MONOSPACE
): Typeface {
    val context = LocalContext.current
    val fontProvider = remember { TerminalFontProvider(context) }
    var typeface by remember(storedValue) { mutableStateOf(fallback) }

    LaunchedEffect(storedValue) {
        val googleFontName = TerminalFont.getGoogleFontName(storedValue)
        if (googleFontName.isBlank()) {
            typeface = fallback
            return@LaunchedEffect
        }

        // Check cache first
        val cached = fontProvider.getCachedTypefaceByName(googleFontName)
        if (cached != null) {
            typeface = cached
            return@LaunchedEffect
        }

        // Load font
        fontProvider.loadFontByName(googleFontName) { loadedTypeface ->
            typeface = loadedTypeface
        }
    }

    return typeface
}

/**
 * Remember a terminal font provider instance.
 */
@Composable
fun rememberTerminalFontProvider(): TerminalFontProvider {
    val context = LocalContext.current
    return remember { TerminalFontProvider(context) }
}
