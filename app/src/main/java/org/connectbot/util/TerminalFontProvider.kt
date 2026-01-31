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

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.connectbot.R
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Provider for loading terminal fonts via Google Fonts API.
 * Fonts are cached after loading for performance.
 * Supports both preset fonts (TerminalFont enum) and custom font names.
 *
 * All font loading operations run on Dispatchers.IO to avoid blocking the main thread.
 */
class TerminalFontProvider(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val fontCache = ConcurrentHashMap<String, Typeface>()
    private val loadingFonts = ConcurrentHashMap<String, Boolean>()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val ioExecutor = ioDispatcher.asExecutor()

    /**
     * Load a font by its Google Fonts name using coroutines.
     * Returns the loaded typeface or Typeface.MONOSPACE on failure.
     *
     * @param googleFontName The exact name as it appears on Google Fonts (e.g., "JetBrains Mono")
     * @return The loaded Typeface, or Typeface.MONOSPACE on failure
     */
    suspend fun loadFontByNameSuspend(googleFontName: String): Typeface = withContext(ioDispatcher) {
        // Empty name returns monospace immediately
        if (googleFontName.isBlank()) {
            return@withContext Typeface.MONOSPACE
        }

        val cacheKey = googleFontName.lowercase()

        // Return cached font if available
        fontCache[cacheKey]?.let {
            return@withContext it
        }

        // Wait if another coroutine is already loading this font
        while (loadingFonts.putIfAbsent(cacheKey, true) != null) {
            // Check if it got cached while we waited
            fontCache[cacheKey]?.let {
                return@withContext it
            }
            // Small delay before checking again
            kotlinx.coroutines.delay(POLL_INTERVAL_MS)
        }

        try {
            val typeface = suspendCancellableCoroutine<Typeface> { continuation ->
                val request = FontRequest(
                    "com.google.android.gms.fonts",
                    "com.google.android.gms",
                    googleFontName,
                    R.array.com_google_android_gms_fonts_certs
                )

                val fontCallback = object : FontsContractCompat.FontRequestCallback() {
                    override fun onTypefaceRetrieved(typeface: Typeface) {
                        Timber.d("Font loaded: $googleFontName")
                        if (continuation.isActive) {
                            continuation.resume(typeface)
                        }
                    }

                    override fun onTypefaceRequestFailed(reason: Int) {
                        Timber.w("Font request failed for $googleFontName: $reason")
                        if (continuation.isActive) {
                            continuation.resume(Typeface.MONOSPACE)
                        }
                    }
                }

                FontsContractCompat.requestFont(
                    context,
                    request,
                    Typeface.NORMAL,
                    ioExecutor,
                    ioExecutor,
                    fontCallback
                )
            }

            // Cache the loaded font
            if (typeface != Typeface.MONOSPACE) {
                fontCache[cacheKey] = typeface
            }
            typeface
        } finally {
            loadingFonts.remove(cacheKey)
        }
    }

    /**
     * Load a font asynchronously by its Google Fonts name and invoke the callback when ready.
     * If the font is already cached, the callback is invoked immediately.
     *
     * @param googleFontName The exact name as it appears on Google Fonts (e.g., "JetBrains Mono")
     * @param callback Called with the loaded typeface, or Typeface.MONOSPACE on failure
     */
    fun loadFontByName(googleFontName: String, callback: (Typeface) -> Unit) {
        // Empty name returns monospace immediately
        if (googleFontName.isBlank()) {
            callback(Typeface.MONOSPACE)
            return
        }

        val cacheKey = googleFontName.lowercase()

        // Return cached font if available
        fontCache[cacheKey]?.let {
            callback(it)
            return
        }

        // Launch coroutine to load font on IO dispatcher
        scope.launch {
            val typeface = loadFontByNameSuspend(googleFontName)
            withContext(Dispatchers.Main) {
                callback(typeface)
            }
        }
    }

    /**
     * Load a preset font using coroutines.
     * Returns the loaded typeface or Typeface.MONOSPACE on failure.
     */
    suspend fun loadFontSuspend(font: TerminalFont): Typeface {
        if (font == TerminalFont.SYSTEM_DEFAULT) {
            return Typeface.MONOSPACE
        }
        return loadFontByNameSuspend(font.googleFontName)
    }

    /**
     * Load a preset font asynchronously and invoke the callback when ready.
     * If the font is already cached, the callback is invoked immediately.
     */
    fun loadFont(font: TerminalFont, callback: (Typeface) -> Unit) {
        // System default returns monospace immediately
        if (font == TerminalFont.SYSTEM_DEFAULT) {
            callback(Typeface.MONOSPACE)
            return
        }

        loadFontByName(font.googleFontName, callback)
    }

    /**
     * Load a font from a stored value using coroutines.
     */
    suspend fun loadFontFromStoredValueSuspend(storedValue: String?): Typeface {
        val googleFontName = TerminalFont.getGoogleFontName(storedValue)
        if (googleFontName.isBlank()) {
            return Typeface.MONOSPACE
        }
        return loadFontByNameSuspend(googleFontName)
    }

    /**
     * Load a font from a stored value (either preset enum name or custom:FontName).
     */
    fun loadFontFromStoredValue(storedValue: String?, callback: (Typeface) -> Unit) {
        val googleFontName = TerminalFont.getGoogleFontName(storedValue)
        if (googleFontName.isBlank()) {
            callback(Typeface.MONOSPACE)
            return
        }
        loadFontByName(googleFontName, callback)
    }

    /**
     * Get a typeface synchronously by Google Fonts name, returning the fallback if not yet loaded.
     * Also triggers loading if not already cached.
     */
    fun getTypefaceByName(googleFontName: String, fallback: Typeface = Typeface.MONOSPACE): Typeface {
        if (googleFontName.isBlank()) {
            return fallback
        }

        val cacheKey = googleFontName.lowercase()
        fontCache[cacheKey]?.let { return it }

        // Trigger loading in background using coroutine
        scope.launch {
            loadFontByNameSuspend(googleFontName)
        }

        return fallback
    }

    /**
     * Get a typeface synchronously, returning the fallback if not yet loaded.
     * Also triggers loading if not already cached.
     */
    fun getTypeface(font: TerminalFont, fallback: Typeface = Typeface.MONOSPACE): Typeface {
        if (font == TerminalFont.SYSTEM_DEFAULT) {
            return fallback
        }

        return getTypefaceByName(font.googleFontName, fallback)
    }

    /**
     * Get a cached typeface by Google Fonts name if available, null otherwise.
     */
    fun getCachedTypefaceByName(googleFontName: String): Typeface? {
        if (googleFontName.isBlank()) {
            return Typeface.MONOSPACE
        }
        return fontCache[googleFontName.lowercase()]
    }

    /**
     * Get a cached typeface if available, null otherwise.
     */
    fun getCachedTypeface(font: TerminalFont): Typeface? {
        if (font == TerminalFont.SYSTEM_DEFAULT) {
            return Typeface.MONOSPACE
        }
        return getCachedTypefaceByName(font.googleFontName)
    }

    /**
     * Check if the font provider is available on this device.
     */
    suspend fun isProviderAvailable(): Boolean = withContext(ioDispatcher) {
        try {
            val pm = context.packageManager
            pm.getPackageInfo("com.google.android.gms", 0)
            true
        } catch (_: Exception) {
            Timber.w("Google Play Services not available for fonts")
            false
        }
    }

    /**
     * Preload a list of fonts in the background using coroutines.
     */
    suspend fun preloadFontsSuspend(fonts: List<TerminalFont>) = withContext(ioDispatcher) {
        fonts.forEach { font ->
            loadFontSuspend(font)
        }
    }

    /**
     * Preload a list of fonts in the background.
     */
    fun preloadFonts(fonts: List<TerminalFont>, onComplete: (() -> Unit)? = null) {
        if (fonts.isEmpty()) {
            onComplete?.invoke()
            return
        }

        scope.launch {
            preloadFontsSuspend(fonts)
            onComplete?.let {
                withContext(Dispatchers.Main) {
                    it()
                }
            }
        }
    }

    /**
     * Clear the font cache.
     */
    fun clearCache() {
        fontCache.clear()
    }

    companion object {
        private const val TAG = "TerminalFontProvider"
        private const val POLL_INTERVAL_MS = 50L
    }
}
