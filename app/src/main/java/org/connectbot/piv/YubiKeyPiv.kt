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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.nfc.NfcAdapter
import android.util.Base64
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.preference.PreferenceManager
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.android.transport.usb.UsbYubiKeyDevice
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.smartcard.ApduException
import com.yubico.yubikit.core.smartcard.SW
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.piv.KeyType
import com.yubico.yubikit.piv.PinPolicy
import com.yubico.yubikit.piv.PivSession
import com.yubico.yubikit.piv.Slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.sshlib.AuthPublicKey
import org.connectbot.sshlib.SshSigning
import org.connectbot.util.PreferenceConstants
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

internal object YubiKeyPiv {
    private const val SIGNATURE_ALGORITHM = "ecdsa-sha2-nistp256"
    private const val DEVICE_TIMEOUT_MS = 60_000L

    // How long to keep NFC reader mode on for the key to be lifted from the phone after it was used.
    private const val NFC_REMOVAL_TIMEOUT_MS = 10_000L

    // A key with PIN policy ONCE only remembers the PIN until the card is powered down, and every new
    // USB connection powers it on again. So a USB connection is kept open after a signature, until the key
    // is unplugged or has not been used for the time set in the preferences (see PreferenceConstants.
    // PIV_SESSION_IDLE_SECONDS), to let the card enforce its own policy. This cannot work over NFC: that
    // connection ends when the key leaves the reader's field, so the PIN is asked for every time.
    private val DEFAULT_HOLD_IDLE_MS = PreferenceConstants.PIV_SESSION_IDLE_SECONDS_DEFAULT.toLong() * 1000

    /** How long to keep a USB connection open after use; zero or less means not at all. */
    fun preferredHoldIdleMs(context: Context): Long = PreferenceManager.getDefaultSharedPreferences(context)
        .getString(PreferenceConstants.PIV_SESSION_IDLE_SECONDS, null)
        ?.toLongOrNull()
        ?.let { it.coerceIn(0, 24 * 3600) * 1000 }
        ?: DEFAULT_HOLD_IDLE_MS

    fun slotId(slot: Slot): Int = slot.value.also { require(slot != Slot.ATTESTATION) }

    fun slotFromId(id: Int?): Slot = Slot.fromValue(
        requireNotNull(id) { "Missing PIV slot" },
    ).also { require(it != Slot.ATTESTATION) }

    fun encodePublicKey(publicKey: PublicKey): AuthPublicKey = SshSigning.encodePublicKey(KeyPair(publicKey, null)).also {
        require(it.algorithmName == SIGNATURE_ALGORITHM)
    }

    /** Short SHA-256 fingerprint in the form ssh-keygen prints, for telling keys in the same slot apart. */
    fun shortFingerprint(publicKey: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(SshSigning.encodePublicKeyBlob(publicKey))
        return "SHA256:" + Base64.encodeToString(digest, Base64.NO_PADDING or Base64.NO_WRAP).take(8)
    }

    private val client by lazy { Client() }

    @MainThread
    suspend fun readPublicKey(activity: Activity, slot: Slot): PublicKey = client.readPublicKey(activity, slot)

    @MainThread
    suspend fun sign(activity: Activity, slot: Slot, expectedKey: PublicKey, pin: CharArray, data: ByteArray): ByteArray = client.sign(activity, slot, expectedKey, pin, data)

    suspend fun canSignWithoutPin(slot: Slot): Boolean = client.canSignWithoutPin(slot)

