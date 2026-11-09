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

package org.connectbot.fido2

import android.app.Activity
import android.content.Context
import android.nfc.Tag
import android.nfc.TagLostException
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.connectbot.R
import org.connectbot.ctap2.Ctap2Client
import org.connectbot.ctap2.Ctap2Errors
import org.connectbot.ctap2.Fido2Credential
import org.connectbot.ctap2.Fido2Result
import org.connectbot.ctap2.Fido2SignatureResult
import org.connectbot.ctap2.android.Ctap2Device
import org.connectbot.ctap2.android.Ctap2DeviceWorker
import org.connectbot.ctap2.android.Ctap2Discovery
import org.connectbot.ctap2.android.Ctap2NfcSessions
import org.connectbot.ctap2.android.Fido2ConnectionState
import org.connectbot.data.entity.Fido2Transport
import org.connectbot.di.CoroutineDispatchers
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * High-level manager for FIDO2 security key operations using YubiKit SDK.
 *
 * Coordinates:
 * - USB and NFC device detection and connection
 * - CTAP2 protocol communication via YubiKit
 * - Credential discovery and management
 * - Signing operations for SSH authentication
 */
@Singleton
class Fido2Manager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)

    // Single-thread executor for all YubiKey operations - ensures thread safety
    private val nfcSessions = Ctap2NfcSessions()

    private val _connectionState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Disconnected)
    val connectionState: StateFlow<Fido2ConnectionState> = _connectionState.asStateFlow()
    private val discovery = Ctap2Discovery(
        context,
        ::launchUsbConnect,
        { device ->
            val signingDiscovery = _waitingForNfcSigning.value
            scope.launch {
                if (signingDiscovery) {
                    handleNfcTagForSigning(device.nfcTag!!)
                } else {
                    connectToDevice(device, "NFC", "Security Key")
                }
            }
        },
        ::disconnect,
        _connectionState,
    )

    private var currentDevice: Ctap2Device? = null
    private var currentSession: Ctap2Client? = null

    private var usbConnectInProgress = false
    private val nfcConnectionMutex = Mutex()
    private val deviceWorker = Ctap2DeviceWorker(dispatchers.io)
    private val signingMutex = Mutex()
    private val importGeneration = AtomicLong()

    /** Keep a device operation serialized until its SDK callback has finished, even on cancellation. */
    private suspend fun <T> withDevice(operation: suspend () -> T): T = deviceWorker.execute(operation)

    /** Invalidates pending import callbacks, including operations already on the device. */
    fun cancelImport() {
        importGeneration.incrementAndGet()
        pendingPin = null
        nfcCredentialCallback = null
        usbCredentialCallback = null
    }

    /**
     * Start USB device discovery.
     * Call this when the activity becomes visible.
     * Also checks for already-connected USB devices.
     */
    fun startUsbDiscovery() = discovery.startUsbDiscovery()

    fun stopUsbDiscovery() = discovery.stopUsbDiscovery()

    /** Verify a fresh USB connection rather than reusing a previous discovery. */
    suspend fun awaitFreshUsbConnection(): Boolean = signingMutex.withLock {
        withContext(dispatchers.io) {
            stopUsbDiscovery()
            // Credential import may have left a pending PIN. SSH signing supplies
            // its own PIN later and only needs device discovery at this stage.
            pendingPin = null
            startUsbDiscovery()
        }
        val state = connectionState.first {
            (it is Fido2ConnectionState.Connected && it.transport == "USB") || it is Fido2ConnectionState.Error
        }
        state is Fido2ConnectionState.Connected
    }

    /**
     * Launch a USB connection attempt while preventing duplicate concurrent attempts.
     */
    private fun launchUsbConnect(device: Ctap2Device, deviceName: String?) {
        if (usbConnectInProgress) {
            Timber.d("USB connect already in progress, ignoring duplicate callback")
            return
        }

        usbConnectInProgress = true
        scope.launch {
            try {
                connectToDevice(device, "USB", deviceName)
            } finally {
                usbConnectInProgress = false
            }
        }
    }

    /**
     * Start NFC device discovery.
     * Call this in Activity.onResume().
     */
    fun startNfcDiscovery(activity: Activity) = discovery.startNfcDiscovery(activity)

    fun stopNfcDiscovery(activity: Activity) = discovery.stopNfcDiscovery(activity)

    // Stored PIN for operations (must be collected before connection for NFC, or before retry for USB)
    private var pendingPin: String? = null

    // Callback for delivering credential results
    private var nfcCredentialCallback: ((Fido2Result<List<Fido2Credential>>) -> Unit)? = null
    private var usbCredentialCallback: ((Fido2Result<List<Fido2Credential>>) -> Unit)? = null

    /**
     * Set the PIN to use for the next connection.
     */
    fun setPendingPin(pin: String) {
        pendingPin = pin
    }

    /**
     * Set a callback to receive NFC credential enumeration results.
     */
    fun setNfcCredentialCallback(callback: (Fido2Result<List<Fido2Credential>>) -> Unit) {
        nfcCredentialCallback = callback
    }

    /**
     * Set a callback to receive USB credential enumeration results.
     */
    fun setUsbCredentialCallback(callback: (Fido2Result<List<Fido2Credential>>) -> Unit) {
        usbCredentialCallback = callback
    }

    /**
     * Perform USB credential enumeration with PIN using existing session.
     * Call this after setting the PIN to enumerate credentials.
     * Uses the session that was kept open from the initial connection.
     */
    fun retryUsbWithPin() {
        val session = currentSession
        val pin = pendingPin
        val callback = usbCredentialCallback
        val generation = importGeneration.get()

        if (session == null) {
            Log.e(TAG, "retryUsbWithPin: No session or protocol available")
            callback?.invoke(Fido2Result.Error("No device connection"))
            usbCredentialCallback = null
            pendingPin = null
            return
        }

        if (pin == null) {
            Log.e(TAG, "retryUsbWithPin: No PIN provided")
            callback?.invoke(Fido2Result.PinRequired(null))
            usbCredentialCallback = null
            return
        }

        scope.launch {
            val result = withDevice {
                if (generation != importGeneration.get()) return@withDevice null
                val device = currentDevice ?: return@withDevice Fido2Result.Error("No device connection")
                device.enumerateUsbCredentials(session, pin) { it.startsWith(SSH_RP_ID) }
            }
            if (generation != importGeneration.get()) return@launch

            // Deliver result via callback
            pendingPin = null
            if (result != null) callback?.invoke(result)
            usbCredentialCallback = null
        }
    }

    private data class NfcImportRequest(
        val generation: Long,
        val pin: String?,
        val callback: ((Fido2Result<List<Fido2Credential>>) -> Unit)?,
    )

    /**
     * Connect to a YubiKey device and establish a CTAP2 session.
     * Uses FidoConnection for USB (CTAPHID protocol) and SmartCardConnection for NFC (APDU).
     *
     * For NFC, all operations (PIN auth + credential enumeration) are done synchronously
     * within the connection callback since NFC connections are transient.
     */
    private suspend fun connectToDevice(device: Ctap2Device, transport: String, deviceName: String?) {
        val isNfc = transport == "NFC"
        if (isNfc && !nfcConnectionMutex.tryLock()) {
            Timber.d("NFC connection already in progress, ignoring duplicate discovery")
            return
        }
        try {
            val request = withContext(dispatchers.main) {
                NfcImportRequest(importGeneration.get(), pendingPin, nfcCredentialCallback)
            }
            withDevice {
                if (isNfc && request.generation != importGeneration.get()) return@withDevice
                // Disconnect any existing session before opening another.
                closeCurrentSession()
                currentDevice = null

                _connectionState.value = Fido2ConnectionState.Connecting

                if (transport == "USB") {
                    connectUsbDevice(device, deviceName)
                } else {
                    connectNfcDevice(device, deviceName, request)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to FIDO2 device")
            _connectionState.value = Fido2ConnectionState.Error(e.message ?: "Connection failed")
        } finally {
            if (isNfc) nfcConnectionMutex.unlock()
        }
    }

    private suspend fun connectUsbDevice(device: Ctap2Device, deviceName: String?) {
        currentSession = device.openUsbSession()
        currentDevice = device

        _connectionState.value = Fido2ConnectionState.Connected(
            transport = "USB",
            deviceName = deviceName,
        )
        // The ViewModel requests the PIN after discovery.
    }

    private suspend fun connectNfcDevice(device: Ctap2Device, deviceName: String?, request: NfcImportRequest) {
        // NFC: Do ALL operations synchronously in the callback
        // because the NFC connection is transient
        val pin = request.pin
        val callback = request.callback
        val generation = request.generation

        if (pin == null) {
            _connectionState.value = Fido2ConnectionState.Error("PIN required for NFC")
            return
        }

        val result = try {
            device.withNfcSession { client ->
                client.enumerateCredentials(pin, { it.startsWith(SSH_RP_ID) })
            }
        } catch (e: Exception) {
            mapNfcCredentialError(e)
        }

        withContext(dispatchers.main) {
            if (generation != importGeneration.get()) return@withContext

            // Deliver result via callback and update state
            pendingPin = null
            callback?.invoke(result)
            nfcCredentialCallback = null

            when (result) {
                is Fido2Result.Success -> {
                    _connectionState.value = Fido2ConnectionState.Connected(
                        transport = "NFC",
                        deviceName = deviceName,
                    )
                }

                else -> {
                    _connectionState.value = Fido2ConnectionState.Disconnected
                }
            }
        }
    }

    private fun mapNfcCredentialError(e: Exception): Fido2Result<List<Fido2Credential>> = Ctap2Errors.fromException(e, "NFC operation failed")

    /**
     * Connect to an NFC tag directly.
     * Use this when receiving an NFC intent in the activity.
     */
    suspend fun connectToNfcTag(tag: Tag) = withContext(dispatchers.io) {
        // Emit tag detected signal for connect prompt to observe

        try {
            // Create NfcYubiKeyDevice using the sessionExecutor for thread safety
            val device = nfcSessions.device(tag)
            connectToDevice(device, "NFC", "Security Key")
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to NFC tag")
            _connectionState.value = Fido2ConnectionState.Error(e.message ?: "NFC connection failed")
        }
    }

    /**
     * Disconnect from the current device.
     */
    fun disconnect() {
        val session = currentSession
        currentDevice = null
        currentSession = null
        _connectionState.value = Fido2ConnectionState.Disconnected
        scope.launch(dispatchers.io) {
            deviceWorker.execute { closeSession(session) }
        }
    }

    private fun closeCurrentSession() {
        val session = currentSession
        currentSession = null
        closeSession(session)
    }

    private fun closeSession(session: Ctap2Client?) {
        try {
            session?.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing session")
        }
    }

    // ==================== SSH Authentication Support ====================

    class SigningRequest internal constructor(val owner: Any?)

    private val _signingRequest = MutableStateFlow<SigningRequest?>(null)
    val signingRequest: StateFlow<SigningRequest?> = _signingRequest.asStateFlow()

    /** Own the request until completion; cancellation while queued cannot affect its predecessor. */
    suspend fun signSshChallenge(
        credentialId: ByteArray,
        challenge: ByteArray,
        rpId: String,
        pin: String,
        transport: Fido2Transport,
        owner: Any? = null,
    ): Fido2Result<Fido2SignatureResult> = signingMutex.withLock {
        withContext(dispatchers.main) {
            var request: SigningRequest? = null
            try {
                suspendCancellableCoroutine { continuation ->
                    request = prepareSshSigning(credentialId, challenge, rpId, owner) { result ->
                        continuation.resume(result)
                    }
                    when (transport) {
                        Fido2Transport.USB -> connectAndSignUsb(pin)
                        Fido2Transport.NFC -> requestNfcSigning(pin)
                    }
                }
            } finally {
                if (_signingRequest.value === request) clearSigningRequest()
            }
        }
    }

    // Callback for SSH signing operations
    private var sshSigningCallback: ((Fido2Result<Fido2SignatureResult>) -> Unit)? = null
    private var sshCredentialId: ByteArray? = null
    private var sshChallenge: ByteArray? = null
    private var sshPin: String? = null
    private var sshRpId: String? = null

    // State flow to signal when waiting for NFC tap for SSH signing
    private val _waitingForNfcSigning = MutableStateFlow(false)
    val waitingForNfcSigning: StateFlow<Boolean> = _waitingForNfcSigning.asStateFlow()
    private val _nfcSigningTagLost = MutableStateFlow(false)
    val nfcSigningTagLost: StateFlow<Boolean> = _nfcSigningTagLost.asStateFlow()

    /**
     * Set up for SSH signing operation.
     * Call this before connecting to prepare for signing.
     */
    internal fun prepareSshSigning(
        credentialId: ByteArray,
        challenge: ByteArray,
        rpId: String = SSH_RP_ID,
        owner: Any? = null,
        callback: (Fido2Result<Fido2SignatureResult>) -> Unit,
    ): SigningRequest {
        check(_signingRequest.value == null) { "Signing request already active" }
        val request = SigningRequest(owner)
        _signingRequest.value = request
        sshCredentialId = credentialId
        sshChallenge = challenge
        sshRpId = rpId
        sshSigningCallback = callback
        return request
    }

    /**
     * Request NFC tap for SSH signing.
     * UI should observe waitingForNfcSigning and start NFC discovery when true.
     */
    internal fun requestNfcSigning(pin: String) {
        sshPin = pin
        _nfcSigningTagLost.value = false
        _waitingForNfcSigning.value = true
    }

    /**
     * Cancel pending NFC signing request.
     */
    fun cancelNfcSigning(request: SigningRequest? = _signingRequest.value) {
        if (request == null || _signingRequest.value !== request) return
        val callback = sshSigningCallback
        clearSigningRequest()
        callback?.invoke(Fido2Result.Error("NFC signing cancelled"))
    }

    private fun clearSigningRequest() {
        _signingRequest.value = null
        sshCredentialId = null
        sshChallenge = null
        sshPin = null
        sshRpId = null
        sshSigningCallback = null
        _nfcSigningTagLost.value = false
        _waitingForNfcSigning.value = false
    }

    /** Keep the tap prompt and NFC reader active until signing completes or is cancelled. */
    suspend fun handleNfcTagForSigning(tag: Tag) {
        if (!nfcConnectionMutex.tryLock()) return
        try {
            val credId = sshCredentialId ?: return
            val challenge = sshChallenge ?: return
            val pin = sshPin ?: return
            val rpId = sshRpId ?: SSH_RP_ID
            val callback = sshSigningCallback ?: return
            _nfcSigningTagLost.value = false

            val result = connectAndSignNfc(tag, pin, credId, challenge, rpId)
            // Cancellation or a replacement request must not receive a late signature.
            if (sshSigningCallback !== callback) return
            if (result is Fido2Result.Error &&
                generateSequence(result.cause) { it.cause }.any { it is TagLostException }
            ) {
                _nfcSigningTagLost.value = true
                return
            }

            clearSigningRequest()
            callback(result)
        } finally {
            nfcConnectionMutex.unlock()
        }
    }

    /**
     * Connect to USB device and perform SSH signing.
     * This handles the full flow: connect -> PIN auth -> sign -> return result via callback.
     */
    internal fun connectAndSignUsb(pin: String) {
        val request = _signingRequest.value ?: return
        val credId = sshCredentialId ?: return
        val challenge = sshChallenge ?: return
        val rpId = sshRpId ?: SSH_RP_ID
        val callback = sshSigningCallback ?: return

        val device = currentDevice
        if (device == null) {
            clearSigningRequest()
            callback(Fido2Result.Error(context.getString(R.string.fido2_usb_unavailable)))
            return
        }

        scope.launch {
            val result = withDevice {
                if (_signingRequest.value !== request) return@withDevice null
                closeCurrentSession()

                try {
                    device.openUsbSession().use { client ->
                        client.getAssertion(pin, credId, rpId, sha256(challenge))
                    }
                } catch (e: Exception) {
                    if (e is IllegalStateException || e is SecurityException) {
                        if (currentDevice === device) disconnect()
                        Fido2Result.Error(context.getString(R.string.fido2_usb_unavailable), e)
                    } else {
                        Ctap2Errors.fromException(e, "USB signing failed")
                    }
                }
            }

            // Do not deliver a late result to a replacement signing request.
            if (sshSigningCallback !== callback) return@launch
            clearSigningRequest()
            if (result != null) callback(result)
        }
    }

    /**
     * Connect to NFC device and perform SSH signing.
     * This handles the full flow: connect -> PIN auth -> sign in a single NFC tap.
     */
    suspend fun connectAndSignNfc(tag: Tag, pin: String, credentialId: ByteArray, challenge: ByteArray, rpId: String = SSH_RP_ID): Fido2Result<Fido2SignatureResult> = withDevice {
        try {
            val device = nfcSessions.device(tag)
            device.withNfcSession { client ->
                client.getAssertion(pin, credentialId, rpId, sha256(challenge))
            }
        } catch (e: Exception) {
            Ctap2Errors.fromException(e, "NFC signing failed")
        }
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    companion object {
        private const val TAG = "Fido2Manager"

        /** SSH relying party ID used by OpenSSH for FIDO2 keys */
        const val SSH_RP_ID = "ssh:"
    }
}
