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

package org.connectbot.service.tmux

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TmuxInputEncoderTest {
    @Test
    fun `keys encode as hex send-keys`() {
        assertThat(TmuxInputEncoder.toSendKeysCommands("%3", "ls\r".toByteArray()))
            .containsExactly("send-keys -t %3 -H 6c 73 0d")
    }

    @Test
    fun `escape and high bytes encode correctly`() {
        val bytes = byteArrayOf(0x1b, 0x5b, 0x41, 0xc3.toByte(), 0xa9.toByte())
        assertThat(TmuxInputEncoder.toSendKeysCommands("%0", bytes))
            .containsExactly("send-keys -t %0 -H 1b 5b 41 c3 a9")
    }

    @Test
    fun `large input chunks into multiple commands`() {
        val bytes = ByteArray(600) { 'a'.code.toByte() }
        val commands = TmuxInputEncoder.toSendKeysCommands("%1", bytes, maxBytesPerCommand = 256)
        assertThat(commands).hasSize(3)
        assertThat(commands[0].split(' ')).hasSize(4 + 256)
        assertThat(commands[2].split(' ')).hasSize(4 + 88)
    }

    @Test
    fun `empty input produces no commands`() {
        assertThat(TmuxInputEncoder.toSendKeysCommands("%1", ByteArray(0))).isEmpty()
        assertThat(TmuxInputEncoder.toPasteCommands("%1", "")).isEmpty()
    }

    @Test
    fun `paste builds buffer then pastes with bracketed flag`() {
        val commands = TmuxInputEncoder.toPasteCommands("%2", "hello world")
        assertThat(commands).containsExactly(
            "set-buffer -b connectbot-paste -- \"hello world\"",
            "paste-buffer -p -d -b connectbot-paste -t %2",
        )
    }

    @Test
    fun `long paste appends chunks`() {
        val text = "x".repeat(2500)
        val commands = TmuxInputEncoder.toPasteCommands("%2", text, chunkChars = 1024)
        assertThat(commands).hasSize(4)
        assertThat(commands[0]).startsWith("set-buffer -b connectbot-paste")
        assertThat(commands[1]).startsWith("set-buffer -ab connectbot-paste")
        assertThat(commands[2]).startsWith("set-buffer -ab connectbot-paste")
        assertThat(commands[3]).startsWith("paste-buffer")
    }

    @Test
    fun `quoting escapes tmux specials and control bytes`() {
        // Verified against tmux 3.7b: \ooo decodes inside double quotes.
        assertThat(TmuxInputEncoder.quoteDouble("a\"b\\c\$d`e"))
            .isEqualTo("a\\\"b\\\\c\\\$d\\`e")
        assertThat(TmuxInputEncoder.quoteDouble("line1\nline2\ttab"))
            .isEqualTo("line1\\012line2\\011tab")
        assertThat(TmuxInputEncoder.quoteDouble("del\u007f"))
            .isEqualTo("del\\177")
        assertThat(TmuxInputEncoder.quoteDouble("café 🚀"))
            .isEqualTo("café 🚀")
    }

    @Test
    fun `paste with newlines survives command-line framing`() {
        val commands = TmuxInputEncoder.toPasteCommands("%0", "line1\nline2\n")
        assertThat(commands[0]).doesNotContain("\n")
        assertThat(commands[0]).contains("line1\\012line2\\012")
    }
}
