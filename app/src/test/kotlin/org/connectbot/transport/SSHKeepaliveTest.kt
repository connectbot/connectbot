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

import com.trilead.ssh2.Connection
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIOException
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SSHKeepaliveTest {

    @Test
    fun supportsKeepalive_isTrueForSsh() {
        assertThat(SSH().supportsKeepalive()).isTrue()
    }

    @Test
    fun supportsKeepalive_isFalseByDefault() {
        assertThat(Local().supportsKeepalive()).isFalse()
        assertThat(Telnet().supportsKeepalive()).isFalse()
    }

    @Test
    fun sendKeepalive_pingsConnection() {
        val connection = mock<Connection>()
        val ssh = SSH()
        ssh.setConnectionForTesting(connection)

        ssh.sendKeepalive()

        verify(connection).ping()
    }

    @Test
    fun sendKeepalive_throwsWhenNotConnected() {
        assertThatIOException().isThrownBy { SSH().sendKeepalive() }
    }
}
