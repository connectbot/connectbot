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
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

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

        // Load font using suspendCancellableCoroutine for proper coroutine integration
        state = FontLoadingState.Loading
        val loadedTypeface = suspendCancellableCoroutine { continuation ->
            fontProvider.loadFont(font) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }
        state = if (loadedTypeface == Typeface.MONOSPACE && font != TerminalFont.SYSTEM_DEFAULT) {
            FontLoadingState.Error(fallback, "Failed to load font")
        } else {
            FontLoadingState.Loaded(loadedTypeface)
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
 * Result of loading a terminal typeface from a stored value.
 */
data class TerminalTypefaceResult(
    val typeface: Typeface,
    val isLoading: Boolean,
    val loadFailed: Boolean,
    val requestedFontName: String?
)

/**
 * Remember a terminal typeface from a stored value (preset enum name, custom:FontName, or local:filename).
 * Returns the fallback immediately while loading, along with loading state information.
 *
 * @param storedValue The stored font value (e.g., "JETBRAINS_MONO", "custom:Cascadia Code", or "local:font.ttf")
 * @param fallback Fallback typeface while loading or on error
 * @return TerminalTypefaceResult containing the typeface and loading state
 */
@Composable
fun rememberTerminalTypefaceResultFromStoredValue(
    storedValue: String?,
    fallback: Typeface = Typeface.MONOSPACE
): TerminalTypefaceResult {
    val context = LocalContext.current
    val fontProvider = remember { TerminalFontProvider(context) }
    val localFontProvider = remember { LocalFontProvider(context) }
    var typeface by remember(storedValue) { mutableStateOf(fallback) }
    var isLoading by remember(storedValue) { mutableStateOf(true) }
    var loadFailed by remember(storedValue) { mutableStateOf(false) }

    val requestedFontName = remember(storedValue) {
        TerminalFont.getDisplayName(storedValue)
    }

    LaunchedEffect(storedValue) {
        Timber.d("Loading font for storedValue: $storedValue")
        isLoading = true
        loadFailed = false

        // Check if it's a local font
        if (LocalFontProvider.isLocalFont(storedValue)) {
            val fileName = LocalFontProvider.getLocalFontFileName(storedValue)
            Timber.d("Local font detected, fileName: $fileName")
            if (fileName != null) {
                val loadedTypeface = localFontProvider.getTypeface(fileName, fallback)
                typeface = loadedTypeface
                loadFailed = loadedTypeface == fallback
            } else {
                typeface = fallback
                loadFailed = true
            }
            isLoading = false
            return@LaunchedEffect
        }

        // Handle Google Fonts (preset or custom)
        val googleFontName = TerminalFont.getGoogleFontName(storedValue)
        Timber.d("Google font name resolved: '$googleFontName'")
        if (googleFontName.isBlank()) {
            Timber.d("Google font name is blank, using fallback")
            typeface = fallback
            isLoading = false
            // Not a failure if it's SYSTEM_DEFAULT
            loadFailed = storedValue != null &&
                storedValue != TerminalFont.SYSTEM_DEFAULT.name &&
                storedValue != TerminalFont.SYSTEM_DEFAULT.displayName
            return@LaunchedEffect
        }

        // Check cache first
        val cached = fontProvider.getCachedTypefaceByName(googleFontName)
        if (cached != null) {
            Timber.d("Font found in cache: $googleFontName")
            typeface = cached
            isLoading = false
            loadFailed = false
            return@LaunchedEffect
        }

        // Load font using suspend function (runs on Dispatchers.IO)
        Timber.d("Loading font from provider: $googleFontName")
        val loadedTypeface = fontProvider.loadFontByNameSuspend(googleFontName)
        Timber.d("Font loaded: $googleFontName, typeface: $loadedTypeface")
        typeface = loadedTypeface
        // If we got MONOSPACE back but didn't request it, the load failed
        loadFailed = loadedTypeface == Typeface.MONOSPACE
        isLoading = false
        Timber.d("Font set to: $typeface, loadFailed: $loadFailed")
    }

    return TerminalTypefaceResult(
        typeface = typeface,
        isLoading = isLoading,
        loadFailed = loadFailed,
        requestedFontName = requestedFontName
    )
}

/**
 * Remember a terminal typeface from a stored value (preset enum name, custom:FontName, or local:filename).
 * Returns the fallback immediately while loading.
 *
 * @param storedValue The stored font value (e.g., "JETBRAINS_MONO", "custom:Cascadia Code", or "local:font.ttf")
 * @param fallback Fallback typeface while loading or on error
 * @return The loaded typeface or fallback
 */
@Composable
fun rememberTerminalTypefaceFromStoredValue(
    storedValue: String?,
    fallback: Typeface = Typeface.MONOSPACE
): Typeface = rememberTerminalTypefaceResultFromStoredValue(storedValue, fallback).typeface

/**
 * Remember a terminal font provider instance.
 */
@Composable
fun rememberTerminalFontProvider(): TerminalFontProvider {
    val context = LocalContext.current
    return remember { TerminalFontProvider(context) }
}
