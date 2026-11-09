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
import android.hardware.usb.UsbManager
import android.nfc.Tag
import android.nfc.TagLostException
import android.util.Log
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.android.transport.usb.DeviceFilter
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.android.transport.usb.UsbYubiKeyDevice
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.fido.CtapException
import com.yubico.yubikit.core.fido.FidoConnection
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.fido.ctap.ClientPin
import com.yubico.yubikit.fido.ctap.CredentialManagement
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocol
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV1
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV2
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
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
import org.connectbot.data.entity.Fido2Transport
import org.connectbot.di.CoroutineDispatchers
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
    private val yubiKitManager = YubiKitManager(context)

    // Single-thread executor for all YubiKey operations - ensures thread safety
    private val sessionExecutor = Executors.newSingleThreadExecutor()

    private val _connectionState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Disconnected)
    val connectionState: StateFlow<Fido2ConnectionState> = _connectionState.asStateFlow()

    private var currentDevice: YubiKeyDevice? = null
    private var currentSession: Ctap2Session? = null
    private var pinUvAuthProtocol: PinUvAuthProtocol? = null
    private var usbDiscoveryActive = false
    private var usbConnectInProgress = false
    private val nfcConnectionMutex = Mutex()
    private val deviceMutex = Mutex()
    private val signingMutex = Mutex()
    private val importGeneration = AtomicLong()

    /** Keep a device operation serialized until its SDK callback has finished, even on cancellation. */
    private suspend fun <T> withDevice(operation: suspend () -> T): T = withContext(dispatchers.io) {
        deviceMutex.withLock {
            withContext(NonCancellable) { operation() }
        }
    }

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
    fun startUsbDiscovery() {
        if (usbDiscoveryActive) {
            Timber.d("USB discovery already active, skipping duplicate start")
            return
        }

        try {
            val usbConfig = UsbConfiguration().setDeviceFilter(FIDO2_DEVICE_FILTER)
            yubiKitManager.startUsbDiscovery(usbConfig) { device ->
                Timber.d("USB FIDO2 device detected: ${device.usbDevice.productName}")
                launchUsbConnect(device, device.usbDevice.productName)
            }
            usbDiscoveryActive = true

            // Also check for already-connected USB devices
            checkForConnectedUsbDevices()
        } catch (e: Exception) {
            Timber.e(e, "Failed to start USB discovery")
            _connectionState.value = Fido2ConnectionState.Error(e.message ?: "USB discovery failed")
        }
    }

    /**
     * Check for already-connected USB devices.
     * YubiKit's startUsbDiscovery may not immediately detect devices that were
     * connected before discovery started.
     */
    private fun checkForConnectedUsbDevices() {
        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
            val deviceList = usbManager.deviceList

            for ((_, usbDevice) in deviceList) {
                // Check if this is a supported FIDO2 security key
                if (usbDevice.vendorId in SUPPORTED_VENDOR_IDS) {
                    Timber.d("Found already-connected FIDO2 device: ${usbDevice.productName}")

                    if (usbManager.hasPermission(usbDevice)) {
                        Timber.d("Already have permission for device, connecting...")
                        try {
                            val device = UsbYubiKeyDevice(usbManager, usbDevice)
                            launchUsbConnect(device, usbDevice.productName)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to connect to USB device")
                        }
                    } else {
                        Timber.d("Need permission for device, will be requested by YubiKit")
                        // YubiKit will request permission via its discovery mechanism
                    }
                    break // Only handle the first FIDO2 device found
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check for connected USB devices")
        }
    }

    /**
     * Stop USB device discovery.
     * Call this when the activity is no longer visible.
     */
    fun stopUsbDiscovery() {
        try {
            yubiKitManager.stopUsbDiscovery()
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop USB discovery")
        } finally {
            usbDiscoveryActive = false
            // YubiKit invalidates the discovered device when discovery stops.
            disconnect()
        }
    }

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
    private fun launchUsbConnect(device: YubiKeyDevice, deviceName: String?) {
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
    fun startNfcDiscovery(activity: Activity) {
        val signingDiscovery = _waitingForNfcSigning.value
        try {
            yubiKitManager.startNfcDiscovery(
                // FIDO2 uses ISO-DEP; NDEF probing can time out before tag discovery.
                NfcConfiguration().skipNdefCheck(true).timeout(30000), // 30 second timeout
                activity,
            ) { device ->
                Timber.d("NFC YubiKey detected")
                scope.launch {
                    if (signingDiscovery) {
                        handleNfcTagForSigning(device.tag)
                    } else {
                        connectToDevice(device, "NFC", "Security Key")
                    }
                }
            }
        } catch (e: NfcNotAvailable) {
            Timber.w("NFC not available on this device")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start NFC discovery")
        }
    }

    /**
     * Stop NFC device discovery.
     * Call this in Activity.onPause().
     */
    fun stopNfcDiscovery(activity: Activity) {
        try {
            yubiKitManager.stopNfcDiscovery(activity)
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop NFC discovery")
        }
    }

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
        val protocol = pinUvAuthProtocol
        val pin = pendingPin
        val callback = usbCredentialCallback
        val generation = importGeneration.get()

        if (session == null || protocol == null) {
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
                try {
                    Log.d(TAG, "USB authenticating with PIN using existing session")
                    enumerateCredentialsWithPin(session, protocol, pin)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (shouldRetryUsbWithFreshConnection(e)) {
                        Log.w(TAG, "USB session appears stale, retrying with fresh connection")
                        reconnectAndEnumerateUsbCredentials(pin)
                    } else {
                        mapPinOrCredentialError(e, session = session, protocol = protocol)
                    }
                }
            }
            if (generation != importGeneration.get()) return@launch

            // Deliver result via callback
            pendingPin = null
            if (result != null) callback?.invoke(result)
            usbCredentialCallback = null
        }
    }

    /**
     * Enumerate SSH credentials after PIN authentication.
     */
    private fun enumerateCredentialsWithPin(
        session: Ctap2Session,
        protocol: PinUvAuthProtocol,
        pin: String,
    ): Fido2Result<List<Fido2Credential>> {
        val clientPin = ClientPin(session, protocol)
        val token = clientPin.getPinToken(
            normalizePin(pin),
            ClientPin.PIN_PERMISSION_CM,
            null,
        )
        Log.d(TAG, "USB PIN authentication successful")

        val credMgmt = CredentialManagement(session, protocol, token)
        val credentialList = enumerateSshCredentials(credMgmt, "USB")

        Log.i(TAG, "USB credential enumeration complete: ${credentialList.size} credentials")
        return Fido2Result.Success(credentialList)
    }

    /**
     * Fallback path when an existing USB session goes stale before PIN auth.
     * Reconnect once and retry the operation with a fresh session.
     */
    private suspend fun reconnectAndEnumerateUsbCredentials(pin: String): Fido2Result<List<Fido2Credential>> {
        val device = currentDevice ?: return Fido2Result.Error("No device connection")

        return suspendCancellableCoroutine { continuation ->
            device.requestConnection(FidoConnection::class.java) { connectionResult ->
                try {
                    val connection = connectionResult.value
                    val reconnectSession = Ctap2Session(connection)
                    val result = enumerateReconnectedUsbCredentials(reconnectSession, pin)

                    continuation.resume(result)
                } catch (e: Exception) {
                    Log.e(TAG, "USB reconnect operation failed: ${e.message}", e)
                    continuation.resume(mapPinOrCredentialError(e))
                }
            }
        }
    }

    private fun enumerateReconnectedUsbCredentials(
        reconnectSession: Ctap2Session,
        pin: String,
    ): Fido2Result<List<Fido2Credential>> {
        val reconnectProtocol = selectPinProtocol(reconnectSession)
        return try {
            enumerateCredentialsWithPin(
                session = reconnectSession,
                protocol = reconnectProtocol,
                pin = pin,
            )
        } catch (e: Exception) {
            if (shouldRetryPinBlockedWithV1(e, reconnectProtocol)) {
                Timber.w(e, "USB PIN blocked on protocol V2, retrying with PIN protocol V1")
                try {
                    enumerateCredentialsWithPin(
                        session = reconnectSession,
                        protocol = PinUvAuthProtocolV1(),
                        pin = pin,
                    )
                } catch (fallbackError: Exception) {
                    mapPinOrCredentialError(
                        fallbackError,
                        v1FallbackAttempted = true,
                        session = reconnectSession,
                        protocol = PinUvAuthProtocolV1(),
                    )
                }
            } else {
                mapPinOrCredentialError(
                    e,
                    session = reconnectSession,
                    protocol = reconnectProtocol,
                )
            }
        } finally {
            try {
                reconnectSession.close()
            } catch (closeError: Exception) {
                Timber.w(closeError, "Failed to close reconnect USB session")
            }
        }
    }

    private fun matchesCtapError(error: Throwable, code: Byte, fallback: String): Boolean {
        val causes = generateSequence(error) { it.cause }.toList()
        val ctap = causes.filterIsInstance<CtapException>().firstOrNull()
        return if (ctap != null) {
            ctap.ctapError == code
        } else {
            causes.any { it.message?.contains(fallback, ignoreCase = true) == true }
        }
    }

    private fun shouldRetryUsbWithFreshConnection(e: Exception): Boolean {
        val message = e.message ?: return false
        return message.contains("Failed to send full packet", ignoreCase = true) ||
            message.contains("broken pipe", ignoreCase = true) ||
            message.contains("connection reset", ignoreCase = true)
    }

    private fun shouldRetryPinBlockedWithV1(e: Exception, protocol: PinUvAuthProtocol): Boolean {
        if (protocol !is PinUvAuthProtocolV2) return false
        return matchesCtapError(e, CtapException.ERR_PIN_BLOCKED, "PIN_BLOCKED")
    }

    private fun mapPinOrCredentialError(
        e: Exception,
        v1FallbackAttempted: Boolean = false,
        session: Ctap2Session? = null,
        protocol: PinUvAuthProtocol? = null,
    ): Fido2Result<List<Fido2Credential>> {
        Log.e(TAG, "USB operation failed: ${e.message}", e)
        return when {
            matchesCtapError(e, CtapException.ERR_PIN_INVALID, "PIN_INVALID") -> {
                Fido2Result.PinInvalid(attemptsRemaining = null)
            }

            matchesCtapError(e, CtapException.ERR_PIN_AUTH_BLOCKED, "PIN_AUTH_BLOCKED") -> {
                if (v1FallbackAttempted) {
                    Fido2Result.PinLocked(
                        "PIN is temporarily blocked. Retried with PIN protocol V1; reinsert the security key and try again.",
                    )
                } else {
                    Fido2Result.PinLocked(ERR_PIN_AUTH_BLOCKED)
                }
            }

            matchesCtapError(e, CtapException.ERR_PIN_BLOCKED, "PIN_BLOCKED") -> {
                pinBlockedError(session, protocol, v1FallbackAttempted)
            }

            else -> Fido2Result.Error(e.message ?: "USB operation failed")
        }
    }

    private fun pinBlockedError(
        session: Ctap2Session?,
        protocol: PinUvAuthProtocol?,
        v1FallbackAttempted: Boolean,
    ): Fido2Result.PinLocked {
        val retries = getPinRetriesWithFallback(session, protocol)
        return if (retries != null && retries.count > 0) {
            val retryHint = if (v1FallbackAttempted) {
                "Retried with PIN protocol V1."
            } else {
                "Using current PIN protocol."
            }
            val powerCycleHint = if (retries.powerCycleState == true) {
                "Power-cycle or reinsert the key, then retry."
            } else {
                "Reinsert the key and retry."
            }
            Fido2Result.PinLocked(
                "Authenticator reported PIN_BLOCKED, but $retryHint Remaining retries: ${retries.count}. " +
                    "This may be a device firmware quirk. $powerCycleHint",
            )
        } else if (v1FallbackAttempted) {
            Fido2Result.PinLocked("PIN is locked. Retried with PIN protocol V1. Please reset your security key.")
        } else {
            Fido2Result.PinLocked(ERR_PIN_BLOCKED)
        }
    }

    private data class PinRetryInfo(
        val count: Int,
        val powerCycleState: Boolean?,
    )

    private fun getPinRetriesWithFallback(
        session: Ctap2Session?,
        protocol: PinUvAuthProtocol?,
    ): PinRetryInfo? {
        if (session == null || protocol == null) {
            Timber.w("PIN retries probe skipped: session or protocol is null")
            return null
        }

        val primaryName = "primary(${protocol.javaClass.simpleName})"
        getPinRetriesSafely(session, protocol, primaryName)?.let { return it }

        if (protocol !is PinUvAuthProtocolV1) {
            val fallbackProtocol = PinUvAuthProtocolV1()
            val fallbackName = "fallback(${fallbackProtocol.javaClass.simpleName})"
            return getPinRetriesSafely(session, fallbackProtocol, fallbackName)
        }

        return null
    }

    private fun getPinRetriesSafely(
        session: Ctap2Session,
        protocol: PinUvAuthProtocol,
        probeName: String,
    ): PinRetryInfo? = try {
        val retries = ClientPin(session, protocol).getPinRetries()
        val result = PinRetryInfo(
            count = retries.count,
            powerCycleState = retries.powerCycleState,
        )
        Timber.i(
            "PIN retries probe %s succeeded: count=%d, powerCycleState=%s",
            probeName,
            result.count,
            result.powerCycleState,
        )
        result
    } catch (e: Exception) {
        Timber.w(e, "PIN retries probe %s failed: %s", probeName, e.message)
        null
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
    private suspend fun connectToDevice(device: YubiKeyDevice, transport: String, deviceName: String?) {
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

    private suspend fun connectUsbDevice(device: YubiKeyDevice, deviceName: String?) {
        // Discovery only establishes a session; PIN authentication is a separate operation.
        suspendCancellableCoroutine<Unit> { continuation ->
            device.requestConnection(FidoConnection::class.java) { result ->
                try {
                    val connection = result.value
                    Log.d(TAG, "USB FidoConnection established on thread ${Thread.currentThread().name}")
                    val session = Ctap2Session(connection)
                    Log.d(TAG, "USB Ctap2Session created, checking if PIN required")

                    val info = session.cachedInfo
                    val pinRequired = info.options["clientPin"] == true
                    Log.d(TAG, "USB PIN required: $pinRequired")

                    // Always store the device for retry
                    currentDevice = device

                    // Always store session - we'll reuse it for PIN auth
                    // Closing the session also closes the USB connection,
                    // and some devices (like SoloKey) don't handle reconnection well
                    currentSession = session
                    pinUvAuthProtocol = selectPinProtocol(session)
                    continuation.resume(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "USB connection failed: ${e.message}", e)
                    continuation.resumeWithException(e)
                }
            }
        }

        _connectionState.value = Fido2ConnectionState.Connected(
            transport = "USB",
            deviceName = deviceName,
        )
        // The ViewModel requests the PIN after discovery.
    }

    private suspend fun connectNfcDevice(device: YubiKeyDevice, deviceName: String?, request: NfcImportRequest) {
        // NFC: Do ALL operations synchronously in the callback
        // because the NFC connection is transient
        val pin = request.pin
        val callback = request.callback
        val generation = request.generation

        if (pin == null) {
            _connectionState.value = Fido2ConnectionState.Error("PIN required for NFC")
            return
        }

        val result = suspendCancellableCoroutine<Fido2Result<List<Fido2Credential>>> { continuation ->
            device.requestConnection(SmartCardConnection::class.java) { connectionResult ->
                val threadName = Thread.currentThread().name
                Timber.d("NFC callback running on thread $threadName")

                try {
                    val connection = connectionResult.value
                    Timber.d("NFC SmartCardConnection established")

                    // Create session
                    val session = Ctap2Session(connection)
                    Timber.d("NFC Ctap2Session created")

                    // Create PIN protocol based on authenticator support
                    val protocol = selectPinProtocol(session)

                    // Authenticate with PIN - ALL on the same thread
                    Timber.d("NFC authenticating with PIN on thread $threadName")
                    val clientPin = ClientPin(session, protocol)
                    val token = clientPin.getPinToken(
                        normalizePin(pin),
                        ClientPin.PIN_PERMISSION_CM,
                        null,
                    )
                    Timber.d("NFC PIN authentication successful")

                    // Enumerate credentials - ALL on the same thread
                    Timber.d("NFC enumerating credentials on thread $threadName")
                    val credMgmt = CredentialManagement(session, protocol, token)
                    val credentialList = enumerateSshCredentials(credMgmt, "NFC")

                    session.close()
                    Timber.i("NFC credential enumeration complete: ${credentialList.size} credentials")
                    continuation.resume(Fido2Result.Success(credentialList))
                } catch (e: Exception) {
                    Timber.e(e, "NFC operation failed")
                    val error = mapNfcCredentialError(e)
                    continuation.resume(error)
                }
            }
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

    private fun mapNfcCredentialError(e: Exception): Fido2Result<List<Fido2Credential>> = when {
        matchesCtapError(e, CtapException.ERR_PIN_INVALID, "PIN_INVALID") -> {
            Fido2Result.PinInvalid(attemptsRemaining = null)
        }

        matchesCtapError(e, CtapException.ERR_PIN_AUTH_BLOCKED, "PIN_AUTH_BLOCKED") -> {
            Fido2Result.PinLocked(ERR_PIN_AUTH_BLOCKED)
        }

        matchesCtapError(e, CtapException.ERR_PIN_BLOCKED, "PIN_BLOCKED") -> {
            Fido2Result.PinLocked(ERR_PIN_BLOCKED)
        }

        else -> Fido2Result.Error(e.message ?: "NFC operation failed")
    }

    /**
     * Connect to an NFC tag directly.
     * Use this when receiving an NFC intent in the activity.
     */
    suspend fun connectToNfcTag(tag: Tag) = withContext(dispatchers.io) {
        // Emit tag detected signal for connect prompt to observe

        try {
            // Create NfcYubiKeyDevice using the sessionExecutor for thread safety
            val device = NfcYubiKeyDevice(tag, 30000, sessionExecutor)
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
        pinUvAuthProtocol = null
        _connectionState.value = Fido2ConnectionState.Disconnected
        scope.launch(dispatchers.io) {
            deviceMutex.withLock { closeSession(session) }
        }
    }

    private fun closeCurrentSession() {
        val session = currentSession
        currentSession = null
        pinUvAuthProtocol = null
        closeSession(session)
    }

    private fun closeSession(session: Ctap2Session?) {
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

                suspendCancellableCoroutine<Fido2Result<Fido2SignatureResult>> { continuation ->
                    try {
                        // A detached device or revoked USB permission can throw here,
                        // before YubiKit invokes the connection callback.
                        device.requestConnection(FidoConnection::class.java) { connectionResult ->
                            val signingResult = try {
                                val connection = connectionResult.value
                                val session = Ctap2Session(connection)
                                signSshChallengeOnSession(session, pin, credId, challenge, rpId, "USB")
                            } catch (e: Exception) {
                                Log.e(TAG, "USB SSH signing connection failed: ${e.message}", e)
                                mapSigningError(e, "USB SSH signing failed")
                            }
                            continuation.resume(signingResult)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to request USB connection for signing", e)
                        val error = if (e is IllegalStateException || e is SecurityException) {
                            if (currentDevice === device) disconnect()
                            Fido2Result.Error(context.getString(R.string.fido2_usb_unavailable), e)
                        } else {
                            mapSigningError<Fido2SignatureResult>(e, "USB connection failed")
                        }
                        continuation.resume(error)
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
        suspendCancellableCoroutine { continuation ->
            try {
                val device = NfcYubiKeyDevice(tag, 30000, sessionExecutor)

                device.requestConnection(SmartCardConnection::class.java) { connectionResult ->
                    val threadName = Thread.currentThread().name
                    Log.d(TAG, "SSH signing NFC callback on thread $threadName")

                    val signingResult = try {
                        val connection = connectionResult.value
                        Log.d(TAG, "NFC SmartCardConnection established for signing")
                        val session = Ctap2Session(connection)
                        signSshChallengeOnSession(session, pin, credentialId, challenge, rpId, "NFC")
                    } catch (e: Exception) {
                        Log.e(TAG, "NFC SSH signing connection failed: ${e.message}", e)
                        mapSigningError(e, "NFC SSH signing failed")
                    }
                    continuation.resume(signingResult)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to NFC tag for signing: ${e.message}", e)
                continuation.resume(Fido2Result.Error(e.message ?: "NFC connection failed", e))
            }
        }
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun enumerateSshCredentials(
        credMgmt: CredentialManagement,
        logPrefix: String,
    ): List<Fido2Credential> {
        val rps: List<CredentialManagement.RpData> = credMgmt.enumerateRps()
        Timber.d("$logPrefix found ${rps.size} RPs")

        val sshRps = rps.filter(Fido2CredentialMapper::isSshRp)
        if (sshRps.isEmpty()) {
            Timber.d("$logPrefix no SSH RP found")
            return emptyList()
        }

        return sshRps.flatMap { sshRp ->
            val credentials: List<CredentialManagement.CredentialData> = credMgmt.enumerateCredentials(sshRp.rpIdHash)
            Timber.d("$logPrefix found ${credentials.size} credentials for RP ${sshRp.rp["id"]}")
            credentials.map { Fido2CredentialMapper.fromCredentialData(it, sshRp) }
        }
    }

    /**
     * Core SSH-signing flow on an already-established [Ctap2Session]. Performs PIN
     * authentication, requests an assertion for [credentialId], and returns the
     * signature result. Shared by the USB and NFC SSH-signing paths. The session
     * is closed on completion.
     */
    private fun signSshChallengeOnSession(
        session: Ctap2Session,
        pin: String,
        credentialId: ByteArray,
        challenge: ByteArray,
        rpId: String,
        transport: String,
    ): Fido2Result<Fido2SignatureResult> = try {
        val protocol = selectPinProtocol(session)
        Log.d(TAG, "$transport authenticating with PIN for SSH signing")
        val clientPin = ClientPin(session, protocol)
        val token = clientPin.getPinToken(
            normalizePin(pin),
            ClientPin.PIN_PERMISSION_GA,
            rpId,
        )
        val clientDataHash = sha256(challenge)
        val pinUvAuth = protocol.authenticate(token, clientDataHash)
        val allowList = listOf(
            mapOf(
                "type" to PUBLIC_KEY_TYPE,
                "id" to credentialId,
            ),
        )
        val assertions: List<Ctap2Session.AssertionData> = session.getAssertions(
            rpId,
            clientDataHash,
            allowList,
            null,
            null,
            pinUvAuth,
            protocol.version,
            null,
        )
        if (assertions.isEmpty()) {
            Fido2Result.Error(ERR_NO_ASSERTION_RETURNED)
        } else {
            Log.i(TAG, "$transport SSH signing successful")
            Fido2Result.Success(Fido2SignatureMapper.fromAssertion(assertions[0]))
        }
    } catch (e: Exception) {
        Log.e(TAG, "$transport SSH signing failed: ${e.message}", e)
        mapSigningError(e, "$transport SSH signing failed")
    } finally {
        try {
            session.close()
        } catch (closeError: Exception) {
            Timber.w(closeError, "Failed to close $transport signing session")
        }
    }

    /**
     * Map a signing-path exception to the user-facing [Fido2Result.Error]/PIN variants.
     * Shared by the USB and NFC SSH-signing callbacks.
     */
    private fun <T> mapSigningError(e: Exception, defaultMessage: String): Fido2Result<T> = when {
        matchesCtapError(e, CtapException.ERR_PIN_INVALID, "PIN_INVALID") ->
            Fido2Result.PinInvalid(attemptsRemaining = null)

        matchesCtapError(e, CtapException.ERR_PIN_AUTH_BLOCKED, "PIN_AUTH_BLOCKED") ->
            Fido2Result.PinLocked(ERR_PIN_AUTH_BLOCKED)

        matchesCtapError(e, CtapException.ERR_PIN_BLOCKED, "PIN_BLOCKED") ->
            Fido2Result.PinLocked(ERR_PIN_BLOCKED)

        matchesCtapError(e, CtapException.ERR_NO_CREDENTIALS, "NO_CREDENTIALS") ->
            Fido2Result.Error("Credential not found on security key")

        else -> Fido2Result.Error(e.message ?: defaultMessage, e)
    }

    /**
     * Select the appropriate PIN/UV auth protocol based on authenticator support.
     * SoloKey1 and other older authenticators only support protocol V1,
     * while newer authenticators (YubiKey 5+) support V2.
     */
    private fun selectPinProtocol(session: Ctap2Session): PinUvAuthProtocol {
        val supportedProtocols: List<Int> = session.cachedInfo.pinUvAuthProtocols
        return if (2 in supportedProtocols) {
            PinUvAuthProtocolV2()
        } else {
            PinUvAuthProtocolV1()
        }
    }

    companion object {
        private const val TAG = "Fido2Manager"

        /** SSH relying party ID used by OpenSSH for FIDO2 keys */
        const val SSH_RP_ID = "ssh:"

        private const val PUBLIC_KEY_TYPE = "public-key"
        private const val ERR_NO_ASSERTION_RETURNED = "No assertion returned"
        private const val ERR_PIN_AUTH_BLOCKED = "PIN is temporarily blocked. Reinsert the security key and try again."
        private const val ERR_PIN_BLOCKED = "PIN is locked. Please reset your security key."

        /** Yubico vendor ID for USB devices */
        private const val YUBICO_VENDOR_ID = 0x1050

        /** SoloKey vendor ID for USB devices (pid.codes open source hardware) */
        private const val SOLOKEY_VENDOR_ID = 0x1209

        /** Set of supported FIDO2 security key vendor IDs */
        private val SUPPORTED_VENDOR_IDS = setOf(YUBICO_VENDOR_ID, SOLOKEY_VENDOR_ID)

        /**
         * DeviceFilter that accepts all FIDO2 security keys.
         * The base DeviceFilter class allows all devices by default (returns true).
         * We use this instead of the default YubicoVendorFilter which only accepts Yubico devices.
         */
        private val FIDO2_DEVICE_FILTER = DeviceFilter()
    }
}
