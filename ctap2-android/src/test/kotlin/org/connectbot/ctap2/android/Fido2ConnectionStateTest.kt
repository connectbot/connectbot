/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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

package org.connectbot.ctap2.android
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
class Fido2ConnectionStateTest {
    @Test
    fun `Fido2ConnectionState Connected contains transport info`() {
        val state = Fido2ConnectionState.Connected(
            transport = "USB",
            deviceName = "YubiKey 5",
        )

        assertThat(state.transport).isEqualTo("USB")
        assertThat(state.deviceName).isEqualTo("YubiKey 5")
    }

    @Test
    fun `Fido2ConnectionState Error contains message`() {
        val state = Fido2ConnectionState.Error("Connection failed")

        assertThat(state.message).isEqualTo("Connection failed")
    }
}
