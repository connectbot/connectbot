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

package org.connectbot.piv

import android.app.Activity
import androidx.annotation.MainThread
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.piv.KeyType
import com.yubico.yubikit.piv.PinPolicy
import com.yubico.yubikit.piv.PivSession
import com.yubico.yubikit.piv.Slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.sshlib.AuthPublicKey
import org.connectbot.sshlib.SshSigning
import org.connectbot.util.PubkeyConstants
import java.io.IOException
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.atomic.AtomicBoolean

internal object YubiKeyPiv {
    private const val DEVICE_TIMEOUT_MS = 60_000L
    private val discoveryMutex = Mutex()

    fun storedType(slot: Slot): String = PubkeyConstants.KEY_TYPE_YUBIKEY_PIV_PREFIX + slot.stringAlias

    fun slotFromStoredType(type: String): Slot = Slot.fromStringAlias(
        type.removePrefix(PubkeyConstants.KEY_TYPE_YUBIKEY_PIV_PREFIX),
    ).also { require(it != Slot.ATTESTATION) }

    fun encodePublicKey(publicKey: PublicKey): AuthPublicKey =
        SshSigning.encodePublicKey(KeyPair(publicKey, null)).also {
            require(it.algorithmName == PivSshSignature.ALGORITHM)
        }

    @MainThread
    suspend fun readPublicKey(activity: Activity, slot: Slot): PublicKey =
        withSession(activity) { session, _ -> readSlotKey(session, slot).publicKey }

    @MainThread
    suspend fun sign(
        activity: Activity,
        slot: Slot,
        expectedKey: PublicKey,
        pin: CharArray,
        data: ByteArray,
    ): ByteArray = withSession(activity) { session, checkActive ->
        val slotKey = readSlotKey(session, slot)
        if (encodePublicKey(expectedKey) != encodePublicKey(slotKey.publicKey)) throw WrongKey()

        if (slotKey.pinPolicy != PinPolicy.NEVER) {
            // The UI can clear its buffer on cancellation while a card operation is in flight.
            val cardPin = synchronized(pin) {
                checkActive()
                pin.copyOf()
            }
            try {
                if (cardPin.isEmpty()) throw PinRequired()
                session.verifyPin(cardPin)
            } finally {
                cardPin.fill('\u0000')
            }
        }

        checkActive()
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val der = session.rawSignOrDecrypt(slot, KeyType.ECCP256, digest)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(expectedKey)
        verifier.update(data)
        if (!verifier.verify(der)) throw WrongKey()
        PivSshSignature.encode(der)
    }

    private data class SlotKey(val publicKey: PublicKey, val pinPolicy: PinPolicy)

    private fun readSlotKey(session: PivSession, slot: Slot): SlotKey {
        if (!session.supports(PivSession.FEATURE_METADATA)) throw UnsupportedKey()
        val metadata = session.getSlotMetadata(slot)
        if (metadata.keyType != KeyType.ECCP256) throw UnsupportedKey()
        return SlotKey(metadata.publicKeyValues.toPublicKey(), metadata.pinPolicy)
    }

    // The SDK handles Android USB permission and owns the connection around its worker callback.
    // Keep reader mode and USB discovery scoped to one visible prompt and one card operation.
    @MainThread
    private suspend fun <T> withSession(
        activity: Activity,
        operation: (PivSession, () -> Unit) -> T,
    ): T = withTimeoutOrNull(DEVICE_TIMEOUT_MS) {
        discoveryMutex.withLock {
            val context = currentCoroutineContext()
            val manager = YubiKitManager(activity)
            val result = CompletableDeferred<T>()
            val claimed = AtomicBoolean()
            val connectionType = SmartCardConnection::class.java
            val accept: (YubiKeyDevice) -> Unit = accept@{ device ->
                if (!result.isActive || !device.supportsConnection(connectionType) || !claimed.compareAndSet(false, true)) {
                    return@accept
                }
                try {
                    device.requestConnection(connectionType) { connection ->
                        try {
                            context.ensureActive()
                            result.complete(operation(PivSession(connection.value)) { context.ensureActive() })
                        } catch (e: Exception) {
                            result.completeExceptionally(e)
                        }
                    }
                } catch (e: Exception) {
                    result.completeExceptionally(e)
                }
            }
            var nfcStarted = false
            try {
                try {
                    manager.startNfcDiscovery(NfcConfiguration().timeout(15_000), activity) { accept(it) }
                    nfcStarted = true
                } catch (_: NfcNotAvailable) {
                    // USB can be used even when NFC is disabled or unavailable.
                }
                manager.startUsbDiscovery(UsbConfiguration().handlePermissions(true)) { accept(it) }
                result.await()
            } finally {
                result.cancel()
                try {
                    manager.stopUsbDiscovery()
                } finally {
                    if (nfcStarted) manager.stopNfcDiscovery(activity)
                }
            }
        }
    } ?: throw DeviceTimeout()

    class DeviceTimeout : IOException()
    class PinRequired : IOException()
    class UnsupportedKey : IOException()
    class WrongKey : IOException()
}
