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

package org.connectbot.data.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class KeyboardFitItemsTest {
    @Test
    fun fittedLayoutKeepsKeyIdentityAndSeparatesPinnedKeys() {
        val layoutId = UUID.randomUUID()
        val first = KeyboardItem(layoutId = layoutId, column = 0, columnSpan = 2)
        val wide = KeyboardItem(layoutId = layoutId, column = 2, columnSpan = 4)
        val short = KeyboardItem(layoutId = layoutId, row = 1, column = 0)
        val pinned = KeyboardItem(layoutId = layoutId, column = 0, pinned = true)
        val nextPinned = pinned.copy(id = UUID.randomUUID(), column = 3)
        val scenarios = listOf(
            emptyList<KeyboardItem>() to emptyList<Int>(),
            listOf(pinned, nextPinned) to listOf(0, 3),
            listOf(first, wide, short) to listOf(0, 2, 0),
            listOf(first, wide, short, pinned, nextPinned) to listOf(0, 2, 0, 6, 9),
        )
        for ((items, columns) in scenarios) {
            val fitted = fittedKeyboardItems(items)
            assertEquals(columns, fitted.map { it.column })
            assertEquals(items.map { it.id }, fitted.map { it.id })
            assertEquals(items.map { it.columnSpan }, fitted.map { it.columnSpan })
            assertEquals(items.map { it.row }, fitted.map { it.row })
            assertFalse(fitted.any { it.pinned })
        }
        assertTrue(pinned.pinned)
        assertEquals(0, pinned.column)
    }
}
