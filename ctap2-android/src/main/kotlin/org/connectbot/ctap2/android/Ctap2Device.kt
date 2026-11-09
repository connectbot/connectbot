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

package org.connectbot.ctap2.android

import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.fido.FidoConnection
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import kotlinx.coroutines.suspendCancellableCoroutine
import org.connectbot.ctap2.Ctap2Client
import org.connectbot.ctap2.YubiKitCtap2Client
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Opens CTAP2 sessions on an Android-discovered device. Use through [Ctap2DeviceWorker]. */
class Ctap2Device internal constructor(private val device: YubiKeyDevice) {
    /** The caller owns the USB session, allowing reuse after PIN entry. */
    suspend fun openUsbSession(): Ctap2Client = suspendCancellableCoroutine { continuation ->
        try {
            device.requestConnection(FidoConnection::class.java) { result ->
                try {
                    val client = YubiKitCtap2Client.open(result.value)
                    continuation.resume(client) { _, value, _ -> value.close() }
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    /** Executes and closes the transient NFC session before its SDK callback returns. */
    suspend fun <T> withNfcSession(operation: (Ctap2Client) -> T): T = suspendCancellableCoroutine { continuation ->
        try {
            device.requestConnection(SmartCardConnection::class.java) { result ->
                try {
                    val value = YubiKitCtap2Client.open(result.value).use(operation)
                    continuation.resume(value)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    /** Reuses the PIN-entry session, reopening once for known stale USB transport errors. */
    suspend fun enumerateUsbCredentials(
        session: Ctap2Client,
        pin: String,
        rpFilter: (String) -> Boolean,
    ): org.connectbot.ctap2.Fido2Result<List<org.connectbot.ctap2.Fido2Credential>> {
        val result = session.enumerateCredentials(pin, rpFilter)
        val failure = (result as? org.connectbot.ctap2.Fido2Result.Error)?.cause
        val message = failure?.message.orEmpty()
        val stale = listOf("Failed to send full packet", "broken pipe", "connection reset")
            .any { message.contains(it, ignoreCase = true) }
        if (!stale) return result
        return try {
            openUsbSession().use { it.enumerateCredentials(pin, rpFilter, retryWithPinProtocolV1 = true) }
        } catch (error: Exception) {
            org.connectbot.ctap2.Ctap2Errors.fromException(error, "USB operation failed")
        }
    }

    val nfcTag: android.nfc.Tag?
        get() = (device as? com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice)?.tag

    companion object {
        fun fromNfcTag(tag: android.nfc.Tag, executor: java.util.concurrent.ExecutorService): Ctap2Device = Ctap2Device(com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice(tag, 30000, executor))
    }
}
