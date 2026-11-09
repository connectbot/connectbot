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

package org.connectbot.ctap2

import java.io.Closeable

/** A single blocking CTAP2 session. Serialize calls and use a worker thread. */
interface Ctap2Client : Closeable {
    fun enumerateCredentials(pin: String, rpFilter: (String) -> Boolean = { true }, retryWithPinProtocolV1: Boolean = false): Fido2Result<List<Fido2Credential>>
    fun getAssertion(pin: String, credentialId: ByteArray, rpId: String, clientDataHash: ByteArray): Fido2Result<Fido2SignatureResult>
}
