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

import android.app.Activity
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.nfc.Tag
import android.util.Log
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.android.transport.usb.DeviceFilter
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.android.transport.usb.UsbYubiKeyDevice
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.fido.FidoConnection
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.fido.ctap.ClientPin
import com.yubico.yubikit.fido.ctap.CredentialManagement
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocol
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV1
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV2
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.Executors
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
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val yubiKitManager = YubiKitManager(context)

    // Single-thread executor for all YubiKey operations - ensures thread safety
    private val sessionExecutor = Executors.newSingleThreadExecutor()
    private val sessionDispatcher = sessionExecutor.asCoroutineDispatcher()

    private val _connectionState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Disconnected)
    val connectionState: StateFlow<Fido2ConnectionState> = _connectionState.asStateFlow()

    // Signal when NFC tag is detected (for connect prompt to detect NFC availability)
    private val _nfcTagDetected = MutableSharedFlow<Tag>(replay = 0, extraBufferCapacity = 1)
    val nfcTagDetected: SharedFlow<Tag> = _nfcTagDetected.asSharedFlow()

    private var currentDevice: YubiKeyDevice? = null
    private var currentSession: Ctap2Session? = null
    private var pinUvAuthProtocol: PinUvAuthProtocol? = null
    private var pinUvToken: ByteArray? = null

    /**
     * Start USB device discovery.
     * Call this when the activity becomes visible.
     * Also checks for already-connected USB devices.
     */
    fun startUsbDiscovery() {
        try {
            val usbConfig = UsbConfiguration().setDeviceFilter(FIDO2_DEVICE_FILTER)
            yubiKitManager.startUsbDiscovery(usbConfig) { device ->
                Timber.d("USB FIDO2 device detected: ${device.usbDevice.productName}")
                scope.launch {
                    connectToDevice(device, "USB", device.usbDevice.productName)
                }
            }

            // Also check for already-connected USB devices
            checkForConnectedUsbDevices()
        } catch (e: Exception) {
            Timber.e(e, "Failed to start USB discovery")
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
                        scope.launch {
                            try {
                                val device = UsbYubiKeyDevice(usbManager, usbDevice)
                                connectToDevice(device, "USB", usbDevice.productName)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to connect to USB device")
                            }
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
        }
    }

    /**
     * Start NFC device discovery.
     * Call this in Activity.onResume().
     */
    fun startNfcDiscovery(activity: Activity) {
        try {
            yubiKitManager.startNfcDiscovery(
                NfcConfiguration().timeout(30000), // 30 second timeout
                activity
            ) { device ->
                Timber.d("NFC YubiKey detected")
                scope.launch {
                    connectToDevice(device, "NFC", "Security Key")
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
            val result = try {
                Log.d(TAG, "USB authenticating with PIN using existing session")
                enumerateCredentialsWithPin(session, protocol, pin)
            } catch (e: Exception) {
                if (shouldRetryUsbWithFreshConnection(e)) {
                    Log.w(TAG, "USB session appears stale, retrying with fresh connection")
                    reconnectAndEnumerateUsbCredentials(pin)
                } else {
                    mapPinOrCredentialError(e)
                }
            }

            // Deliver result via callback
            pendingPin = null
            callback?.invoke(result)
            usbCredentialCallback = null
        }
    }

    /**
     * Enumerate SSH credentials after PIN authentication.
     * Optionally closes the session (for temporary/reconnect sessions).
     */
    private fun enumerateCredentialsWithPin(
        session: Ctap2Session,
        protocol: PinUvAuthProtocol,
        pin: String,
        closeSessionAfter: Boolean = false
    ): Fido2Result<List<Fido2Credential>> {
        try {
            val clientPin = ClientPin(session, protocol)
            val token = clientPin.getPinToken(
                pin.toCharArray(),
                ClientPin.PIN_PERMISSION_CM,
                null
            )
            Log.d(TAG, "USB PIN authentication successful")

            val credMgmt = CredentialManagement(session, protocol, token)
            val rps = credMgmt.enumerateRps()
            Log.d(TAG, "USB found ${rps.size} RPs")

            val sshRp = rps.find { rpData ->
                val rpId = rpData.rp?.get("id") as? String
                rpId == SSH_RP_ID || rpId?.startsWith("ssh:") == true
            }

            if (sshRp == null) {
                Log.d(TAG, "USB no SSH RP found")
                return Fido2Result.Success(emptyList())
            }

            val rpHash = sshRp.rpIdHash ?: sha256((sshRp.rp?.get("id") as String).toByteArray())
            val credentials = credMgmt.enumerateCredentials(rpHash)
            Log.d(TAG, "USB found ${credentials.size} credentials")

            val credentialList = credentials.map { credData ->
                val user = credData.user
                val credentialId = credData.credentialId?.get("id") as? ByteArray
                    ?: throw IllegalStateException("Missing credential ID")

                val publicKey = credData.publicKey

                @Suppress("UNCHECKED_CAST")
                val pubKeyMap = publicKey as? Map<Int, Any>
                val algValue = pubKeyMap?.get(3)
                val algorithm = when (algValue) {
                    -7, -7L -> Fido2Algorithm.ES256
                    -8, -8L -> Fido2Algorithm.EDDSA
                    else -> throw IllegalStateException("Unsupported algorithm: $algValue")
                }

                val publicKeyCose = encodeCoseKey(publicKey ?: emptyMap<Any, Any>())

                Fido2Credential(
                    credentialId = credentialId,
                    rpId = sshRp.rp?.get("id") as? String ?: SSH_RP_ID,
                    userHandle = user?.get("id") as? ByteArray,
                    userName = user?.get("name") as? String,
                    publicKeyCose = publicKeyCose,
                    algorithm = algorithm
                )
            }

            Log.i(TAG, "USB credential enumeration complete: ${credentialList.size} credentials")
            return Fido2Result.Success(credentialList)
        } finally {
            if (closeSessionAfter) {
                try {
                    session.close()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to close temporary USB session")
                }
            }
        }
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
                    val reconnectProtocol = selectPinProtocol(reconnectSession)
                    val result = enumerateCredentialsWithPin(
                        session = reconnectSession,
                        protocol = reconnectProtocol,
                        pin = pin,
                        closeSessionAfter = true
                    )
                    continuation.resume(result)
                } catch (e: Exception) {
                    Log.e(TAG, "USB reconnect operation failed: ${e.message}", e)
                    continuation.resume(mapPinOrCredentialError(e))
                }
            }
        }
    }

    private fun shouldRetryUsbWithFreshConnection(e: Exception): Boolean {
        val message = e.message ?: return false
        return message.contains("Failed to send full packet", ignoreCase = true) ||
            message.contains("broken pipe", ignoreCase = true) ||
            message.contains("connection reset", ignoreCase = true)
    }

    private fun mapPinOrCredentialError(e: Exception): Fido2Result<List<Fido2Credential>> {
        Log.e(TAG, "USB operation failed: ${e.message}", e)
        val message = e.message ?: ""
        return when {
            message.contains("PIN_INVALID", ignoreCase = true) ||
                message.contains("CTAP2_ERR_PIN_INVALID", ignoreCase = true) -> {
                Fido2Result.PinInvalid(attemptsRemaining = null)
            }

            message.contains("PIN_AUTH_BLOCKED", ignoreCase = true) -> {
                Fido2Result.PinLocked("PIN is temporarily blocked. Reinsert the security key and try again.")
            }

            message.contains("PIN_BLOCKED", ignoreCase = true) -> {
                Fido2Result.PinLocked("PIN is locked. Please reset your security key.")
            }

            else -> Fido2Result.Error(e.message ?: "USB operation failed")
        }
    }

    /**
     * Connect to a YubiKey device and establish a CTAP2 session.
     * Uses FidoConnection for USB (CTAPHID protocol) and SmartCardConnection for NFC (APDU).
     *
     * For NFC, all operations (PIN auth + credential enumeration) are done synchronously
     * within the connection callback since NFC connections are transient.
     */
    private suspend fun connectToDevice(device: YubiKeyDevice, transport: String, deviceName: String?) {
        try {
            // Disconnect any existing session
            disconnect()

            _connectionState.value = Fido2ConnectionState.Connecting

            if (transport == "USB") {
                // USB: Like NFC, do ALL operations in the callback to avoid thread issues
                val pin = pendingPin
                val callback = usbCredentialCallback

                Log.d(TAG, "USB: Requesting FidoConnection... pin=${pin != null}, callback=${callback != null}")

                if (pin == null) {
                    // No PIN yet - just connect and get authenticator info to check if PIN is needed
                    val needsPin = suspendCancellableCoroutine { continuation ->
                        device.requestConnection(FidoConnection::class.java) { result ->
                            try {
                                val connection = result.value
                                Log.d(TAG, "USB FidoConnection established on thread ${Thread.currentThread().name}")
                                val session = Ctap2Session(connection)
                                Log.d(TAG, "USB Ctap2Session created, checking if PIN required")

                                val info = session.cachedInfo
                                val pinRequired = info.options?.get("clientPin") == true
                                Log.d(TAG, "USB PIN required: $pinRequired")

                                // Always store the device for retry
                                currentDevice = device

                                // Always store session - we'll reuse it for PIN auth
                                // Closing the session also closes the USB connection,
                                // and some devices (like SoloKey) don't handle reconnection well
                                currentSession = session
                                pinUvAuthProtocol = selectPinProtocol(session)
                                continuation.resume(pinRequired)
                            } catch (e: Exception) {
                                Log.e(TAG, "USB connection failed: ${e.message}", e)
                                continuation.resumeWithException(e)
                            }
                        }
                    }

                    if (needsPin) {
                        _connectionState.value = Fido2ConnectionState.Connected(
                            transport = "USB",
                            deviceName = deviceName
                        )
                        // ViewModel will show PIN prompt and call back with PIN
                    } else {
                        _connectionState.value = Fido2ConnectionState.Connected(
                            transport = "USB",
                            deviceName = deviceName
                        )
                    }
                    return
                }

                // USB with PIN is handled by retryUsbWithPin() using the existing session
                // This branch should not be reached for USB
                Log.w(TAG, "Unexpected: connectToDevice called for USB with PIN set")
                return
            } else {
                // NFC: Do ALL operations synchronously in the callback
                // because the NFC connection is transient
                val pin = pendingPin
                val callback = nfcCredentialCallback

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
                                pin.toCharArray(),
                                ClientPin.PIN_PERMISSION_CM,
                                null
                            )
                            Timber.d("NFC PIN authentication successful")

                            // Enumerate credentials - ALL on the same thread
                            Timber.d("NFC enumerating credentials on thread $threadName")
                            val credMgmt = CredentialManagement(session, protocol, token)

                            val rps = credMgmt.enumerateRps()
                            Timber.d("NFC found ${rps.size} RPs")

                            val sshRp = rps.find { rpData ->
                                val rpId = rpData.rp?.get("id") as? String
                                rpId == SSH_RP_ID || rpId?.startsWith("ssh:") == true
                            }

                            if (sshRp == null) {
                                Timber.d("NFC no SSH RP found")
                                session.close()
                                continuation.resume(Fido2Result.Success(emptyList()))
                                return@requestConnection
                            }

                            val rpHash = sshRp.rpIdHash ?: sha256((sshRp.rp?.get("id") as String).toByteArray())
                            val credentials = credMgmt.enumerateCredentials(rpHash)
                            Timber.d("NFC found ${credentials.size} credentials")

                            val credentialList = credentials.map { credData ->
                                val user = credData.user
                                val credentialId = credData.credentialId?.get("id") as? ByteArray
                                    ?: throw IllegalStateException("Missing credential ID")

                                val publicKey = credData.publicKey

                                @Suppress("UNCHECKED_CAST")
                                val pubKeyMap = publicKey as? Map<Int, Any>
                                val algValue = pubKeyMap?.get(3)
                                val algorithm = when (algValue) {
                                    -7, -7L -> Fido2Algorithm.ES256
                                    -8, -8L -> Fido2Algorithm.EDDSA
                                    else -> throw IllegalStateException("Unsupported algorithm: $algValue")
                                }

                                val publicKeyCose = encodeCoseKey(publicKey ?: emptyMap<Any, Any>())

                                Fido2Credential(
                                    credentialId = credentialId,
                                    rpId = sshRp.rp?.get("id") as? String ?: SSH_RP_ID,
                                    userHandle = user?.get("id") as? ByteArray,
                                    userName = user?.get("name") as? String,
                                    publicKeyCose = publicKeyCose,
                                    algorithm = algorithm
                                )
                            }

                            session.close()
                            Timber.i("NFC credential enumeration complete: ${credentialList.size} credentials")
                            continuation.resume(Fido2Result.Success(credentialList))
                        } catch (e: Exception) {
                            Timber.e(e, "NFC operation failed")
                            val message = e.message ?: ""

                            val error = when {
                                message.contains("PIN_INVALID", ignoreCase = true) ||
                                    message.contains("CTAP2_ERR_PIN_INVALID", ignoreCase = true) -> {
                                    Fido2Result.PinInvalid(attemptsRemaining = null)
                                }

                                message.contains("PIN_AUTH_BLOCKED", ignoreCase = true) -> {
                                    Fido2Result.PinLocked("PIN is temporarily blocked. Reinsert the security key and try again.")
                                }

                                message.contains("PIN_BLOCKED", ignoreCase = true) -> {
                                    Fido2Result.PinLocked("PIN is locked. Please reset your security key.")
                                }

                                else -> Fido2Result.Error(e.message ?: "NFC operation failed")
                            }
                            continuation.resume(error)
                        }
                    }
                }

                // Deliver result via callback and update state
                pendingPin = null
                callback?.invoke(result)
                nfcCredentialCallback = null

                when (result) {
                    is Fido2Result.Success -> {
                        _connectionState.value = Fido2ConnectionState.Connected(
                            transport = "NFC",
                            deviceName = deviceName
                        )
                    }

                    else -> {
                        _connectionState.value = Fido2ConnectionState.Disconnected
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to FIDO2 device")
            _connectionState.value = Fido2ConnectionState.Error(e.message ?: "Connection failed")
        }
    }

    /**
     * Connect to an NFC tag directly.
     * Use this when receiving an NFC intent in the activity.
     */
    suspend fun connectToNfcTag(tag: Tag) {
        // Emit tag detected signal for connect prompt to observe
        _nfcTagDetected.tryEmit(tag)

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
        try {
            // Ctap2Session.close() also closes the underlying connection
            currentSession?.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing session")
        }
        currentDevice = null
        currentSession = null
        pinUvToken = null
        _connectionState.value = Fido2ConnectionState.Disconnected
    }

    /**
     * Get authenticator information.
     * Uses cached info from session construction to avoid NFC communication issues.
     */
    suspend fun getAuthenticatorInfo(): Fido2Result<Fido2AuthenticatorInfo> {
        val session = currentSession
            ?: return Fido2Result.Error("No device connected")

        return withContext(sessionDispatcher) {
            try {
                // Use getCachedInfo() instead of getInfo() to avoid additional NFC communication
                // The info is already retrieved and cached during Ctap2Session construction
                val info = session.cachedInfo
                val options = info.options

                Fido2Result.Success(
                    Fido2AuthenticatorInfo(
                        versions = info.versions?.toList() ?: emptyList(),
                        aaguid = info.aaguid,
                        pinConfigured = options?.get("clientPin") == true,
                        credentialManagementSupported = options?.get("credMgmt") == true ||
                            options?.get("credentialMgmtPreview") == true,
                        residentKeySupported = options?.get("rk") == true,
                        maxCredentialCount = info.maxCredentialCountInList,
                        remainingCredentialCount = info.remainingDiscoverableCredentials
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to get authenticator info")
                Fido2Result.Error(e.message ?: "Failed to get info")
            }
        }
    }

    /**
     * Authenticate with PIN to enable credential management.
     */
    suspend fun authenticateWithPin(pin: String): Fido2Result<Unit> {
        Log.d(TAG, "authenticateWithPin: starting")
        val session = currentSession
            ?: return Fido2Result.Error("No device connected").also {
                Log.e(TAG, "authenticateWithPin: No device connected")
            }
        val protocol = pinUvAuthProtocol
            ?: return Fido2Result.Error("Protocol not initialized").also {
                Log.e(TAG, "authenticateWithPin: Protocol not initialized")
            }

        // Don't use sessionDispatcher - YubiKit connections aren't thread-safe
        // Operations must run on the same dispatcher as the caller
        return try {
            Log.d(TAG, "authenticateWithPin: creating ClientPin on thread ${Thread.currentThread().name}")
            val clientPin = ClientPin(session, protocol)

            // Get PIN token with credential management permission
            Log.d(TAG, "authenticateWithPin: getting PIN token")
            val token = clientPin.getPinToken(
                pin.toCharArray(),
                ClientPin.PIN_PERMISSION_CM, // Credential Management permission
                null // rpId
            )

            pinUvToken = token
            Log.d(TAG, "authenticateWithPin: success")
            Fido2Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "PIN authentication failed: ${e.message}", e)
            val message = e.message ?: ""

            when {
                message.contains("PIN_INVALID", ignoreCase = true) ||
                    message.contains("CTAP2_ERR_PIN_INVALID", ignoreCase = true) -> {
                    Fido2Result.PinInvalid(attemptsRemaining = null)
                }

                message.contains("PIN_AUTH_BLOCKED", ignoreCase = true) -> {
                    Fido2Result.PinLocked("PIN is temporarily blocked. Reinsert the security key and try again.")
                }

                message.contains("PIN_BLOCKED", ignoreCase = true) -> {
                    Fido2Result.PinLocked("PIN is locked. Please reset your security key.")
                }

                else -> Fido2Result.Error(e.message ?: "PIN authentication failed")
            }
        }
    }

    /**
     * Discover SSH resident credentials on the connected device.
     */
    suspend fun discoverSshCredentials(pin: String? = null): Fido2Result<List<Fido2Credential>> {
        val session = currentSession
            ?: return Fido2Result.Error("No device connected")
        val protocol = pinUvAuthProtocol
            ?: return Fido2Result.Error("Protocol not initialized")

        // Authenticate with PIN if provided
        if (pin != null) {
            val authResult = authenticateWithPin(pin)
            if (authResult !is Fido2Result.Success) {
                @Suppress("UNCHECKED_CAST")
                return authResult as Fido2Result<List<Fido2Credential>>
            }
        }

        val token = pinUvToken
            ?: return Fido2Result.PinRequired(attemptsRemaining = null)

        // Don't use sessionDispatcher - YubiKit connections aren't thread-safe
        return try {
            Log.d(TAG, "discoverSshCredentials: enumerating RPs on thread ${Thread.currentThread().name}")
            val credMgmt = CredentialManagement(session, protocol, token)

            // Find SSH relying party
            val rps = credMgmt.enumerateRps()
            Log.d(TAG, "discoverSshCredentials: found ${rps.size} RPs")
            val sshRp = rps.find { rpData ->
                val rpId = rpData.rp?.get("id") as? String
                rpId == SSH_RP_ID || rpId?.startsWith("ssh:") == true
            }

            if (sshRp == null) {
                Log.d(TAG, "discoverSshCredentials: no SSH RP found")
                return Fido2Result.Success(emptyList())
            }

            // Enumerate credentials for SSH RP
            val rpHash = sshRp.rpIdHash ?: sha256((sshRp.rp?.get("id") as String).toByteArray())
            val credentials = credMgmt.enumerateCredentials(rpHash)
            Log.d(TAG, "discoverSshCredentials: found ${credentials.size} credentials")

            val result = credentials.map { credData ->
                val user = credData.user
                val credentialId = credData.credentialId?.get("id") as? ByteArray
                    ?: throw IllegalStateException("Missing credential ID")

                // Get public key from credential data
                val publicKey = credData.publicKey

                // Determine algorithm from public key (key 3 is the algorithm)
                // The COSE key map uses integer keys: 3 is the algorithm identifier
                @Suppress("UNCHECKED_CAST")
                val pubKeyMap = publicKey as? Map<Int, Any>
                val algValue = pubKeyMap?.get(3)
                val algorithm = when (algValue) {
                    -7, -7L -> Fido2Algorithm.ES256
                    -8, -8L -> Fido2Algorithm.EDDSA
                    else -> throw IllegalStateException("Unsupported algorithm: $algValue")
                }

                // Encode public key to COSE format
                val publicKeyCose = encodeCoseKey(publicKey ?: emptyMap<Any, Any>())

                Fido2Credential(
                    credentialId = credentialId,
                    rpId = sshRp.rp?.get("id") as? String ?: SSH_RP_ID,
                    userHandle = user?.get("id") as? ByteArray,
                    userName = user?.get("name") as? String,
                    publicKeyCose = publicKeyCose,
                    algorithm = algorithm
                )
            }

            Log.d(TAG, "discoverSshCredentials: success with ${result.size} credentials")
            Fido2Result.Success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enumerate credentials: ${e.message}", e)
            val message = e.message ?: ""

            when {
                message.contains("PIN", ignoreCase = true) -> Fido2Result.PinRequired(null)
                else -> Fido2Result.Error(e.message ?: "Failed to enumerate credentials")
            }
        }
    }

    /**
     * Sign an SSH challenge using a FIDO2 credential.
     */
    suspend fun signSshChallenge(
        credentialId: ByteArray,
        challenge: ByteArray,
        pin: String? = null
    ): Fido2Result<Fido2SignatureResult> {
        val session = currentSession
            ?: return Fido2Result.Error("No device connected")
        val protocol = pinUvAuthProtocol
            ?: return Fido2Result.Error("Protocol not initialized")

        // Authenticate with PIN if provided
        if (pin != null) {
            val authResult = authenticateWithPin(pin)
            if (authResult !is Fido2Result.Success) {
                @Suppress("UNCHECKED_CAST")
                return authResult as Fido2Result<Fido2SignatureResult>
            }
        }

        val token = pinUvToken
            ?: return Fido2Result.PinRequired(attemptsRemaining = null)

        return withContext(sessionDispatcher) {
            try {
                val clientDataHash = sha256(challenge)

                // Calculate pinUvAuth = HMAC(pinToken, clientDataHash)
                // For getAssertion, the message is just clientDataHash (no 0x02 prefix)
                val pinUvAuth = protocol.authenticate(token, clientDataHash)

                // Build allow list with the specific credential
                val allowList = listOf(
                    mapOf(
                        "type" to "public-key",
                        "id" to credentialId
                    )
                )

                // Get assertion
                val assertions = session.getAssertions(
                    SSH_RP_ID,
                    clientDataHash,
                    allowList,
                    null, // extensions
                    null, // options
                    pinUvAuth,
                    protocol.version,
                    null // CommandState
                )

                if (assertions.isEmpty()) {
                    return@withContext Fido2Result.Error("No assertion returned")
                }

                val assertion = assertions[0]
                val authData = assertion.authenticatorData
                val signature = assertion.signature

                // Parse counter from authenticator data (bytes 33-36 are the counter)
                val counter = if (authData.size >= 37) {
                    ((authData[33].toInt() and 0xFF) shl 24) or
                        ((authData[34].toInt() and 0xFF) shl 16) or
                        ((authData[35].toInt() and 0xFF) shl 8) or
                        (authData[36].toInt() and 0xFF)
                } else {
                    0
                }

                // Parse flags byte (byte 32)
                val flags = if (authData.size >= 33) authData[32] else 0
                val userPresent = (flags.toInt() and 0x01) != 0
                val userVerified = (flags.toInt() and 0x04) != 0

                Fido2Result.Success(
                    Fido2SignatureResult(
                        authenticatorData = authData,
                        signature = signature,
                        userPresenceVerified = userPresent,
                        userVerified = userVerified,
                        counter = counter
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to get assertion")
                Fido2Result.Error(e.message ?: "Failed to sign challenge")
            }
        }
    }

    /**
     * Check if a FIDO2 device is currently connected.
     */
    fun isDeviceConnected(): Boolean = currentDevice != null

    // ==================== SSH Authentication Support ====================

    // Callback for SSH signing operations
    private var sshSigningCallback: ((Fido2Result<Fido2SignatureResult>) -> Unit)? = null
    private var sshCredentialId: ByteArray? = null
    private var sshChallenge: ByteArray? = null
    private var sshPin: String? = null

    // State flow to signal when waiting for NFC tap for SSH signing
    private val _waitingForNfcSigning = MutableStateFlow(false)
    val waitingForNfcSigning: StateFlow<Boolean> = _waitingForNfcSigning.asStateFlow()

    /**
     * Set up for SSH signing operation.
     * Call this before connecting to prepare for signing.
     */
    fun prepareSshSigning(
        credentialId: ByteArray,
        challenge: ByteArray,
        callback: (Fido2Result<Fido2SignatureResult>) -> Unit
    ) {
        sshCredentialId = credentialId
        sshChallenge = challenge
        sshSigningCallback = callback
    }

    /**
     * Request NFC tap for SSH signing.
     * UI should observe waitingForNfcSigning and start NFC discovery when true.
     */
    fun requestNfcSigning(pin: String) {
        sshPin = pin
        _waitingForNfcSigning.value = true
    }

    /**
     * Cancel pending NFC signing request.
     */
    fun cancelNfcSigning() {
        _waitingForNfcSigning.value = false
        sshPin = null
        val callback = sshSigningCallback
        sshSigningCallback = null
        callback?.invoke(Fido2Result.Error("NFC signing cancelled"))
    }

    /**
     * Handle NFC tag detection for SSH signing.
     * Call this when an NFC tag is detected while waitingForNfcSigning is true.
     */
    suspend fun handleNfcTagForSigning(tag: Tag) {
        val credId = sshCredentialId ?: return
        val challenge = sshChallenge ?: return
        val pin = sshPin ?: return
        val callback = sshSigningCallback ?: return

        _waitingForNfcSigning.value = false

        val result = connectAndSignNfc(tag, pin, credId, challenge)

        // Clear state
        sshCredentialId = null
        sshChallenge = null
        sshPin = null
        sshSigningCallback = null

        callback(result)
    }

    /**
     * Connect to USB device and perform SSH signing.
     * This handles the full flow: connect -> PIN auth -> sign -> return result via callback.
     */
    fun connectAndSignUsb(pin: String) {
        val credId = sshCredentialId ?: return
        val challenge = sshChallenge ?: return
        val callback = sshSigningCallback ?: return

        val device = currentDevice
        if (device == null) {
            callback(Fido2Result.Error("No device connected"))
            return
        }

        scope.launch {
            val result = suspendCancellableCoroutine<Fido2Result<Fido2SignatureResult>> { continuation ->
                device.requestConnection(FidoConnection::class.java) { connectionResult ->
                    val threadName = Thread.currentThread().name
                    Log.d(TAG, "SSH signing USB callback on thread $threadName")

                    try {
                        val connection = connectionResult.value
                        Log.d(TAG, "USB FidoConnection established for signing")

                        val session = Ctap2Session(connection)
                        Log.d(TAG, "Ctap2Session created for signing")

                        val protocol = selectPinProtocol(session)

                        // Authenticate with PIN
                        Log.d(TAG, "Authenticating with PIN for SSH signing")
                        val clientPin = ClientPin(session, protocol)
                        val token = clientPin.getPinToken(
                            pin.toCharArray(),
                            ClientPin.PIN_PERMISSION_GA, // Get Assertion permission for signing
                            SSH_RP_ID // Specify the RP ID
                        )
                        Log.d(TAG, "PIN authentication successful for SSH signing")

                        // Perform signing
                        Log.d(TAG, "Getting assertion for SSH signing")
                        val clientDataHash = sha256(challenge)

                        // Calculate pinUvAuth = HMAC(pinToken, clientDataHash)
                        // For getAssertion, the message is just clientDataHash (no 0x02 prefix)
                        val pinUvAuth = protocol.authenticate(token, clientDataHash)

                        val allowList = listOf(
                            mapOf(
                                "type" to "public-key",
                                "id" to credId
                            )
                        )

                        val assertions = session.getAssertions(
                            SSH_RP_ID,
                            clientDataHash,
                            allowList,
                            null, // extensions
                            null, // options
                            pinUvAuth,
                            protocol.version,
                            null // CommandState
                        )

                        if (assertions.isEmpty()) {
                            session.close()
                            continuation.resume(Fido2Result.Error("No assertion returned"))
                            return@requestConnection
                        }

                        val assertion = assertions[0]
                        val authData = assertion.authenticatorData
                        val signature = assertion.signature

                        // Parse counter from authenticator data (bytes 33-36 are the counter)
                        val counter = if (authData.size >= 37) {
                            ((authData[33].toInt() and 0xFF) shl 24) or
                                ((authData[34].toInt() and 0xFF) shl 16) or
                                ((authData[35].toInt() and 0xFF) shl 8) or
                                (authData[36].toInt() and 0xFF)
                        } else {
                            0
                        }

                        // Parse flags byte (byte 32)
                        val flags = if (authData.size >= 33) authData[32] else 0

                        session.close()
                        Log.i(TAG, "SSH signing successful")

                        continuation.resume(
                            Fido2Result.Success(
                                Fido2SignatureResult(
                                    authenticatorData = authData,
                                    signature = signature,
                                    userPresenceVerified = (flags.toInt() and 0x01) != 0,
                                    userVerified = (flags.toInt() and 0x04) != 0,
                                    counter = counter
                                )
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "SSH signing failed: ${e.message}", e)
                        val message = e.message ?: ""

                        val error = when {
                            message.contains("PIN_INVALID", ignoreCase = true) ||
                                message.contains("CTAP2_ERR_PIN_INVALID", ignoreCase = true) -> {
                                Fido2Result.PinInvalid(attemptsRemaining = null)
                            }

                            message.contains("PIN_AUTH_BLOCKED", ignoreCase = true) -> {
                                Fido2Result.PinLocked("PIN is temporarily blocked. Reinsert the security key and try again.")
                            }

                            message.contains("PIN_BLOCKED", ignoreCase = true) -> {
                                Fido2Result.PinLocked("PIN is locked. Please reset your security key.")
                            }

                            message.contains("NO_CREDENTIALS", ignoreCase = true) -> {
                                Fido2Result.Error("Credential not found on security key")
                            }

                            else -> Fido2Result.Error(e.message ?: "SSH signing failed")
                        }
                        continuation.resume(error)
                    }
                }
            }

            // Clear state and deliver result
            sshCredentialId = null
            sshChallenge = null
            sshSigningCallback = null
            callback(result)
        }
    }

    /**
     * Connect to NFC device and perform SSH signing.
     * This handles the full flow: connect -> PIN auth -> sign in a single NFC tap.
     */
    suspend fun connectAndSignNfc(tag: Tag, pin: String, credentialId: ByteArray, challenge: ByteArray): Fido2Result<Fido2SignatureResult> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val device = NfcYubiKeyDevice(tag, 30000, sessionExecutor)

                device.requestConnection(SmartCardConnection::class.java) { connectionResult ->
                    val threadName = Thread.currentThread().name
                    Log.d(TAG, "SSH signing NFC callback on thread $threadName")

                    try {
                        val connection = connectionResult.value
                        Log.d(TAG, "NFC SmartCardConnection established for signing")

                        val session = Ctap2Session(connection)
                        Log.d(TAG, "Ctap2Session created for NFC signing")

                        val protocol = selectPinProtocol(session)

                        // Authenticate with PIN
                        Log.d(TAG, "Authenticating with PIN for NFC SSH signing")
                        val clientPin = ClientPin(session, protocol)
                        val token = clientPin.getPinToken(
                            pin.toCharArray(),
                            ClientPin.PIN_PERMISSION_GA,
                            SSH_RP_ID
                        )
                        Log.d(TAG, "NFC PIN authentication successful")

                        // Perform signing
                        Log.d(TAG, "Getting assertion for NFC SSH signing")
                        val clientDataHash = sha256(challenge)

                        // Calculate pinUvAuth = HMAC(pinToken, clientDataHash)
                        // For getAssertion, the message is just clientDataHash (no 0x02 prefix)
                        val pinUvAuth = protocol.authenticate(token, clientDataHash)

                        val allowList = listOf(
                            mapOf(
                                "type" to "public-key",
                                "id" to credentialId
                            )
                        )

                        val assertions = session.getAssertions(
                            SSH_RP_ID,
                            clientDataHash,
                            allowList,
                            null,
                            null,
                            pinUvAuth,
                            protocol.version,
                            null
                        )

                        if (assertions.isEmpty()) {
                            session.close()
                            continuation.resume(Fido2Result.Error("No assertion returned"))
                            return@requestConnection
                        }

                        val assertion = assertions[0]
                        val authData = assertion.authenticatorData
                        val signature = assertion.signature

                        val counter = if (authData.size >= 37) {
                            ((authData[33].toInt() and 0xFF) shl 24) or
                                ((authData[34].toInt() and 0xFF) shl 16) or
                                ((authData[35].toInt() and 0xFF) shl 8) or
                                (authData[36].toInt() and 0xFF)
                        } else {
                            0
                        }

                        val flags = if (authData.size >= 33) authData[32] else 0

                        session.close()
                        Log.i(TAG, "NFC SSH signing successful")

                        continuation.resume(
                            Fido2Result.Success(
                                Fido2SignatureResult(
                                    authenticatorData = authData,
                                    signature = signature,
                                    userPresenceVerified = (flags.toInt() and 0x01) != 0,
                                    userVerified = (flags.toInt() and 0x04) != 0,
                                    counter = counter
                                )
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "NFC SSH signing failed: ${e.message}", e)
                        val message = e.message ?: ""

                        val error = when {
                            message.contains("PIN_INVALID", ignoreCase = true) ||
                                message.contains("CTAP2_ERR_PIN_INVALID", ignoreCase = true) -> {
                                Fido2Result.PinInvalid(attemptsRemaining = null)
                            }

                            message.contains("PIN_AUTH_BLOCKED", ignoreCase = true) -> {
                                Fido2Result.PinLocked("PIN is temporarily blocked. Reinsert the security key and try again.")
                            }

                            message.contains("PIN_BLOCKED", ignoreCase = true) -> {
                                Fido2Result.PinLocked("PIN is locked. Please reset your security key.")
                            }

                            message.contains("NO_CREDENTIALS", ignoreCase = true) -> {
                                Fido2Result.Error("Credential not found on security key")
                            }

                            else -> Fido2Result.Error(e.message ?: "NFC SSH signing failed")
                        }
                        continuation.resume(error)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to NFC tag for signing: ${e.message}", e)
                continuation.resume(Fido2Result.Error(e.message ?: "NFC connection failed"))
            }
        }
    }

    /**
     * Find connected USB devices (for backwards compatibility).
     */
    fun findConnectedDevices(): List<UsbDevice> {
        // YubiKitManager handles discovery via callbacks, so we return empty list
        // The actual device detection happens via startUsbDiscovery
        return emptyList()
    }

    /**
     * Request permission for a USB device (for backwards compatibility).
     */
    fun requestDevicePermission(device: UsbDevice) {
        // YubiKitManager handles permissions internally
        // Start USB discovery to detect the device
        startUsbDiscovery()
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /**
     * Select the appropriate PIN/UV auth protocol based on authenticator support.
     * SoloKey1 and other older authenticators only support protocol V1,
     * while newer authenticators (YubiKey 5+) support V2.
     */
    private fun selectPinProtocol(session: Ctap2Session): PinUvAuthProtocol {
        val supportedProtocols = session.cachedInfo.pinUvAuthProtocols
        return if (supportedProtocols != null && 2 in supportedProtocols) {
            PinUvAuthProtocolV2()
        } else {
            PinUvAuthProtocolV1()
        }
    }

    /**
     * Encode a public key map to CBOR bytes.
     */
    private fun encodeCoseKey(publicKey: Map<*, *>): ByteArray {
        // Convert the map to CBOR format
        val output = java.io.ByteArrayOutputStream()
        val encoder = co.nstant.`in`.cbor.CborEncoder(output)

        val cborMap = co.nstant.`in`.cbor.model.Map()
        for ((key, value) in publicKey) {
            val cborKey = when (key) {
                is Long -> if (key >= 0) {
                    co.nstant.`in`.cbor.model.UnsignedInteger(key)
                } else {
                    co.nstant.`in`.cbor.model.NegativeInteger(key)
                }

                is Int -> if (key >= 0) {
                    co.nstant.`in`.cbor.model.UnsignedInteger(key.toLong())
                } else {
                    co.nstant.`in`.cbor.model.NegativeInteger(key.toLong())
                }

                else -> continue
            }

            val cborValue = when (value) {
                is ByteArray -> co.nstant.`in`.cbor.model.ByteString(value)

                is Long -> if (value >= 0) {
                    co.nstant.`in`.cbor.model.UnsignedInteger(value)
                } else {
                    co.nstant.`in`.cbor.model.NegativeInteger(value)
                }

                is Int -> if (value >= 0) {
                    co.nstant.`in`.cbor.model.UnsignedInteger(value.toLong())
                } else {
                    co.nstant.`in`.cbor.model.NegativeInteger(value.toLong())
                }

                else -> continue
            }

            cborMap.put(cborKey, cborValue)
        }

        encoder.encode(cborMap)
        return output.toByteArray()
    }

    companion object {
        private const val TAG = "Fido2Manager"

        /** SSH relying party ID used by OpenSSH for FIDO2 keys */
        const val SSH_RP_ID = "ssh:"

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
