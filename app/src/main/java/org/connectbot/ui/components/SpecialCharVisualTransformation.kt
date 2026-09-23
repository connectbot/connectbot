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

package org.connectbot.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private const val NEWLINE_SYMBOL = "↩"
private const val TAB_SYMBOL = "⇥"

object SpecialCharVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text

        // Build transformed string and a map from original index to transformed index
        val transformedBuilder = StringBuilder()
        val originalToTransformed = IntArray(original.length + 1)
        for (i in original.indices) {
            originalToTransformed[i] = transformedBuilder.length
            when (original[i]) {
                '\n' -> transformedBuilder.append("$NEWLINE_SYMBOL\n")
                '\r' -> transformedBuilder.append("␍")
                '\t' -> transformedBuilder.append(TAB_SYMBOL)
                else -> transformedBuilder.append(original[i])
            }
        }
        originalToTransformed[original.length] = transformedBuilder.length
        val transformed = transformedBuilder.toString()

        // Build reverse map from transformed index to original index
        val transformedToOriginal = IntArray(transformed.length + 1)
        for (i in original.indices) {
            val tStart = originalToTransformed[i]
            val tEnd = originalToTransformed[i + 1]
            for (t in tStart until tEnd) {
                transformedToOriginal[t] = i
            }
        }
        transformedToOriginal[transformed.length] = original.length

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = originalToTransformed[offset.coerceIn(0, original.length)]

            override fun transformedToOriginal(offset: Int): Int = transformedToOriginal[offset.coerceIn(0, transformed.length)]
        }

        return TransformedText(AnnotatedString(transformed), offsetMapping)
    }
}
