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
import android.content.Intent
import androidx.core.net.toUri

object UrlUtils {
    private val schemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    /**
     * Normalizes a URL string by trimming and adding a default scheme if missing.
     *
     * @param url The raw URL string from terminal.
     * @return The normalized URL string, or an empty string if the input was empty.
     */
    fun normalizeUrl(url: String): String {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return ""

        val schemeMatch = schemeRegex.find(trimmedUrl)
        if (schemeMatch != null) {
            val scheme = schemeMatch.value.removeSuffix(":")
            val rest = trimmedUrl.substring(schemeMatch.value.length)

            // If it has :// it's definitely a scheme.
            // If it's mailto: or tel:, it's a known scheme that doesn't use //.
            if (rest.startsWith("//") ||
                scheme.equals("mailto", ignoreCase = true) ||
                scheme.equals("tel", ignoreCase = true)
            ) {
                return trimmedUrl
            }

            // It's likely a host:port if the scheme part contains a dot (e.g., google.com:80)
            // OR if the part after the colon starts with digits (e.g., localhost:8080).
            val startsWithPort = rest.isNotEmpty() && rest[0].isDigit() &&
                rest.takeWhile { it.isDigit() }.let { digits ->
                    digits.length == rest.length || rest[digits.length] == '/'
                }

            if (scheme.contains('.') || startsWithPort) {
                return "https://$trimmedUrl"
            }

            // Otherwise treat it as a scheme (e.g., "javascript:", "intent:").
            return trimmedUrl
        }

        return "https://$trimmedUrl"
    }

    private val supportedSchemes = setOf("http", "https", "mailto", "tel")

    /**
     * Attempts to open a URL using an ACTION_VIEW intent.
     * Only allows specific safe schemes: http, https, mailto, tel.
     *
     * @param context The context to use for starting the activity.
     * @param url The URL string to open.
     * @return A Result indicating success or failure (e.g., ActivityNotFoundException, IllegalArgumentException).
     */
    fun openUrl(context: Context, url: String): Result<Unit> {
        val normalizedUrl = normalizeUrl(url)
        if (normalizedUrl.isEmpty()) return Result.success(Unit)

        return runCatching {
            val uri = normalizedUrl.toUri()
            val scheme = uri.scheme?.lowercase()

            if (scheme !in supportedSchemes) {
                throw IllegalArgumentException("Unsupported scheme: $scheme")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
