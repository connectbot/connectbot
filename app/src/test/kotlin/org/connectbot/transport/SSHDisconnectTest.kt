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

package org.connectbot.transport

import com.trilead.ssh2.Session
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.service.DisconnectReason
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SSHDisconnectTest {
    @Test
    fun getDisconnectReasonForClosedSession_withExitStatus_reportsSessionExit() {
        val session = mock(Session::class.java)
        `when`(session.exitStatus).thenReturn(0)

        val reason = SSH().getDisconnectReasonForClosedSession(session)

        assertThat(reason).isEqualTo(DisconnectReason.SESSION_EXIT)
    }

    @Test
    fun getDisconnectReasonForClosedSession_withoutExitStatus_reportsRemoteEof() {
        val session = mock(Session::class.java)
        `when`(session.exitStatus).thenReturn(null)

        val reason = SSH().getDisconnectReasonForClosedSession(session)

        assertThat(reason).isEqualTo(DisconnectReason.REMOTE_EOF)
    }
}
