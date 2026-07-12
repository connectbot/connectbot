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

const val AUTOMATIC_FONT_SIZE = 0
const val PHONE_DEFAULT_FONT_SIZE_SP = 10
const val TABLET_DEFAULT_FONT_SIZE_SP = 14
const val TABLET_SMALLEST_WIDTH_DP = 600

fun adaptiveTerminalFontSize(smallestScreenWidthDp: Int, einkMode: Boolean = false): Int {
    val size = if (smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP) {
        TABLET_DEFAULT_FONT_SIZE_SP
    } else {
        PHONE_DEFAULT_FONT_SIZE_SP
    }
    return if (einkMode) maxOf(size, TABLET_DEFAULT_FONT_SIZE_SP) else size
}
