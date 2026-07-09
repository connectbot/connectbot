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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException

/**
 * Classification of connect-time network failures so an airplane-mode
 * attempt is reported as "network unreachable" rather than a generic
 * connection interruption.
 * https://github.com/connectbot/connectbot/issues/386
 */
class SSHNetworkUnreachableTest {
    @Test
    fun noRouteToHost_isUnreachable() {
        assertThat(SSH.isNetworkUnreachable(NoRouteToHostException("No route to host"))).isTrue()
    }

    @Test
    fun connectExceptionNetworkUnreachable_isUnreachable() {
        // What Android throws for ENETUNREACH, e.g. connecting by IP in airplane mode
        assertThat(SSH.isNetworkUnreachable(ConnectException("Network is unreachable"))).isTrue()
    }

    @Test
    fun socketExceptionHostUnreachable_isUnreachable() {
        assertThat(SSH.isNetworkUnreachable(SocketException("EHOSTUNREACH (Host is unreachable)"))).isTrue()
    }

    @Test
    fun connectionRefused_isNotUnreachable() {
        assertThat(SSH.isNetworkUnreachable(ConnectException("Connection refused"))).isFalse()
    }

    @Test
    fun genericIoException_isNotUnreachable() {
        assertThat(SSH.isNetworkUnreachable(IOException("Network is unreachable"))).isFalse()
    }

    @Test
    fun socketExceptionWithoutMessage_isNotUnreachable() {
        assertThat(SSH.isNetworkUnreachable(SocketException())).isFalse()
    }
}
