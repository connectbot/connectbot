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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import org.connectbot.R

/**
 * Icon styles available for shortcut customization.
 */
enum class IconStyle(@DrawableRes val drawableRes: Int) {
    TERMINAL(R.drawable.ic_shortcut_foreground),
    SERVER(R.drawable.ic_shortcut_server),
    CLOUD(R.drawable.ic_shortcut_cloud),
    KEY(R.drawable.ic_shortcut_key)
}

/**
 * Generates shortcut icons with customizable colors and styles.
 */
object ShortcutIconGenerator {
    private const val ADAPTIVE_ICON_SIZE = 108
    private const val INNER_CIRCLE_SIZE = 72
    private const val CIRCLE_OFFSET = (ADAPTIVE_ICON_SIZE - INNER_CIRCLE_SIZE) / 2f

    /**
     * Generate a shortcut icon with the specified background color and icon style.
     *
     * @param context The context used to access resources
     * @param backgroundColor The background color as a hex string (e.g., "#2196F3") or null for default
     * @param style The icon style to use
     * @return An IconCompat suitable for use in shortcut creation
     */
    fun generateShortcutIcon(
        context: Context,
        backgroundColor: String?,
        style: IconStyle
    ): IconCompat {
        val bitmap = Bitmap.createBitmap(
            ADAPTIVE_ICON_SIZE,
            ADAPTIVE_ICON_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)

        val bgColor = try {
            if (backgroundColor != null) {
                Color.parseColor(backgroundColor)
            } else {
                ContextCompat.getColor(context, R.color.host_blue)
            }
        } catch (e: IllegalArgumentException) {
            ContextCompat.getColor(context, R.color.host_blue)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
        }
        canvas.drawCircle(
            ADAPTIVE_ICON_SIZE / 2f,
            ADAPTIVE_ICON_SIZE / 2f,
            INNER_CIRCLE_SIZE / 2f,
            paint
        )

        val drawable = ContextCompat.getDrawable(context, style.drawableRes)
        drawable?.let {
            it.setBounds(0, 0, ADAPTIVE_ICON_SIZE, ADAPTIVE_ICON_SIZE)
            it.draw(canvas)
        }

        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }

    /**
     * Generate a preview bitmap for the customization dialog.
     *
     * @param context The context used to access resources
     * @param backgroundColor The background color as a hex string (e.g., "#2196F3") or null for default
     * @param style The icon style to use
     * @param sizePx The desired size in pixels
     * @return A Bitmap suitable for preview display
     */
    fun generatePreviewBitmap(
        context: Context,
        backgroundColor: String?,
        style: IconStyle,
        sizePx: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgColor = try {
            if (backgroundColor != null) {
                Color.parseColor(backgroundColor)
            } else {
                ContextCompat.getColor(context, R.color.host_blue)
            }
        } catch (e: IllegalArgumentException) {
            ContextCompat.getColor(context, R.color.host_blue)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        val drawable = ContextCompat.getDrawable(context, style.drawableRes)
        drawable?.let {
            it.setBounds(0, 0, sizePx, sizePx)
            it.draw(canvas)
        }

        return bitmap
    }
}
