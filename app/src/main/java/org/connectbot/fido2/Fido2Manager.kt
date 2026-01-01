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
import android.nfc.Tag
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.fido.ctap.ClientPin
import com.yubico.yubikit.fido.ctap.CredentialManagement
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocol
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV2
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val nfcExecutor = Executors.newSingleThreadExecutor()

    private val _connectionState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Disconnected)
    val connectionState: StateFlow<Fido2ConnectionState> = _connectionState.asStateFlow()

    private var currentDevice: YubiKeyDevice? = null
    private var currentSession: Ctap2Session? = null
    private var pinUvAuthProtocol: PinUvAuthProtocol? = null
    private var pinUvToken: ByteArray? = null

    /**
     * Start USB device discovery.
     * Call this when the activity becomes visible.
     */
    fun startUsbDiscovery() {
        try {
            yubiKitManager.startUsbDiscovery(UsbConfiguration()) { device ->
                Timber.d("USB YubiKey detected: ${device.usbDevice.productName}")
                scope.launch {
                    connectToDevice(device, "USB", device.usbDevice.productName)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start USB discovery")
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

    /**
     * Connect to a YubiKey device and establish a CTAP2 session.
     */
    private suspend fun connectToDevice(device: YubiKeyDevice, transport: String, deviceName: String?) {
        try {
            // Disconnect any existing session
            disconnect()

            _connectionState.value = Fido2ConnectionState.Connecting

            // Open a SmartCard connection and create CTAP2 session
            val session = withContext(Dispatchers.IO) {
                suspendCancellableCoroutine { continuation ->
                    device.requestConnection(SmartCardConnection::class.java) { result ->
                        try {
                            val connection = result.value
                            val ctap2Session = Ctap2Session(connection)
                            continuation.resume(ctap2Session)
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            }

            currentDevice = device
            currentSession = session
            pinUvAuthProtocol = PinUvAuthProtocolV2()

            _connectionState.value = Fido2ConnectionState.Connected(
                transport = transport,
                deviceName = deviceName
            )

            Timber.i("Connected to FIDO2 device via $transport: $deviceName")
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
        try {
            // Create NfcYubiKeyDevice from the tag
            val device = NfcYubiKeyDevice(tag, 30000, nfcExecutor)
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
     */
    suspend fun getAuthenticatorInfo(): Fido2Result<Fido2AuthenticatorInfo> {
        val session = currentSession
            ?: return Fido2Result.Error("No device connected")

        return withContext(Dispatchers.IO) {
            try {
                val info = session.info
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
        val session = currentSession
            ?: return Fido2Result.Error("No device connected")
        val protocol = pinUvAuthProtocol
            ?: return Fido2Result.Error("Protocol not initialized")

        return withContext(Dispatchers.IO) {
            try {
                val clientPin = ClientPin(session, protocol)

                // Get PIN token with credential management permission
                val token = clientPin.getPinToken(
                    pin.toCharArray(),
                    ClientPin.PIN_PERMISSION_CM, // Credential Management permission
                    null // rpId
                )

                pinUvToken = token
                Fido2Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "PIN authentication failed")
                val message = e.message ?: ""

                when {
                    message.contains("PIN_INVALID", ignoreCase = true) ||
                    message.contains("CTAP2_ERR_PIN_INVALID", ignoreCase = true) -> {
                        Fido2Result.PinInvalid(attemptsRemaining = null)
                    }
                    message.contains("PIN_AUTH_BLOCKED", ignoreCase = true) ||
                    message.contains("PIN_BLOCKED", ignoreCase = true) -> {
                        Fido2Result.PinLocked("PIN is locked")
                    }
                    else -> Fido2Result.Error(e.message ?: "PIN authentication failed")
                }
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

        return withContext(Dispatchers.IO) {
            try {
                val credMgmt = CredentialManagement(session, protocol, token)

                // Find SSH relying party
                val rps = credMgmt.enumerateRps()
                val sshRp = rps.find { rpData ->
                    val rpId = rpData.rp?.get("id") as? String
                    rpId == SSH_RP_ID || rpId?.startsWith("ssh:") == true
                }

                if (sshRp == null) {
                    return@withContext Fido2Result.Success(emptyList())
                }

                // Enumerate credentials for SSH RP
                val rpHash = sshRp.rpIdHash ?: sha256((sshRp.rp?.get("id") as String).toByteArray())
                val credentials = credMgmt.enumerateCredentials(rpHash)

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

                Fido2Result.Success(result)
            } catch (e: Exception) {
                Timber.e(e, "Failed to enumerate credentials")
                val message = e.message ?: ""

                when {
                    message.contains("PIN", ignoreCase = true) -> Fido2Result.PinRequired(null)
                    else -> Fido2Result.Error(e.message ?: "Failed to enumerate credentials")
                }
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

        return withContext(Dispatchers.IO) {
            try {
                val clientDataHash = sha256(challenge)

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
                    pinUvToken,
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
    fun isDeviceConnected(): Boolean {
        return currentSession != null
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

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
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
                is Long -> if (key >= 0) co.nstant.`in`.cbor.model.UnsignedInteger(key)
                          else co.nstant.`in`.cbor.model.NegativeInteger(key)
                is Int -> if (key >= 0) co.nstant.`in`.cbor.model.UnsignedInteger(key.toLong())
                          else co.nstant.`in`.cbor.model.NegativeInteger(key.toLong())
                else -> continue
            }

            val cborValue = when (value) {
                is ByteArray -> co.nstant.`in`.cbor.model.ByteString(value)
                is Long -> if (value >= 0) co.nstant.`in`.cbor.model.UnsignedInteger(value)
                           else co.nstant.`in`.cbor.model.NegativeInteger(value)
                is Int -> if (value >= 0) co.nstant.`in`.cbor.model.UnsignedInteger(value.toLong())
                          else co.nstant.`in`.cbor.model.NegativeInteger(value.toLong())
                else -> continue
            }

            cborMap.put(cborKey, cborValue)
        }

        encoder.encode(cborMap)
        return output.toByteArray()
    }

    companion object {
        /** SSH relying party ID used by OpenSSH for FIDO2 keys */
        const val SSH_RP_ID = "ssh:"
    }
}
