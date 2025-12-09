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
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import org.connectbot.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Provider for loading terminal fonts via Google Fonts API.
 * Fonts are cached after loading for performance.
 * Supports both preset fonts (TerminalFont enum) and custom font names.
 */
class TerminalFontProvider(private val context: Context) {
    private val fontCache = ConcurrentHashMap<String, Typeface>()
    private val loadingFonts = ConcurrentHashMap<String, Boolean>()
    private val handlerThread = HandlerThread("FontLoader").apply { start() }
    private val handler = Handler(handlerThread.looper)

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

        // Prevent duplicate loading
        if (loadingFonts.putIfAbsent(cacheKey, true) != null) {
            // Already loading, poll until ready
            pollForFontByName(cacheKey, callback)
            return
        }

        val request = FontRequest(
            "com.google.android.gms.fonts",
            "com.google.android.gms",
            googleFontName,
            R.array.com_google_android_gms_fonts_certs
        )

        val fontCallback = object : FontsContractCompat.FontRequestCallback() {
            override fun onTypefaceRetrieved(typeface: Typeface) {
                Log.d(TAG, "Font loaded: $googleFontName")
                fontCache[cacheKey] = typeface
                loadingFonts.remove(cacheKey)
                callback(typeface)
            }

            override fun onTypefaceRequestFailed(reason: Int) {
                Log.w(TAG, "Font request failed for $googleFontName: $reason")
                loadingFonts.remove(cacheKey)
                // Fall back to monospace on failure
                callback(Typeface.MONOSPACE)
            }
        }

        FontsContractCompat.requestFont(context, request, fontCallback, handler)
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

        // Trigger loading in background
        loadFontByName(googleFontName) { /* just cache it */ }

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
    fun isProviderAvailable(): Boolean {
        return try {
            val pm = context.packageManager
            pm.getPackageInfo("com.google.android.gms", 0)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Google Play Services not available for fonts")
            false
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

        var remaining = fonts.size
        val lock = Any()

        fonts.forEach { font ->
            loadFont(font) {
                synchronized(lock) {
                    remaining--
                    if (remaining == 0) {
                        onComplete?.invoke()
                    }
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

    private fun pollForFontByName(cacheKey: String, callback: (Typeface) -> Unit) {
        handler.postDelayed({
            val cached = fontCache[cacheKey]
            if (cached != null) {
                callback(cached)
            } else if (loadingFonts.containsKey(cacheKey)) {
                // Still loading, poll again
                pollForFontByName(cacheKey, callback)
            } else {
                // Loading finished but no font - use fallback
                callback(Typeface.MONOSPACE)
            }
        }, POLL_INTERVAL_MS)
    }

    companion object {
        private const val TAG = "TerminalFontProvider"
        private const val POLL_INTERVAL_MS = 100L
    }
}
