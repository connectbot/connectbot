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

package org.connectbot.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer

class RelayTest {

    @Test
    fun advanceAfterRead_validRead_advancesPosition() {
        val buffer = ByteBuffer.allocate(8)
        buffer.position(3)

        val endOfInput = buffer.advanceAfterRead(bytesRead = 2, requestedLength = 5)

        assertFalse(endOfInput)
        assertEquals(5, buffer.position())
    }

    @Test
    fun advanceAfterRead_remoteEof_reportsEndOfInput() {
        val buffer = ByteBuffer.allocate(8)
        buffer.position(3)

        val endOfInput = buffer.advanceAfterRead(bytesRead = -1, requestedLength = 5)

        assertTrue(endOfInput)
        assertEquals(3, buffer.position())
    }

    @Test
    fun advanceAfterRead_tooManyBytesRead_reportsIoFailure() {
        val buffer = ByteBuffer.allocate(8)
        buffer.position(3)

        assertThrows(IOException::class.java) {
            buffer.advanceAfterRead(bytesRead = 6, requestedLength = 5)
        }
        assertEquals(3, buffer.position())
    }

    @Test
    fun advanceAfterRead_invalidNegativeCount_reportsIoFailure() {
        val buffer = ByteBuffer.allocate(8)
        buffer.position(3)

        assertThrows(IOException::class.java) {
            buffer.advanceAfterRead(bytesRead = -2, requestedLength = 5)
        }
        assertEquals(3, buffer.position())
    }
}
