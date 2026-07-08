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

package org.connectbot.keyboard

/**
 * Tap-target size preset for the special-keys bar. Values are in dp; [MEDIUM]
 * preserves the historical 45×30 key with 20dp content.
 */
enum class KeyboardKeySize(
    val prefValue: String,
    val keyWidthDp: Int,
    val keyHeightDp: Int,
    val contentDp: Int,
) {
    SMALL("small", 38, 26, 16),
    MEDIUM("medium", 45, 30, 20),
    LARGE("large", 54, 36, 24),
    ;

    companion object {
        fun fromPreferenceValue(value: String?): KeyboardKeySize =
            entries.firstOrNull { it.prefValue == value } ?: MEDIUM
    }
}
