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

package org.connectbot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SHA1_MAX_BYTES = 20
private const val MILLIS_BETWEEN_INPUTS = 50L

/**
 * A composable that gathers random entropy from user touch/drag gestures.
 * The entropy is collected by tracking user input coordinates and converting them
 * into random bits to seed a random number generator for key generation.
 */
@Composable
fun EntropyGatherer(
    onGatherEntropy: (ByteArray) -> Unit,
    onProgressUpdate: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val entropyGenerator = remember { DragEntropyGenerator() }
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .border(width = 2.dp, color = borderColor)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val x = change.position.x
                    val y = change.position.y
                    val now = System.currentTimeMillis()
                    val wasAdded = entropyGenerator.addDragInput(x, y, now, MILLIS_BETWEEN_INPUTS)

                    if (wasAdded) {
                        onProgressUpdate(entropyGenerator.getProgress())
                    }

                    if (entropyGenerator.isEntropyComplete()) {
                        val finalEntropy = entropyGenerator.getEntropy()
                        onGatherEntropy(finalEntropy)
                    }
                }
            }
    ) {
        // Draw background to indicate touchable area
        drawRect(
            color = backgroundColor,
            style = Fill
        )

        // Draw emojis to indicate touch/mouse interaction
        val emojiText = "👆 🖱️"
        val textLayoutResult = textMeasurer.measure(
            text = emojiText,
            style = TextStyle(
                fontSize = 48.sp,
                color = textColor
            )
        )

        val centerX = size.width / 2f
        val centerY = size.height / 2f

        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(
                x = centerX - textLayoutResult.size.width / 2f,
                y = centerY - textLayoutResult.size.height / 2f
            )
        )
    }
}

/**
 * Inputs entropy from a user's X, Y inputs. These should be fairly random, so it should
 * contribute somewhat to entropy. As a type of whitening, we only accept bit patterns of 01 and 10
 * and discard 00 and 11.
 */
class DragEntropyGenerator(private val entropyCapacity: Int = SHA1_MAX_BYTES) {
    private val entropy = ByteArray(entropyCapacity)
    private var entropyByteIndex = 0
    private var entropyBitIndex = 0
    private var lastX: Float = -1f
    private var lastY: Float = -1f
    private var lastTime: Long = 0L
    private var flipFlop: Boolean = false

    fun addDragInput(x: Float, y: Float, currentTime: Long, minTimeDeltaMillis: Long): Boolean {
        if (entropyByteIndex >= entropyCapacity) return false // Entropy already full

        // Ignore if position hasn't changed
        if (lastX == x && lastY == y) return false

        // Only get entropy every MIN_TIME_DELTA_MILLIS to ensure the user has moved around
        if (currentTime - lastTime < minTimeDeltaMillis) return false
        lastTime = currentTime

        lastX = x
        lastY = y

        // Get the lowest 4 bits of each X, Y input and concat to the entropy-gathering string
        val input = if (flipFlop) {
            ((x.toInt() and 0x0F) shl 4) or (y.toInt() and 0x0F)
        } else {
            ((y.toInt() and 0x0F) shl 4) or (x.toInt() and 0x0F)
        }
        flipFlop = !flipFlop

        var inputByte = input
        for (i in 0 until 4) {
            if (entropyByteIndex >= entropyCapacity) break

            val bitsToAdd = inputByte and 0x3
            when (bitsToAdd) {
                0x1 -> {
                    // Shift left and set bit to 1
                    entropy[entropyByteIndex] = ((entropy[entropyByteIndex].toInt() shl 1) or 1).toByte()
                    entropyBitIndex++
                }

                0x2 -> {
                    // Shift left and set bit to 0
                    entropy[entropyByteIndex] = (entropy[entropyByteIndex].toInt() shl 1).toByte()
                    entropyBitIndex++
                }
                // Ignore 0x0 and 0x3 as they don't contribute distinct bits in this scheme
                // as a kind of whitening to the entropy.
            }
            inputByte = inputByte shr 2 // Shift input by 2 bits to process next pair

            if (entropyBitIndex >= 8) {
                entropyBitIndex = 0
                entropyByteIndex++
            }
        }
        return true
    }

    fun getProgress(): Int = (entropyByteIndex * 100 / SHA1_MAX_BYTES) + (entropyBitIndex * 5 / 8)

    fun getEntropy(): ByteArray = entropy.copyOfRange(0, entropyByteIndex)

    fun isEntropyComplete(): Boolean = entropyByteIndex >= entropyCapacity
}
