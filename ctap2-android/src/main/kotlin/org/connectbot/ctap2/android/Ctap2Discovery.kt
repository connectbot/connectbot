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

import android.app.Activity
import android.content.Context
import android.hardware.usb.UsbManager
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.android.transport.usb.DeviceFilter
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.android.transport.usb.UsbYubiKeyDevice
import com.yubico.yubikit.core.YubiKeyDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.connectbot.ctap2.android.Fido2ConnectionState
import timber.log.Timber

class Ctap2Discovery(
    private val context: Context,
    private val onUsbDevice: (Ctap2Device, String?) -> Unit,
    private val onNfcDevice: (Ctap2Device) -> Unit,
    private val onDisconnect: () -> Unit,
    private val _connectionState: MutableStateFlow<Fido2ConnectionState>,
) : java.io.Closeable {
    private var nfcActivity: Activity? = null
    private val yubiKitManager = YubiKitManager(context)
    private var usbDiscoveryActive = false
    fun startUsbDiscovery() {
        if (usbDiscoveryActive) {
            Timber.d("USB discovery already active, skipping duplicate start")
            return
        }

        try {
            val usbConfig = UsbConfiguration().setDeviceFilter(FIDO2_DEVICE_FILTER)
            yubiKitManager.startUsbDiscovery(usbConfig) { device ->
                Timber.d("USB FIDO2 device detected: ${device.usbDevice.productName}")
                onUsbDevice(Ctap2Device(device), device.usbDevice.productName)
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
                            onUsbDevice(Ctap2Device(device), usbDevice.productName)
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
            onDisconnect()
        }
    }

    fun startNfcDiscovery(activity: Activity) {
        nfcActivity = activity
        try {
            yubiKitManager.startNfcDiscovery(
                // FIDO2 uses ISO-DEP; NDEF probing can time out before tag discovery.
                NfcConfiguration().skipNdefCheck(true).timeout(30000), // 30 second timeout
                activity,
            ) { device ->
                Timber.d("NFC YubiKey detected")
                onNfcDevice(Ctap2Device(device))
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
        } finally {
            if (nfcActivity === activity) nfcActivity = null
        }
    }

    override fun close() {
        nfcActivity?.let(::stopNfcDiscovery)
        stopUsbDiscovery()
    }

    companion object {
        private const val TAG = "Ctap2Discovery"

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
