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

import com.yubico.yubikit.core.fido.FidoConnection
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.fido.ctap.Ctap2Session

/** YubiKit adapter. Closing the client also closes the supplied connection. */
class YubiKitCtap2Client private constructor(private val session: Ctap2Session) : Ctap2Client {
    override fun enumerateCredentials(pin: String, rpFilter: (String) -> Boolean, retryWithPinProtocolV1: Boolean): Fido2Result<List<Fido2Credential>> {
        val operations = Ctap2Operations(rpFilter)
        val protocol = operations.selectPinProtocol(session)
        return try {
            operations.enumerateCredentialsWithPin(session, protocol, pin)
        } catch (e: Exception) {
            if (retryWithPinProtocolV1 && operations.shouldRetryPinBlockedWithV1(e, protocol)) {
                try {
                    operations.enumerateCredentialsWithPin(session, com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV1(), pin)
                } catch (fallback: Exception) {
                    operations.mapPinOrCredentialError(fallback, true, session, com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV1())
                }
            } else {
                operations.mapPinOrCredentialError(e, session = session, protocol = protocol)
            }
        }
    }

    override fun getAssertion(pin: String, credentialId: ByteArray, rpId: String, clientDataHash: ByteArray): Fido2Result<Fido2SignatureResult> = Ctap2Operations().getAssertion(session, pin, credentialId, clientDataHash, rpId)

    override fun close() {
        try {
            session.close()
        } catch (_: java.io.IOException) {
            // Device removal can make cleanup fail after the operation completed.
        }
    }

    companion object {
        fun open(connection: FidoConnection): Ctap2Client = openOwned(connection) { Ctap2Session(connection) }
        fun open(connection: SmartCardConnection): Ctap2Client = openOwned(connection) { Ctap2Session(connection) }

        private fun openOwned(connection: java.io.Closeable, createSession: () -> Ctap2Session): Ctap2Client = try {
            YubiKitCtap2Client(createSession())
        } catch (error: Exception) {
            try {
                connection.close()
            } catch (closeError: Exception) {
                error.addSuppressed(closeError)
            }
            throw error
        }
    }
}