    /**
     * Owns discovery and cached sessions. SDK construction and executors are supplied at this
     * boundary so callers can exercise real session behavior without hardware or global state.
     * Card operations must run on a single-thread executor.
     */
    internal class Client(
        private val cardExecutor: ExecutorService = Executors.newSingleThreadExecutor {
            Thread(it, "yubikey-piv").apply { isDaemon = true }
        },
        private val idleExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
            Thread(it, "yubikey-piv-idle").apply { isDaemon = true }
        },
        private val createManager: (Activity) -> YubiKitManager = ::YubiKitManager,
        private val createSession: (SmartCardConnection) -> PivSession = ::PivSession,
        private val deviceTimeoutMs: Long = DEVICE_TIMEOUT_MS,
        private val holdIdleMs: (Context) -> Long = ::preferredHoldIdleMs,
    ) {
        private val discoveryMutex = Mutex()

        // Card I/O blocks (touch can take seconds) and the held connection must only be used by one thread at a time.
        private val cardDispatcher = cardExecutor.asCoroutineDispatcher()
        private val heldLock = Any()
        private var held: HeldSession? = null // guarded by heldLock

        private inner class HeldSession(val connection: SmartCardConnection, val session: PivSession, val usbDeviceName: String) {
            var busy = false // guarded by heldLock
            var idleMs = 0L // guarded by heldLock
            var idleTask: ScheduledFuture<*>? = null // guarded by heldLock
            var onClose: (() -> Unit)? = null // guarded by heldLock

            fun close() {
                val cleanup = synchronized(heldLock) {
                    idleTask?.cancel(false)
                    onClose.also { onClose = null }
                }
                cleanup?.invoke()
                try {
                    connection.close()
                } catch (_: Exception) {
                    // Already gone, for instance because the key was unplugged.
                }
            }
        }

        @MainThread
        suspend fun readPublicKey(activity: Activity, slot: Slot): PublicKey = withSession(activity) { session, _, _ -> readSlotKey(session, slot).publicKey }

        @MainThread
        suspend fun sign(
            activity: Activity,
            slot: Slot,
            expectedKey: PublicKey,
            pin: CharArray,
            data: ByteArray,
        ): ByteArray = withSession(activity, retryRejectedHeldKey = true) { session, connection, checkActive ->
            val slotKey = locateKey(session, slot, expectedKey)

            verifyPin(session, connection, slotKey.pinPolicy, pin, checkActive)
            val pinUnverified = slotKey.pinPolicy != PinPolicy.NEVER && pin.isEmpty()

            checkActive()
            val digest = MessageDigest.getInstance("SHA-256").digest(data)
            val der = try {
                session.rawSignOrDecrypt(slotKey.slot, KeyType.ECCP256, digest)
            } catch (e: ApduException) {
                // We relied on the card remembering the PIN, and it does not (anymore).
                if (pinUnverified && e.sw == SW.SECURITY_CONDITION_NOT_SATISFIED) throw PinRequired()
                throw e
            }
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(expectedKey)
            verifier.update(data)
            if (!verifier.verify(der)) throw WrongKey()
            SshSigning.encodeEcdsaSignature(SIGNATURE_ALGORITHM, der)
        }

        /**
         * Finds [expected] on the card. The saved slot is only a hint: a key can end up in another slot, for
         * instance when it was imported into a second YubiKey or moved on newer firmware. The key is matched by
         * its public key, so searching the other slots can never select a different key.
         */
        private fun locateKey(session: PivSession, hint: Slot, expected: PublicKey): SlotKey {
            var unsupportedAtHint: UnsupportedKey? = null
            try {
                val atHint = readSlotKey(session, hint)
                if (sameKey(atHint.publicKey, expected)) return atHint
            } catch (_: EmptySlot) {
                // Look elsewhere.
            } catch (e: UnsupportedKey) {
                if (e.reason == UnsupportedKey.Reason.FIRMWARE) throw e
                unsupportedAtHint = e
            }

            for (candidate in Slot.values()) {
                if (candidate == hint || candidate == Slot.ATTESTATION) continue
                val found = try {
                    readSlotKey(session, candidate).takeIf { sameKey(it.publicKey, expected) }
                } catch (_: PivFailure) {
                    null // Empty, or a key type we cannot use.
                } catch (_: ApduException) {
                    null
                } catch (_: GeneralSecurityException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
                if (found != null) return found
            }
            // With nothing else matching, the unusable key in the saved slot is the more helpful thing to report.
            throw unsupportedAtHint ?: WrongKey()
        }

        private fun sameKey(a: PublicKey, b: PublicKey): Boolean = try {
            encodePublicKey(a) == encodePublicKey(b)
        } catch (_: Exception) {
            false
        }

        private fun verifyPin(session: PivSession, connection: SmartCardConnection, policy: PinPolicy, pin: CharArray, checkActive: () -> Unit) {
            if (policy != PinPolicy.NEVER) {
                // The UI can clear its buffer on cancellation while a card operation is in flight.
                val cardPin = synchronized(pin) {
                    checkActive()
                    pin.copyOf()
                }
                try {
                    if (cardPin.isNotEmpty()) {
                        session.verifyPin(cardPin)
                    } else if (!pinRemembered(connection, policy)) {
                        // Do not let the card ask for a touch first just to then find out that the PIN is missing.
                        throw PinRequired()
                    }
                } finally {
                    cardPin.fill('\u0000')
                }
            }
        }

        /**
         * Whether [slot] can sign right now without asking for the PIN: a USB connection is held, and the slot
         * needs no PIN or the card still remembers it from the last signature (PIN policy ONCE).
         */
        suspend fun canSignWithoutPin(slot: Slot): Boolean = try {
            useHeld { session, connection, _ ->
                val policy = readSlotKey(session, slot).pinPolicy
                policy == PinPolicy.NEVER || pinRemembered(connection, policy)
            } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }

        // Only the ONCE policy survives between signatures; ALWAYS has to be verified again for every one.
        private fun pinRemembered(connection: SmartCardConnection, policy: PinPolicy): Boolean = policy == PinPolicy.ONCE && isPinVerified(connection)

        // A VERIFY without data does not try a PIN: the card answers 9000 if the PIN is currently verified and
        // 63Cx, with the attempts left, if it is not (yubikit relies on the same behavior).
        private fun isPinVerified(connection: SmartCardConnection): Boolean {
            val response = connection.sendAndReceive(byteArrayOf(0x00, 0x20, 0x00, 0x80.toByte()))
            return response.size >= 2 && response[response.size - 2] == 0x90.toByte() && response[response.size - 1] == 0.toByte()
        }

        private suspend fun <T : Any> useHeld(
            onRejectedKey: ((String) -> Unit)? = null,
            operation: (PivSession, SmartCardConnection, () -> Unit) -> T,
        ): T? {
            val context = currentCoroutineContext()
            return withContext(cardDispatcher) {
                val session = synchronized(heldLock) {
                    held?.takeUnless { it.busy }?.also {
                        it.busy = true
                        it.idleTask?.cancel(false)
                    }
                } ?: return@withContext null
                try {
                    operation(session.session, session.connection) { context.ensureActive() }
                } catch (e: PivFailure) {
                    if (onRejectedKey == null || (e !is WrongKey && e !is UnsupportedKey && e !is EmptySlot)) throw e
                    onRejectedKey(session.usbDeviceName)
                    release(session)
                    null
                } catch (_: IOException) {
                    // The connection broke, for instance because the key was replugged: start over with discovery.
                    release(session)
                    null
                } finally {
                    finishUse(session)
                }
            }
        }

        private fun finishUse(session: HeldSession) = synchronized(heldLock) {
            session.busy = false
            if (held === session) scheduleIdleClose(session)
        }

        private fun scheduleIdleClose(session: HeldSession) {
            session.idleTask?.cancel(false)
            session.idleTask = idleExecutor.schedule({
                val expired = synchronized(heldLock) {
                    (held === session && !session.busy).also { if (it) held = null }
                }
                if (expired) session.close()
            }, session.idleMs, TimeUnit.MILLISECONDS)
        }

        private fun hold(session: HeldSession, device: UsbYubiKeyDevice, context: Context) {
            val idleMs = holdIdleMs(context)
            val previous = synchronized(heldLock) {
                held.also {
                    held = session.takeIf { idleMs > 0 }
                    session.idleMs = idleMs
                    if (idleMs > 0) scheduleIdleClose(session)
                }
            }
            if (previous !== session) previous?.close()
            if (idleMs <= 0) {
                // The user does not want the key kept busy between logins.
                session.close()
                return
            }

            // UsbYubiKeyDevice.setOnClosed is no use for noticing an unplugged key: stopping USB discovery
            // closes the device object as well, while the connection is meant to outlive that.
            val usbDevice = device.usbDevice
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val detached = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    if (detached?.deviceName == usbDevice.deviceName) {
                        cardExecutor.execute { release(session) }
                    }
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            synchronized(heldLock) {
                session.onClose = { runCatching { context.unregisterReceiver(receiver) } }
            }
        }

        private fun release(session: HeldSession) {
            synchronized(heldLock) { if (held === session) held = null }
            session.close()
        }

        private data class SlotKey(val slot: Slot, val publicKey: PublicKey, val pinPolicy: PinPolicy)

        private fun readSlotKey(session: PivSession, slot: Slot): SlotKey {
            if (!session.supports(PivSession.FEATURE_METADATA)) throw UnsupportedKey(UnsupportedKey.Reason.FIRMWARE)
            val metadata = try {
                session.getSlotMetadata(slot)
            } catch (e: ApduException) {
                // An empty slot cannot match the requested identity. Let held-session recovery
                // release this device and discover another key, without retrying PIN or touch failures.
                if (e.sw == SW.REFERENCED_DATA_NOT_FOUND) throw EmptySlot()
                throw e
            }
            if (metadata.keyType != KeyType.ECCP256) throw UnsupportedKey(UnsupportedKey.Reason.ALGORITHM, describeKeyType(metadata.keyType))
            return SlotKey(slot, metadata.publicKeyValues.toPublicKey(), metadata.pinPolicy)
        }

        // The SDK handles Android USB permission. Keep reader mode and USB discovery scoped to one visible
        // prompt and one card operation; USB connections stay open afterwards, see preferredHoldIdleMs.
        @MainThread
        private suspend fun <T : Any> withSession(
            activity: Activity,
            retryRejectedHeldKey: Boolean = false,
            operation: (PivSession, SmartCardConnection, () -> Unit) -> T,
        ): T = withTimeoutOrNull(deviceTimeoutMs) {
            discoveryMutex.withLock {
                var rejectedUsbDeviceName: String? = null
                val onRejectedKey: ((String) -> Unit)? = if (retryRejectedHeldKey) {
                    { rejectedUsbDeviceName = it }
                } else {
                    null
                }
                useHeld(onRejectedKey, operation)?.let { return@withLock it }
                discoverSession(activity, rejectedUsbDeviceName, operation)
            }
        } ?: throw DeviceTimeout()

        @MainThread
        private suspend fun <T : Any> discoverSession(
            activity: Activity,
            rejectedUsbDeviceName: String?,
            operation: (PivSession, SmartCardConnection, () -> Unit) -> T,
        ): T {
            val context = currentCoroutineContext()
            val manager = createManager(activity)
            val result = CompletableDeferred<T>()
            val claimed = AtomicBoolean()
            val nfcDevice = AtomicReference<NfcYubiKeyDevice?>()
            val connectionType = SmartCardConnection::class.java
            val accept: (YubiKeyDevice) -> Unit = accept@{ device ->
                // A different identity may be on NFC or another USB key. Do not immediately reclaim
                // the held key that failed the identity check before discovery could reach it.
                if (device is UsbYubiKeyDevice && device.usbDevice.deviceName == rejectedUsbDeviceName) return@accept
                if (!result.isActive || !device.supportsConnection(connectionType) || !claimed.compareAndSet(false, true)) {
                    return@accept
                }
                if (device is NfcYubiKeyDevice) nfcDevice.set(device)
                try {
                    requestDeviceOperation(device, activity.applicationContext, result, context, operation)
                } catch (e: Exception) {
                    result.completeExceptionally(e)
                }
            }
            val nfc = NfcDiscovery(manager, activity, accept)
            try {
                nfc.start()
                manager.startUsbDiscovery(UsbConfiguration().handlePermissions(true)) { accept(it) }
                return result.await()
            } finally {
                result.cancel()
                nfc.unregisterReceiver()
                try {
                    manager.stopUsbDiscovery()
                } finally {
                    nfcDevice.get()?.let { nfc.stopWhenRemoved(it, idleExecutor) } ?: nfc.stop()
                }
            }
        }

        /** Owns NFC restart broadcasts and reader mode for one discovery attempt. */
        private class NfcDiscovery(
            private val manager: YubiKitManager,
            private val activity: Activity,
            private val accept: (YubiKeyDevice) -> Unit,
        ) {
            private var nfcStarted = false

            // NFC may be switched on while we wait (quick settings, or back from the settings screen),
            // so a failed start is retried whenever the adapter state changes.
            private fun startNfc() {
                if (nfcStarted) return
                try {
                    manager.startNfcDiscovery(NfcConfiguration().timeout(15_000), activity) { accept(it) }
                    nfcStarted = true
                } catch (_: NfcNotAvailable) {
                    // USB can be used even when NFC is disabled or unavailable.
                }
            }
            private val nfcStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF) == NfcAdapter.STATE_ON) {
                        startNfc()
                    }
                }
            }
            private val appContext = activity.applicationContext
            private var receiverRegistered = false

            fun start() {
                startNfc()
                if (!nfcStarted) {
                    ContextCompat.registerReceiver(
                        appContext,
                        nfcStateReceiver,
                        IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
                        // Sent by the NFC service, not by the system uid, so a not-exported receiver would
                        // not get it. Only the system can send this protected broadcast.
                        ContextCompat.RECEIVER_EXPORTED,
                    )
                    receiverRegistered = true
                }
            }

            fun unregisterReceiver() {
                if (receiverRegistered) appContext.unregisterReceiver(nfcStateReceiver)
            }

            fun stop() {
                if (nfcStarted) manager.stopNfcDiscovery(activity)
            }

            /**
             * Turning reader mode off while the key is still on the phone hands the tag back to Android, which
             * announces it again and offers it to other apps. So wait for the key to be lifted, but not forever.
             */
            fun stopWhenRemoved(device: NfcYubiKeyDevice, scheduler: ScheduledExecutorService) {
                val stopped = AtomicBoolean()
                val stopOnce = Runnable { if (stopped.compareAndSet(false, true)) stop() }
                try {
                    device.remove(stopOnce)
                    scheduler.schedule(stopOnce, NFC_REMOVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } catch (_: Exception) {
                    stopOnce.run()
                }
            }
        }

        private fun <T : Any> requestDeviceOperation(
            device: YubiKeyDevice,
            appContext: Context,
            result: CompletableDeferred<T>,
            context: CoroutineContext,
            operation: (PivSession, SmartCardConnection, () -> Unit) -> T,
        ) {
            if (device is UsbYubiKeyDevice) {
                cardExecutor.execute { runAndHold(device, appContext, result, context, operation) }
            } else {
                device.requestConnection(SmartCardConnection::class.java) { connection ->
                    try {
                        context.ensureActive()
                        result.complete(operation(createSession(connection.value), connection.value) { context.ensureActive() })
                    } catch (e: Exception) {
                        result.completeExceptionally(e)
                    }
                }
            }
        }

        // Runs on cardExecutor. The connection is opened here rather than through requestConnection, which
        // would close it as soon as the callback returns.
        private fun <T : Any> runAndHold(
            device: UsbYubiKeyDevice,
            appContext: Context,
            result: CompletableDeferred<T>,
            context: CoroutineContext,
            operation: (PivSession, SmartCardConnection, () -> Unit) -> T,
        ) {
            var session: HeldSession? = null
            try {
                context.ensureActive()
                val connection = device.openConnection(SmartCardConnection::class.java)
                session = try {
                    HeldSession(connection, createSession(connection), device.usbDevice.deviceName)
                } catch (e: Throwable) {
                    connection.close()
                    throw e
                }
                val value = operation(session.session, session.connection) { context.ensureActive() }
                hold(session, device, appContext)
                result.complete(value)
            } catch (e: Exception) {
                // A wrong PIN or a refused signature leaves a perfectly good connection; a transport error does not.
                if (session != null && (e !is IOException || e is PivFailure)) hold(session, device, appContext) else session?.close()
                result.completeExceptionally(e)
            }
        }
    }

    // Names a key type in the error shown for a slot holding one that is not supported.
    private fun describeKeyType(keyType: KeyType): String = when (keyType) {
        KeyType.RSA1024 -> "RSA 1024"
        KeyType.RSA2048 -> "RSA 2048"
        KeyType.RSA3072 -> "RSA 3072"
        KeyType.RSA4096 -> "RSA 4096"
        KeyType.ECCP384 -> "EC P-384"
        KeyType.ED25519 -> "Ed25519"
        else -> keyType.name
    }

    open class PivFailure : IOException()
    class DeviceTimeout : IOException()
    class PinRequired : PivFailure()

    /** The slot's key cannot be used: [algorithm] names the unsupported key type, if that is the reason. */
    class UnsupportedKey(val reason: Reason, val algorithm: String? = null) : PivFailure() {
        enum class Reason { FIRMWARE, ALGORITHM }
    }

    class EmptySlot : PivFailure()
    class WrongKey : PivFailure()
}
