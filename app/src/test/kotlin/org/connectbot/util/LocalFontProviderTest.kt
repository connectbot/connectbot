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

import android.content.ContentResolver
import android.content.Context
import android.graphics.Typeface
import androidx.core.net.toUri
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
class LocalFontProviderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val context = mock(Context::class.java)
    private val resolver = mock(ContentResolver::class.java)
    private val source = "content://fonts/example".toUri()

    private fun provider(): LocalFontProvider {
        `when`(context.filesDir).thenReturn(temp.root)
        `when`(context.contentResolver).thenReturn(resolver)
        return LocalFontProvider(context)
    }

    @Test
    fun importsAndCachesTheDecodedFont() {
        val provider = provider()
        val font = File(temp.root, "fonts/My_Font.ttf")
        val typeface = Typeface.MONOSPACE
        val bytes = byteArrayOf(1, 2, 3)
        `when`(resolver.openInputStream(source)).thenReturn(bytes.inputStream())
        mockStatic(Typeface::class.java).use { typefaces ->
            typefaces.`when`<Typeface> { Typeface.createFromFile(font) }.thenReturn(typeface)
            assertEquals("My_Font.ttf", provider.importFont(source, "My/Font"))
            assertArrayEquals(bytes, font.readBytes())
            assertSame(typeface, provider.loadFont("My_Font.ttf"))
            typefaces.verify { Typeface.createFromFile(font) }
        }
    }

    @Test
    fun removesAnImportRejectedByTheFontDecoder() {
        val provider = provider()
        val font = File(temp.root, "fonts/rejected.ttf")
        `when`(resolver.openInputStream(source)).thenReturn(byteArrayOf(1).inputStream())
        mockStatic(Typeface::class.java).use { typefaces ->
            typefaces.`when`<Typeface> { Typeface.createFromFile(font) }.thenReturn(null)
            assertNull(provider.importFont(source, "rejected.ttf"))
            assertFalse(font.exists())
        }
    }

    @Test
    fun missingFilesAndReadFailuresUseTheFallback() {
        val provider = provider()
        val invalidFont = File(temp.root, "fonts/invalid.ttf").apply { writeText("invalid font") }
        val fallback = Typeface.DEFAULT
        `when`(resolver.openInputStream(source)).thenThrow(FileNotFoundException())
        assertNull(provider.importFont(source, "missing.ttf"))
        assertSame(fallback, provider.getTypeface("missing.ttf", fallback))
        mockStatic(Typeface::class.java).use { typefaces ->
            typefaces.`when`<Typeface> { Typeface.createFromFile(invalidFont) }.thenThrow(RuntimeException("Invalid font"))
            assertSame(fallback, provider.getTypeface(invalidFont.name, fallback))
        }
    }
}
