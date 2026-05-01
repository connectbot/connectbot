/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root, Jeffrey Sharkey
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

package org.connectbot.fido2

import com.yubico.yubikit.fido.ctap.Ctap2Session

internal object Fido2SignatureMapper {
    fun fromAssertion(assertion: Ctap2Session.AssertionData): Fido2SignatureResult {
        val authData = assertion.authenticatorData
        val flags = if (authData.size >= FLAGS_OFFSET + 1) authData[FLAGS_OFFSET] else 0
        val counter = if (authData.size >= COUNTER_OFFSET + COUNTER_LENGTH) {
            ((authData[COUNTER_OFFSET].toInt() and 0xFF) shl 24) or
                ((authData[COUNTER_OFFSET + 1].toInt() and 0xFF) shl 16) or
                ((authData[COUNTER_OFFSET + 2].toInt() and 0xFF) shl 8) or
                (authData[COUNTER_OFFSET + 3].toInt() and 0xFF)
        } else {
            0
        }

        return Fido2SignatureResult(
            authenticatorData = authData,
            signature = assertion.signature,
            userPresenceVerified = (flags.toInt() and USER_PRESENT_FLAG) != 0,
            userVerified = (flags.toInt() and USER_VERIFIED_FLAG) != 0,
            counter = counter,
        )
    }

    private const val FLAGS_OFFSET = 32
    private const val COUNTER_OFFSET = 33
    private const val COUNTER_LENGTH = 4
    private const val USER_PRESENT_FLAG = 0x01
    private const val USER_VERIFIED_FLAG = 0x04
}
