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

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.nfc.Tag
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.connectbot.fido2.ctap.Ctap2Client
import org.connectbot.fido2.transport.Ctap2Transport
import org.connectbot.fido2.transport.NfcTransport
import org.connectbot.fido2.transport.UsbHidTransport
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level manager for FIDO2 security key operations.
 *
 * Coordinates:
 * - USB device detection and connection
 * - CTAP2 protocol communication
 * - Credential discovery and management
 * - Signing operations for SSH authentication
 */
@Singleton
class Fido2Manager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectionState = MutableStateFlow<Fido2ConnectionState>(Fido2ConnectionState.Disconnected)
    val connectionState: StateFlow<Fido2ConnectionState> = _connectionState.asStateFlow()

    private var currentTransport: Ctap2Transport? = null
    private var currentClient: Ctap2Client? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    if (granted && device != null) {
                        scope.launch {
                            connectToDevice(device)
                        }
                    } else {
                        _connectionState.value = Fido2ConnectionState.Error("USB permission denied")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    Timber.d("USB device detached: ${device?.productName}")
                    disconnect()
                }
            }
        }
    }

    init {
        registerUsbReceiver()
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    /**
     * Find all connected FIDO2-compatible USB devices.
     */
    fun findConnectedDevices(): List<UsbDevice> {
        return UsbHidTransport.findFidoDevices(usbManager)
    }

    /**
     * Request permission to connect to a USB device.
     */
    fun requestDevicePermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            scope.launch {
                connectToDevice(device)
            }
        } else {
            _connectionState.value = Fido2ConnectionState.Connecting
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            usbManager.requestPermission(device, pendingIntent)
        }
    }

    /**
     * Connect to a USB FIDO2 device.
     */
    private suspend fun connectToDevice(device: UsbDevice) {
        try {
            _connectionState.value = Fido2ConnectionState.Connecting

            val transport = UsbHidTransport.open(usbManager, device)
            if (transport == null) {
                _connectionState.value = Fido2ConnectionState.Error("Failed to open USB device")
                return
            }

            transport.initialize()
            currentTransport = transport
            currentClient = Ctap2Client(transport)

            _connectionState.value = Fido2ConnectionState.Connected(
                transport = "USB",
                deviceName = device.productName
            )

            Timber.i("Connected to FIDO2 device: ${device.productName}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to FIDO2 device")
            _connectionState.value = Fido2ConnectionState.Error(e.message ?: "Connection failed")
        }
    }

    /**
     * Connect to an NFC FIDO2 device.
     *
     * Call this when an NFC tag is discovered via foreground dispatch.
     */
    suspend fun connectToNfcTag(tag: Tag) {
        try {
            // Disconnect any existing connection first
            disconnect()

            _connectionState.value = Fido2ConnectionState.Connecting

            val transport = NfcTransport(tag)
            if (!transport.connect()) {
                _connectionState.value = Fido2ConnectionState.Error("Failed to connect to NFC device")
                return
            }

            currentTransport = transport
            currentClient = Ctap2Client(transport)

            _connectionState.value = Fido2ConnectionState.Connected(
                transport = "NFC",
                deviceName = "Security Key"
            )

            Timber.i("Connected to FIDO2 device via NFC")
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to NFC FIDO2 device")
            _connectionState.value = Fido2ConnectionState.Error(e.message ?: "NFC connection failed")
        }
    }

    /**
     * Disconnect from the current device.
     */
    fun disconnect() {
        currentTransport?.close()
        currentTransport = null
        currentClient = null
        _connectionState.value = Fido2ConnectionState.Disconnected
    }

    /**
     * Get authenticator information.
     */
    suspend fun getAuthenticatorInfo(): Fido2Result<Fido2AuthenticatorInfo> {
        val client = currentClient
            ?: return Fido2Result.Error("No device connected")
        return client.getInfo()
    }

    /**
     * Authenticate with PIN to enable credential management.
     */
    suspend fun authenticateWithPin(pin: String): Fido2Result<Unit> {
        val client = currentClient
            ?: return Fido2Result.Error("No device connected")
        return client.getPinToken(pin)
    }

    /**
     * Discover SSH resident credentials on the connected device.
     *
     * @param pin Optional PIN if not already authenticated
     * @return List of discovered SSH credentials
     */
    suspend fun discoverSshCredentials(pin: String? = null): Fido2Result<List<Fido2Credential>> {
        val client = currentClient
            ?: return Fido2Result.Error("No device connected")

        // Authenticate with PIN if provided
        if (pin != null) {
            val authResult = client.getPinToken(pin)
            if (authResult !is Fido2Result.Success) {
                return when (authResult) {
                    is Fido2Result.PinInvalid -> authResult
                    is Fido2Result.PinLocked -> authResult
                    is Fido2Result.Error -> authResult
                    else -> Fido2Result.Error("PIN authentication failed")
                }
            }
        }

        // Enumerate credentials for SSH relying party
        return client.enumerateCredentials(SSH_RP_ID)
    }

    /**
     * Sign an SSH challenge using a FIDO2 credential.
     *
     * @param credentialId The credential ID to use for signing
     * @param challenge The SSH challenge data to sign
     * @param pin Optional PIN if user verification is required
     * @return The signature result
     */
    suspend fun signSshChallenge(
        credentialId: ByteArray,
        challenge: ByteArray,
        pin: String? = null
    ): Fido2Result<Fido2SignatureResult> {
        val client = currentClient
            ?: return Fido2Result.Error("No device connected")

        // Authenticate with PIN if provided
        if (pin != null) {
            val authResult = client.getPinToken(pin)
            if (authResult !is Fido2Result.Success) {
                return when (authResult) {
                    is Fido2Result.PinInvalid -> authResult
                    is Fido2Result.PinLocked -> authResult
                    is Fido2Result.Error -> authResult
                    else -> Fido2Result.Error("PIN authentication failed")
                }
            }
        }

        // Compute clientDataHash from challenge (for SSH, this is the session ID + data to sign)
        val clientDataHash = sha256(challenge)

        return client.getAssertion(
            rpId = SSH_RP_ID,
            clientDataHash = clientDataHash,
            credentialId = credentialId,
            requireUserVerification = pin != null
        )
    }

    /**
     * Check if a FIDO2 device is currently connected.
     */
    fun isDeviceConnected(): Boolean {
        return currentTransport?.isConnected == true
    }

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "org.connectbot.USB_PERMISSION"

        /** SSH relying party ID used by OpenSSH for FIDO2 keys */
        const val SSH_RP_ID = "ssh:"
    }
}
