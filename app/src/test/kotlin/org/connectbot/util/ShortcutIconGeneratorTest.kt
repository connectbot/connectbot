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

import android.content.Context
import androidx.core.graphics.toColorInt
import androidx.test.core.app.ApplicationProvider
import org.connectbot.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortcutIconGeneratorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun shortcutUsesTheRequestedBackgroundColor() {
        val color = "#FF124578"
        val icon = ShortcutIconGenerator.generateShortcutIcon(context, color, IconStyle.CLOUD)
        val bitmap = shadowOf(icon.toIcon(context)).bitmap

        assertEquals(color.toColorInt(), bitmap.getPixel(22, 54))
    }

    @Test
    fun previewUsesTheRequestedBackgroundColor() {
        val color = "#FF124578"
        val preview = ShortcutIconGenerator.generatePreviewBitmap(context, color, IconStyle.CLOUD, 100)

        assertEquals(color.toColorInt(), preview.getPixel(5, 50))
    }

    @Test
    fun previewUsesTheDefaultColorWhenTheInputIsInvalid() {
        val preview = ShortcutIconGenerator.generatePreviewBitmap(context, "invalid", IconStyle.CLOUD, 100)

        assertEquals(context.getColor(R.color.host_blue), preview.getPixel(5, 50))
    }
}
