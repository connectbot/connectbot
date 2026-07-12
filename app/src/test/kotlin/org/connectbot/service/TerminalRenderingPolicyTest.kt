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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TerminalRenderingPolicyTest {
    @Test
    fun `e-ink mode throttles new terminal snapshots`() {
        assertThat(terminalUpdateIntervalMs(einkMode = false)).isZero()
        assertThat(terminalUpdateIntervalMs(einkMode = true))
            .isEqualTo(EINK_TERMINAL_UPDATE_INTERVAL_MS)
    }

    @Test
    fun `persisted terminal dimensions are normalized`() {
        assertThat(null.toPositiveTerminalDimension()).isNull()
        assertThat((-1).toPositiveTerminalDimension()).isEqualTo(1)
        assertThat(0.toPositiveTerminalDimension()).isEqualTo(1)
        assertThat(24.toPositiveTerminalDimension()).isEqualTo(24)
    }

    @Test
    fun `persisted scrollback limit is normalized`() {
        assertThat(terminalScrollbackLimit(-1)).isZero()
        assertThat(terminalScrollbackLimit(0)).isZero()
        assertThat(terminalScrollbackLimit(500)).isEqualTo(500)
    }
}
