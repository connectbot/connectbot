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

package org.connectbot.service

import android.text.AndroidCharacter
import android.util.Log
import de.mud.terminal.vt320
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.harmony.niochar.charset.additional.IBM437
import org.connectbot.transport.AbsTransport
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

/**
 * Coroutine-based relay that handles incoming data from the transport to the terminal buffer.
 * Handles charset decoding and East Asian character width calculations.
 *
 * @author Kenny Root
 */
class Relay(
    private val bridge: TerminalBridge,
    private val transport: AbsTransport,
    private val buffer: vt320,
    encoding: String
) {

    private var currentCharset: Charset? = null
    private var decoder: CharsetDecoder? = null

    private lateinit var byteBuffer: ByteBuffer
    private lateinit var charBuffer: CharBuffer
    private lateinit var byteArray: ByteArray
    private lateinit var charArray: CharArray

    init {
        setCharset(encoding)
    }

    /**
     * Set the character set for decoding incoming data.
     *
     * @param encoding the character set name (e.g., "UTF-8", "CP437")
     */
    fun setCharset(encoding: String) {
        Log.d("ConnectBot.Relay", "changing charset to $encoding")

        val charset = if (encoding == "CP437") {
            IBM437("IBM437", arrayOf("IBM437", "CP437"))
        } else {
            Charset.forName(encoding)
        }

        if (charset == currentCharset) {
            return
        }

        val newCd = charset.newDecoder().apply {
            onUnmappableCharacter(CodingErrorAction.REPLACE)
            onMalformedInput(CodingErrorAction.REPLACE)
        }

        currentCharset = charset
        synchronized(this) {
            decoder = newCd
        }
    }

    /**
     * Get the current character set.
     *
     * @return the current Charset
     */
    fun getCharset(): Charset? = currentCharset

    /**
     * Start relaying data from transport to terminal buffer.
     * This is a suspend function that runs on IO dispatcher.
     */
    suspend fun start() = withContext(Dispatchers.IO) {
        byteBuffer = ByteBuffer.allocate(BUFFER_SIZE)
        charBuffer = CharBuffer.allocate(BUFFER_SIZE)

        // for East Asian character widths
        val wideAttribute = ByteArray(BUFFER_SIZE)

        byteArray = byteBuffer.array()
        charArray = charBuffer.array()

        var bytesRead: Int
        byteBuffer.limit(0)

        try {
            while (isActive) {
                val bytesToRead = byteBuffer.capacity() - byteBuffer.limit()
                val offset = byteBuffer.arrayOffset() + byteBuffer.limit()
                bytesRead = transport.read(byteArray, offset, bytesToRead)

                if (bytesRead > 0) {
                    byteBuffer.limit(byteBuffer.limit() + bytesRead)

                    val result = synchronized(this@Relay) {
                        decoder?.decode(byteBuffer, charBuffer, false)
                    }

                    if (result?.isUnderflow == true &&
                        byteBuffer.limit() == byteBuffer.capacity()
                    ) {
                        byteBuffer.compact()
                        byteBuffer.limit(byteBuffer.position())
                        byteBuffer.position(0)
                    }

                    val charOffset = charBuffer.position()

                    AndroidCharacter.getEastAsianWidths(charArray, 0, charOffset, wideAttribute)
                    buffer.putString(charArray, wideAttribute, 0, charBuffer.position())
                    bridge.propagateConsoleText(charArray, charBuffer.position())
                    charBuffer.clear()
                    bridge.redraw()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Problem while handling incoming data in relay", e)
        }
    }

    companion object {
        private const val TAG = "CB.Relay"
        private const val BUFFER_SIZE = 4096
    }
}
