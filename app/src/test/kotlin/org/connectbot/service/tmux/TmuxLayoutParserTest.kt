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

class TmuxLayoutParserTest {
    @Test
    fun `single pane layout`() {
        assertThat(TmuxLayoutParser.parse("a87d,100x30,0,0,0"))
            .containsExactly(TmuxLayoutPane("%0", 100, 30, 0, 0))
    }

    @Test
    fun `vertical split layout from recorded transcript`() {
        assertThat(TmuxLayoutParser.parse("c08a,100x30,0,0[100x15,0,0,0,100x14,0,16,1]"))
            .containsExactly(
                TmuxLayoutPane("%0", 100, 15, 0, 0),
                TmuxLayoutPane("%1", 100, 14, 0, 16),
            )
    }

    @Test
    fun `nested horizontal and vertical splits`() {
        val layout = "b25d,238x54,0,0{118x54,0,0,1,119x54,119,0[119x27,119,0,2,119x26,119,28,3]}"
        assertThat(TmuxLayoutParser.parse(layout)).containsExactly(
            TmuxLayoutPane("%1", 118, 54, 0, 0),
            TmuxLayoutPane("%2", 119, 27, 119, 0),
            TmuxLayoutPane("%3", 119, 26, 119, 28),
        )
    }

    @Test
    fun `garbage returns empty`() {
        assertThat(TmuxLayoutParser.parse("")).isEmpty()
        assertThat(TmuxLayoutParser.parse("nonsense")).isEmpty()
        assertThat(TmuxLayoutParser.parse("abcd,100x30")).isEmpty()
        assertThat(TmuxLayoutParser.parse("abcd,100x30,0,0{100x30,0,0,1")).isEmpty()
    }
}
