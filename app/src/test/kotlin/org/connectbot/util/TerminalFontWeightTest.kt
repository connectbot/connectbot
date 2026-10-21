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

import android.annotation.SuppressLint
import android.graphics.Typeface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TerminalFontWeightTest {
    @Test
    @Config(sdk = [28])
    @SuppressLint("NewApi") // Robolectric @Config above runs this test on API 28.
    fun appliesRequestedWeightAndPreservesItalic() {
        val italic = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
        for (weight in TerminalFontWeight.values) {
            val result = terminalTypefaceWithWeight(italic, weight)
            assertEquals(weight, result.weight)
            assertTrue(result.isItalic)
        }
    }

    @Test
    @Config(sdk = [27])
    fun olderAndroidUsesNearestAvailableStyle() {
        assertFalse(terminalTypefaceWithWeight(Typeface.MONOSPACE, TerminalFontWeight.NORMAL).isBold)
        assertFalse(terminalTypefaceWithWeight(Typeface.MONOSPACE, TerminalFontWeight.MEDIUM).isBold)
        assertTrue(terminalTypefaceWithWeight(Typeface.MONOSPACE, TerminalFontWeight.SEMIBOLD).isBold)
        assertTrue(terminalTypefaceWithWeight(Typeface.MONOSPACE, TerminalFontWeight.BOLD).isBold)
    }

    @Test
    @Config(sdk = [27])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun olderAndroidOffersOnlyRegularAndBold() {
        assertEquals(
            listOf(TerminalFontWeight.NORMAL, TerminalFontWeight.BOLD),
            TerminalFontWeight.availableWeights(),
        )
        assertEquals(TerminalFontWeight.NORMAL, TerminalFontWeight.effectiveWeight(TerminalFontWeight.MEDIUM))
        assertEquals(TerminalFontWeight.BOLD, TerminalFontWeight.effectiveWeight(TerminalFontWeight.SEMIBOLD))
        assertEquals(TerminalFontWeight.NORMAL, TerminalFontWeight.effectiveWeight(-1))
    }

    @Test
    @Config(sdk = [28])
    fun modernAndroidOffersAllWeights() {
        assertEquals(TerminalFontWeight.values, TerminalFontWeight.availableWeights())
        for (weight in TerminalFontWeight.availableWeights()) {
            assertEquals(weight, TerminalFontWeight.effectiveWeight(weight))
        }
    }

    @Test
    @Config(sdk = [27])
    fun olderAndroidPreservesItalicWhenApplyingBold() {
        val italic = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
        val result = terminalTypefaceWithWeight(italic, TerminalFontWeight.BOLD)
        assertTrue(result.isItalic)
        assertTrue(result.isBold)
    }

    @Test
    @Config(sdk = [28])
    @SuppressLint("NewApi") // Robolectric @Config above runs this test on API 28.
    fun invalidStoredWeightFallsBackToNormal() {
        assertEquals(TerminalFontWeight.NORMAL, terminalTypefaceWithWeight(Typeface.MONOSPACE, -1).weight)
    }
}
