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

package org.connectbot.util

import android.os.Build

/** Weight values shared by profile storage, the editor, and terminal rendering. */
object TerminalFontWeight {
    const val NORMAL = 400
    const val MEDIUM = 500
    const val SEMIBOLD = 600
    const val BOLD = 700

    val values = listOf(NORMAL, MEDIUM, SEMIBOLD, BOLD)

    /** Numeric weight matching for an existing typeface was added in Android 9. */
    fun availableWeights(): List<Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        values
    } else {
        listOf(NORMAL, BOLD)
    }

    /** Resolve saved weights consistently in the editor and renderer on older devices. */
    fun effectiveWeight(weight: Int): Int {
        val normalized = weight.takeIf { it in values } ?: NORMAL
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            normalized
        } else if (normalized >= SEMIBOLD) {
            BOLD
        } else {
            NORMAL
        }
    }
}
