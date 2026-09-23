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

package org.connectbot

import androidx.compose.ui.text.AnnotatedString
import org.connectbot.ui.components.SpecialCharVisualTransformation
import org.junit.Assert.assertEquals
import org.junit.Test

class SpecialCharVisualTransformationTest {
    @Test
    fun showsCrLfTabsAndPreservesUnicodeAndOffsets() {
        val original = "世\r\n\t🙂"
        val transformed = SpecialCharVisualTransformation.filter(AnnotatedString(original))
        assertEquals("世␍↩\n⇥🙂", transformed.text.text)
        for (offset in 0..original.length) {
            assertEquals(offset, transformed.offsetMapping.transformedToOriginal(transformed.offsetMapping.originalToTransformed(offset)))
        }
        assertEquals(2, transformed.offsetMapping.transformedToOriginal(3))
        assertEquals("世\r\n\t🙂", original)
    }

    @Test
    fun emptyInputHasValidCursorMapping() {
        val transformed = SpecialCharVisualTransformation.filter(AnnotatedString(""))
        assertEquals("", transformed.text.text)
        assertEquals(0, transformed.offsetMapping.originalToTransformed(0))
        assertEquals(0, transformed.offsetMapping.transformedToOriginal(0))
    }
}
