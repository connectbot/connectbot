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

package org.connectbot.fido2

import android.app.Activity
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.nfc.Tag
import android.nfc.TagLostException
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.android.transport.usb.UsbYubiKeyDevice
import com.yubico.yubikit.core.YubiKeyConnection
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.util.Callback
import com.yubico.yubikit.fido.ctap.ClientPin
import com.yubico.yubikit.fido.ctap.CredentialManagement
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV1
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.R
import org.connectbot.data.entity.Fido2Transport
import org.connectbot.di.CoroutineDispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import org.mockito.stubbing.Answer
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.concurrent.ExecutorService
import kotlin.coroutines.CoroutineContext
import com.yubico.yubikit.core.util.Result as DeviceResult

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class Fido2ManagerTest {
    private val dispatcher = StandardTestDispatcher()
    private val onIo = ThreadLocal<Boolean>()
    private val ioDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatcher.dispatch(context) {
                onIo.set(true)
                try {
                    block.run()
                } finally {
                    onIo.remove()
                }
            }
        }
    }
    private var pinTokenObserver: (() -> Unit)? = null
    private val signedRpIds = mutableListOf<String>()
    private val resources = mutableListOf<AutoCloseable>()
    private lateinit var manager: Fido2Manager
    private lateinit var usbDevice: UsbYubiKeyDevice
    private lateinit var nfcDevice: NfcYubiKeyDevice
    private lateinit var info: Ctap2Session.InfoData
    private lateinit var session: Ctap2Session
    private lateinit var usbManager: UsbManager
    private var usbCallback: Callback<UsbYubiKeyDevice>? = null
    private var nfcCallback: Callback<NfcYubiKeyDevice>? = null
    private var nfcConfiguration: NfcConfiguration? = null
    private var delayNfcConnection = false
    private val pendingNfcConnections = mutableListOf<() -> Unit>()
    private var pinError: Exception? = null
    private val pinTokenErrors = mutableListOf<Exception?>()
    private var retryCount = 0
    private var powerCycle: Boolean? = null
    private var probeFailures = 0
    private var discoveryError = false
    private var infoError: Exception? = null
    private var credentialError: Exception? = null
    private var connectionError: IOException? = null
    private var connectionRequestError: Exception? = null
    private var closeError = false
    private var versions = listOf(2, 1)
    private var options = mapOf<String, Any>("clientPin" to true, "credMgmt" to true, "rk" to true)
    private var assertions = emptyList<Ctap2Session.AssertionData>()
    private var rps = emptyList<CredentialManagement.RpData>()
    private var creds = emptyList<CredentialManagement.CredentialData>()

    private fun attach(name: String, value: Any?) {
        Fido2Manager::class.java.getDeclaredField(name).apply { isAccessible = true }.set(manager, value)
    }

    private val sessionAnswer = Answer<Any?> { call ->
        when (call.method.name) {
            "getCachedInfo" -> {
                infoError?.let { throw it }
                info
            }

            "getAssertions" -> {
                signedRpIds.add(call.getArgument(0))
                assertions
            }

            "close" -> {
                if (closeError) throw IOException("close failed")
                null
            }

            else -> Answers.RETURNS_DEFAULTS.answer(call)
        }
    }
    private val deviceAnswer = Answer<Any?> { call ->
        when (call.method.name) {
            "requestConnection" -> {
                connectionRequestError?.let { throw it }
                @Suppress("UNCHECKED_CAST")
                val type = call.getArgument<Class<*>>(0) as Class<YubiKeyConnection>
                val callback = call.getArgument<Callback<DeviceResult<YubiKeyConnection, IOException>>>(1)
                val result = connectionError?.let { DeviceResult.failure<YubiKeyConnection, IOException>(it) }
                    ?: DeviceResult.success<YubiKeyConnection, IOException>(mock(type))
                if (delayNfcConnection) {
                    pendingNfcConnections.add { callback.invoke(result) }
                } else {
                    callback.invoke(result)
                }
                null
            }

            "getUsbDevice" -> mock(UsbDevice::class.java)

            "getTag" -> mock(Tag::class.java)

            else -> Answers.RETURNS_DEFAULTS.answer(call)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        info = mock(
            Ctap2Session.InfoData::class.java,
            Answer { call ->
                when (call.method.name) {
                    "getOptions" -> options
                    "getVersions" -> listOf("FIDO_2_0")
                    "getAaguid" -> ByteArray(16)
                    "getPinUvAuthProtocols" -> versions
                    else -> Answers.RETURNS_DEFAULTS.answer(call)
                }
            },
        )
        session = mock(Ctap2Session::class.java, sessionAnswer)
        resources.add(mockConstruction(Ctap2Session::class.java, withSettings().defaultAnswer(sessionAnswer)))
        resources.add(
            mockConstruction(
                ClientPin::class.java,
                withSettings().defaultAnswer(
                    Answer { call ->
                        when (call.method.name) {
                            "getPinToken" -> {
                                pinTokenObserver?.invoke()
                                val failure = if (pinTokenErrors.isNotEmpty()) pinTokenErrors.removeAt(0) else pinError
                                failure?.let { throw it }
                                ByteArray(32) { 1 }
                            }

                            "getPinRetries" -> {
                                if (probeFailures > 0) {
                                    probeFailures--
                                    throw IOException("probe unavailable")
                                }
                                mock(
                                    ClientPin.PinRetries::class.java,
                                    Answer { probe ->
                                        when (probe.method.name) {
                                            "getCount" -> retryCount
                                            "getPowerCycleState" -> powerCycle
                                            else -> Answers.RETURNS_DEFAULTS.answer(probe)
                                        }
                                    },
                                )
                            }

                            else -> Answers.RETURNS_DEFAULTS.answer(call)
                        }
                    },
                ),
            ),
        )
        resources.add(
            mockConstruction(
                CredentialManagement::class.java,
                withSettings().defaultAnswer(
                    Answer { call ->
                        credentialError?.let { throw it }
                        when (call.method.name) {
                            "enumerateRps" -> rps
                            "enumerateCredentials" -> creds
                            else -> Answers.RETURNS_DEFAULTS.answer(call)
                        }
                    },
                ),
            ),
        )
        resources.add(
            mockConstruction(
                YubiKitManager::class.java,
                withSettings().defaultAnswer(
                    Answer { call ->
                        if (discoveryError) throw IllegalStateException("discovery unavailable")
                        when (call.method.name) {
                            "startUsbDiscovery" -> {
                                usbCallback = call.getArgument(1)
                                null
                            }

                            "startNfcDiscovery" -> {
                                nfcConfiguration = call.getArgument(0)
                                nfcCallback = call.getArgument(2)
                                null
                            }

                            else -> Answers.RETURNS_DEFAULTS.answer(call)
                        }
                    },
                ),
            ),
        )
        resources.add(mockConstruction(NfcYubiKeyDevice::class.java, withSettings().defaultAnswer(deviceAnswer)))
        resources.add(mockConstruction(UsbYubiKeyDevice::class.java, withSettings().defaultAnswer(deviceAnswer)))
        usbDevice = mock(UsbYubiKeyDevice::class.java, deviceAnswer)
        nfcDevice = mock(NfcYubiKeyDevice::class.java, deviceAnswer)
        usbManager = mock(UsbManager::class.java)
        `when`(usbManager.deviceList).thenReturn(hashMapOf())
        val context = mock(Context::class.java)
        `when`(context.getString(R.string.fido2_usb_unavailable)).thenReturn(
            "USB security key is unavailable. Reconnect it, allow USB access, and try again.",
        )
        `when`(context.getSystemService(Context.USB_SERVICE)).thenReturn(usbManager)
        manager = Fido2Manager(context, CoroutineDispatchers(dispatcher, ioDispatcher, dispatcher))
    }

    @After
    fun tearDown() {
        manager.disconnect()
        val scope = Fido2Manager::class.java.getDeclaredField("scope").apply { isAccessible = true }.get(manager) as CoroutineScope
        scope.cancel()
        val executor = Fido2Manager::class.java.getDeclaredField("sessionExecutor").apply { isAccessible = true }.get(manager) as ExecutorService
        executor.shutdownNow()
        resources.asReversed().forEach { it.close() }
        Dispatchers.resetMain()
    }

    private fun connected() {
        attach("currentSession", session)
        attach("currentDevice", usbDevice)
        attach("pinUvAuthProtocol", PinUvAuthProtocolV1())
    }

    private fun deviceConnected(): Boolean = Fido2Manager::class.java.getDeclaredField("currentDevice").run {
        isAccessible = true
        get(manager) != null
    }

    private fun assertion(): Ctap2Session.AssertionData {
        val result = mock(Ctap2Session.AssertionData::class.java)
        `when`(result.authenticatorData).thenReturn(
            ByteArray(37).apply {
                this[32] = 5
                this[36] = 2
            },
        )
        `when`(result.signature).thenReturn(ByteArray(64))
        return result
    }

    @Test
    fun usbImportRunsOnIoAndSerializesRetries() = runTest(dispatcher) {
        connected()
        var calls = 0
        pinTokenObserver = {
            assertEquals("PIN authentication must use the injected IO dispatcher", true, onIo.get())
            calls++
        }
        // Suspend a stale-session reconnect while another enumeration is submitted.
        pinError = IOException("broken pipe")
        delayNfcConnection = true
        manager.setPendingPin("1234")
        manager.setUsbCredentialCallback {}
        manager.retryUsbWithPin()
        runCurrent()
        assertEquals(1, calls)
        manager.retryUsbWithPin()
        runCurrent()
        assertEquals("Second operation must wait for the reconnect", 1, calls)
        pinError = null
        // YubiKit invokes callbacks on its own worker; model that on injected IO.
        val completion = async(ioDispatcher) { pendingNfcConnections.removeAt(0).invoke() }
        completion.await()
        advanceUntilIdle()
        assertEquals(3, calls)
    }

    @Test
    fun concurrentNfcSignersCompleteInOrderWithoutOverwritingRequests() = runTest(dispatcher) {
        assertions = listOf(assertion())
        val ownerA = Any()
        val ownerB = Any()
        val first = async { manager.signSshChallenge(byteArrayOf(1), byteArrayOf(2), "ssh:a", "1234", Fido2Transport.NFC, ownerA) }
        runCurrent()
        val requestA = manager.signingRequest.value
        val second = async { manager.signSshChallenge(byteArrayOf(3), byteArrayOf(4), "ssh:b", "5678", Fido2Transport.NFC, ownerB) }
        runCurrent()
        assertSame(ownerA, manager.signingRequest.value?.owner)
        assertSame(requestA, manager.signingRequest.value)
        manager.handleNfcTagForSigning(mock(Tag::class.java))
        assertTrue(first.await() is Fido2Result.Success)
        runCurrent()
        assertSame(ownerB, manager.signingRequest.value?.owner)
        manager.handleNfcTagForSigning(mock(Tag::class.java))
        assertTrue(second.await() is Fido2Result.Success)
        assertEquals(listOf("ssh:a", "ssh:b"), signedRpIds)
        assertFalse(manager.waitingForNfcSigning.value)
    }

    @Test
    fun cancellingQueuedSigningDoesNotCancelActiveRequest() = runTest(dispatcher) {
        assertions = listOf(assertion())
        val first = async { manager.signSshChallenge(byteArrayOf(1), byteArrayOf(2), "ssh:a", "1234", Fido2Transport.NFC) }
        runCurrent()
        val request = manager.signingRequest.value
        val queued = async { manager.signSshChallenge(byteArrayOf(3), byteArrayOf(4), "ssh:b", "1234", Fido2Transport.USB) }
        runCurrent()
        queued.cancel()
        runCurrent()
        assertSame(request, manager.signingRequest.value)
        assertTrue(manager.waitingForNfcSigning.value)
        manager.handleNfcTagForSigning(mock(Tag::class.java))
        assertTrue(first.await() is Fido2Result.Success)
        assertEquals(listOf("ssh:a"), signedRpIds)
    }

    @Test
    fun cancelledUsbSignerCannotClearOrCompleteItsNfcSuccessor() = runTest(dispatcher) {
        connected()
        assertions = listOf(assertion())
        delayNfcConnection = true
        val first = async { manager.signSshChallenge(byteArrayOf(1), byteArrayOf(2), "ssh:a", "1234", Fido2Transport.USB) }
        runCurrent()
        val requestA = manager.signingRequest.value
        val second = async { manager.signSshChallenge(byteArrayOf(3), byteArrayOf(4), "ssh:b", "1234", Fido2Transport.NFC) }
        runCurrent()
        first.cancel()
        runCurrent()
        val requestB = manager.signingRequest.value
        assertTrue(requestB != null && requestB !== requestA)
        manager.cancelNfcSigning(requestA)
        assertSame(requestB, manager.signingRequest.value)
        pendingNfcConnections.removeAt(0).invoke()
        delayNfcConnection = false
        runCurrent()
        assertSame(requestB, manager.signingRequest.value)
        assertFalse(second.isCompleted)
        manager.handleNfcTagForSigning(mock(Tag::class.java))
        assertTrue(second.await() is Fido2Result.Success)
    }

    @Test
    fun abandoningNfcImportClearsPinAndCallbacksBeforeUsbDiscovery() = runTest(dispatcher) {
        manager.setPendingPin("1234")
        manager.setNfcCredentialCallback { error("Abandoned callback invoked") }
        manager.setUsbCredentialCallback { error("Abandoned callback invoked") }
        manager.cancelImport()
        for (field in listOf("pendingPin", "nfcCredentialCallback", "usbCredentialCallback")) {
            assertEquals(null, Fido2Manager::class.java.getDeclaredField(field).apply { isAccessible = true }.get(manager))
        }
        manager.startUsbDiscovery()
        usbCallback!!.invoke(usbDevice)
        advanceUntilIdle()
        assertTrue(manager.connectionState.value is Fido2ConnectionState.Connected)
        assertTrue(deviceConnected())
    }

    @Test
    fun cancelledNfcImportCannotDeliverLateResultOrEraseNewPin() = runTest(dispatcher) {
        delayNfcConnection = true
        manager.setPendingPin("1234")
        manager.setNfcCredentialCallback { error("Cancelled import delivered a result") }
        val oldImport = async { manager.connectToNfcTag(mock(Tag::class.java)) }
        runCurrent()
        manager.cancelImport()
        manager.setPendingPin("5678")
        manager.setNfcCredentialCallback { error("Old import delivered to a new callback") }
        pendingNfcConnections.removeAt(0).invoke()
        oldImport.await()
        assertEquals("5678", Fido2Manager::class.java.getDeclaredField("pendingPin").apply { isAccessible = true }.get(manager))
    }

    @Test
    fun typedCtapPinErrorsAreRecognizedThroughCauses() = runTest(dispatcher) {
        connected()
        for ((code, expected) in listOf(
            com.yubico.yubikit.core.fido.CtapException.ERR_PIN_INVALID to Fido2Result.PinInvalid::class.java,
            com.yubico.yubikit.core.fido.CtapException.ERR_PIN_AUTH_BLOCKED to Fido2Result.PinLocked::class.java,
            com.yubico.yubikit.core.fido.CtapException.ERR_PIN_BLOCKED to Fido2Result.PinLocked::class.java,
            com.yubico.yubikit.core.fido.CtapException.ERR_OTHER to Fido2Result.Error::class.java,
        )) {
            pinError = IOException("PIN_INVALID misleading wrapper", com.yubico.yubikit.core.fido.CtapException(code))
            var result: Fido2Result<List<Fido2Credential>>? = null
            manager.setPendingPin("1234")
            manager.setUsbCredentialCallback { result = it }
            manager.retryUsbWithPin()
            advanceUntilIdle()
            assertTrue(expected.isInstance(result))
        }
    }

    @Test
    fun usbDiscoveryConnectsOnceAndReusesSessionForPinCredentialEnumeration() = runTest(dispatcher) {
        manager.startUsbDiscovery()
        manager.startUsbDiscovery()
        usbCallback!!.invoke(usbDevice)
        usbCallback!!.invoke(usbDevice)
        advanceUntilIdle()
        assertTrue(manager.connectionState.value is Fido2ConnectionState.Connected)
        assertTrue(deviceConnected())
        var result: Fido2Result<List<Fido2Credential>>? = null
        manager.setUsbCredentialCallback { result = it }
        manager.retryUsbWithPin()
        assertTrue(result is Fido2Result.PinRequired)
        manager.setPendingPin("1234")
        manager.setUsbCredentialCallback { result = it }
        manager.retryUsbWithPin()
        advanceUntilIdle()
        assertTrue(result is Fido2Result.Success)
        manager.stopUsbDiscovery()
        assertFalse(deviceConnected())
        manager.setUsbCredentialCallback { result = it }
        manager.retryUsbWithPin()
        assertTrue(result is Fido2Result.Error)
    }

    @Test
    fun freshUsbConnectionDoesNotAcceptPreviousDeviceOrConnectedState() = runTest(dispatcher) {
        manager.startUsbDiscovery()
        usbCallback!!.invoke(usbDevice)
        advanceUntilIdle()
        assertTrue(deviceConnected())
        assertTrue(manager.connectionState.value is Fido2ConnectionState.Connected)
        manager.setPendingPin("old import PIN")

        val connection = async { manager.awaitFreshUsbConnection() }
        runCurrent()
        assertFalse(connection.isCompleted)
        assertFalse(deviceConnected())
        assertTrue(manager.connectionState.value is Fido2ConnectionState.Disconnected)

        usbCallback!!.invoke(usbDevice)
        advanceUntilIdle()
        assertTrue(connection.await())
        assertTrue(deviceConnected())
    }

    @Test
    fun freshUsbConnectionReportsDiscoveryFailureInsteadOfWaiting() = runTest(dispatcher) {
        connected()
        discoveryError = true

        assertFalse(manager.awaitFreshUsbConnection())
        assertFalse(deviceConnected())
        assertTrue(manager.connectionState.value is Fido2ConnectionState.Error)
    }

    @Test
    fun nfcDiscoverySkipsNdefProbingBeforeConnectingToSecurityKeys() {
        manager.startNfcDiscovery(mock(Activity::class.java))
        assertTrue(requireNotNull(nfcConfiguration).isSkipNdefCheck)
    }

    @Test
    fun duplicateNfcDiscoveryCannotOpenAnOverlappingConnection() = runTest(dispatcher) {
        delayNfcConnection = true
        manager.setPendingPin("1234")
        val results = mutableListOf<Fido2Result<List<Fido2Credential>>>()
        manager.setNfcCredentialCallback { results.add(it) }
        val first = async { manager.connectToNfcTag(mock(Tag::class.java)) }
        runCurrent()
        val duplicate = async { manager.connectToNfcTag(mock(Tag::class.java)) }
        runCurrent()
        assertTrue(duplicate.isCompleted)
        assertEquals(1, pendingNfcConnections.size)
        pendingNfcConnections.single().invoke()
        advanceUntilIdle()
        first.await()
        assertEquals(1, results.size)
        assertTrue(results.single() is Fido2Result.Success)
    }

    @Test
    fun nfcDiscoveryRequiresPinAndDeliversCredentialErrorsOrSuccess() = runTest(dispatcher) {
        val activity = mock(Activity::class.java)
        manager.startNfcDiscovery(activity)
        nfcCallback!!.invoke(nfcDevice)
        advanceUntilIdle()
        assertTrue(manager.connectionState.value is Fido2ConnectionState.Error)
        val tag = mock(Tag::class.java)
        for (error in listOf<Exception?>(null, IOException("PIN_INVALID"), IOException("PIN_AUTH_BLOCKED"), IOException("PIN_BLOCKED"), IOException(), IOException("other"))) {
            pinError = error
            var result: Fido2Result<List<Fido2Credential>>? = null
            manager.setPendingPin("1234")
            manager.setNfcCredentialCallback { result = it }
            manager.connectToNfcTag(tag)
            assertNotNull(result)
            assertEquals(error == null, result is Fido2Result.Success)
        }
        manager.stopNfcDiscovery(activity)
        pinError = null
        connectionError = IOException("disconnected")
        manager.connectToNfcTag(tag)
        assertTrue(manager.connectionState.value is Fido2ConnectionState.Error)
    }

    @Test
    fun nfcSigningKeepsPromptVisibleAndIgnoresOverlappingTaps() = runTest(dispatcher) {
        delayNfcConnection = true
        assertions = listOf(assertion())
        val results = mutableListOf<Fido2Result<Fido2SignatureResult>>()
        manager.prepareSshSigning(byteArrayOf(1), byteArrayOf(2)) { results.add(it) }
        manager.requestNfcSigning("1234")
        val first = async { manager.handleNfcTagForSigning(mock(Tag::class.java)) }
        runCurrent()
        assertTrue(manager.waitingForNfcSigning.value)
        val duplicate = async { manager.handleNfcTagForSigning(mock(Tag::class.java)) }
        runCurrent()
        assertTrue(duplicate.isCompleted)
        assertEquals(1, pendingNfcConnections.size)
        pendingNfcConnections.single().invoke()
        advanceUntilIdle()
        first.await()
        assertFalse(manager.waitingForNfcSigning.value)
        assertEquals(1, results.size)
        assertTrue(results.single() is Fido2Result.Success)
    }

    @Test
    fun nfcSigningCanRetryAfterTagLossWithoutFailingSshAuthentication() = runTest(dispatcher) {
        assertions = listOf(assertion())
        val results = mutableListOf<Fido2Result<Fido2SignatureResult>>()
        manager.prepareSshSigning(byteArrayOf(1), byteArrayOf(2)) { results.add(it) }
        manager.requestNfcSigning("1234")
        for (failure in listOf(TagLostException("lost"), IOException("wrapped", TagLostException("lost")))) {
            pinError = failure
            manager.handleNfcTagForSigning(mock(Tag::class.java))
            assertTrue(manager.waitingForNfcSigning.value)
            assertTrue(manager.nfcSigningTagLost.value)
            assertTrue(results.isEmpty())
        }
        pinError = null
        manager.handleNfcTagForSigning(mock(Tag::class.java))
        assertFalse(manager.waitingForNfcSigning.value)
        assertFalse(manager.nfcSigningTagLost.value)
        assertEquals(1, results.size)
        assertTrue(results.single() is Fido2Result.Success)
    }

    @Test
    fun nfcSigningCancellationDoesNotDeliverALateSignature() = runTest(dispatcher) {
        delayNfcConnection = true
        assertions = listOf(assertion())
        val results = mutableListOf<Fido2Result<Fido2SignatureResult>>()
        manager.prepareSshSigning(byteArrayOf(1), byteArrayOf(2)) { results.add(it) }
        manager.requestNfcSigning("1234")
        val signing = async { manager.handleNfcTagForSigning(mock(Tag::class.java)) }
        runCurrent()
        manager.cancelNfcSigning()
        assertFalse(manager.waitingForNfcSigning.value)
        pendingNfcConnections.single().invoke()
        advanceUntilIdle()
        signing.await()
        assertEquals(1, results.size)
        assertTrue(results.single() is Fido2Result.Error)
    }

    @Test
    fun nfcReaderDiscoveryRoutesPendingSigningRequestsToSigning() = runTest(dispatcher) {
        assertions = listOf(assertion())
        var result: Fido2Result<Fido2SignatureResult>? = null
        manager.prepareSshSigning(byteArrayOf(1), byteArrayOf(2)) { result = it }
        manager.requestNfcSigning("1234")
        manager.startNfcDiscovery(mock(Activity::class.java))
        assertTrue(requireNotNull(nfcConfiguration).isSkipNdefCheck)
        nfcCallback!!.invoke(nfcDevice)
        advanceUntilIdle()
        assertTrue(result is Fido2Result.Success)
        assertFalse(manager.waitingForNfcSigning.value)
    }

    @Test
    fun usbSigningWithoutDeviceFailsOnceAndCanRetryAfterConnecting() = runTest(dispatcher) {
        val results = mutableListOf<Fido2Result<Fido2SignatureResult>>()
        manager.prepareSshSigning(byteArrayOf(1), byteArrayOf(2)) { results.add(it) }
        manager.connectAndSignUsb("1234")
        manager.connectAndSignUsb("1234")
        advanceUntilIdle()
        assertEquals(1, results.size)
        assertTrue((results.single() as Fido2Result.Error).message.contains("Reconnect"))

        connected()
        assertions = listOf(assertion())
        manager.prepareSshSigning(byteArrayOf(1), byteArrayOf(2)) { results.add(it) }
        manager.connectAndSignUsb("1234")
        advanceUntilIdle()
        assertEquals(2, results.size)
        assertTrue(results.last() is Fido2Result.Success)
    }

    @Test
    fun usbSigningHandlesSynchronousConnectionExceptionsAndAllowsRetry() = runTest(dispatcher) {
        for (error in listOf(IllegalStateException("Device access not permitted"), SecurityException("USB permission revoked"), IOException("Device detached"))) {
            connected()
            connectionRequestError = error
            val results = mutableListOf<Fido2Result<Fido2SignatureResult>>()
            manager.prepareSshSigning(byteArrayOf(1), byteArrayOf(2)) { results.add(it) }
            manager.connectAndSignUsb("1234")
            advanceUntilIdle()
            val failure = results.single() as Fido2Result.Error
            assertSame(error, failure.cause)
            if (error !is IOException) {
                assertTrue(failure.message.contains("Reconnect"))
                assertFalse(deviceConnected())
            }
            manager.connectAndSignUsb("1234")
            advanceUntilIdle()
            assertEquals(1, results.size)

            connectionRequestError = null
            connected()
            assertions = listOf(assertion())
            manager.prepareSshSigning(byteArrayOf(1), byteArrayOf(2)) { results.add(it) }
            manager.connectAndSignUsb("1234")
            advanceUntilIdle()
            assertEquals(2, results.size)
            assertTrue(results.last() is Fido2Result.Success)
        }
    }

    @Test
    fun usbAndNfcSigningClearPendingStateAndMapAuthenticatorErrors() = runTest(dispatcher) {
        var result: Fido2Result<Fido2SignatureResult>? = null
        manager.connectAndSignUsb("1234")
        manager.prepareSshSigning(byteArrayOf(1), byteArrayOf(2)) { result = it }
        manager.connectAndSignUsb("1234")
        assertTrue(result is Fido2Result.Error)
        connected()
        assertions = listOf(assertion())
        for (error in listOf<Exception?>(null, IOException("PIN_INVALID"), IOException("PIN_AUTH_BLOCKED"), IOException("PIN_BLOCKED"), IOException("NO_CREDENTIALS"), IOException(), IOException("other"))) {
            pinError = error
            manager.prepareSshSigning(byteArrayOf(1), byteArrayOf(2), "ssh:server") { result = it }
            manager.connectAndSignUsb("1234")
            advanceUntilIdle()
            assertEquals(error == null, result is Fido2Result.Success)
            manager.prepareSshSigning(byteArrayOf(1), byteArrayOf(2)) { result = it }
            manager.requestNfcSigning("1234")
            assertTrue(manager.waitingForNfcSigning.value)
            manager.handleNfcTagForSigning(mock(Tag::class.java))
            assertFalse(manager.waitingForNfcSigning.value)
            assertEquals(error == null, result is Fido2Result.Success)
        }
        manager.handleNfcTagForSigning(mock(Tag::class.java))
        manager.prepareSshSigning(byteArrayOf(1), byteArrayOf(2)) { result = it }
        manager.requestNfcSigning("1234")
        manager.cancelNfcSigning()
        assertTrue(result is Fido2Result.Error)
        manager.cancelNfcSigning()
        connectionError = IOException("gone")
        manager.prepareSshSigning(byteArrayOf(1), byteArrayOf(2)) { result = it }
        manager.connectAndSignUsb("1234")
        advanceUntilIdle()
        assertTrue(result is Fido2Result.Error)
        assertTrue(manager.connectAndSignNfc(mock(Tag::class.java), "1234", byteArrayOf(1), byteArrayOf(2)) is Fido2Result.Error)
    }

    @Test
    fun retriesStaleUsbSessionsAndMapsPinBlockStatesWithRetryHints() = runTest(dispatcher) {
        connected()
        var result: Fido2Result<List<Fido2Credential>>? = null
        fun retry() {
            manager.setPendingPin("1234")
            manager.setUsbCredentialCallback { result = it }
            manager.retryUsbWithPin()
        }
        for (error in listOf(IOException("PIN_INVALID"), IOException("PIN_AUTH_BLOCKED"), IOException("PIN_BLOCKED"), IOException(), IOException("other"))) {
            pinError = error
            retry()
            advanceUntilIdle()
            assertFalse(result is Fido2Result.Success)
        }
        pinError = null
        for (message in listOf("Failed to send full packet", "broken pipe", "connection reset")) {
            pinTokenErrors.addAll(listOf(IOException(message), null))
            retry()
            advanceUntilIdle()
            assertTrue(result is Fido2Result.Success)
        }
        for (failure in listOf<Exception?>(null, IOException("PIN_AUTH_BLOCKED"), IOException("PIN_BLOCKED"))) {
            pinTokenErrors.addAll(listOf(IOException("broken pipe"), IOException("PIN_BLOCKED"), failure))
            closeError = true
            retry()
            advanceUntilIdle()
            assertEquals(failure == null, result is Fido2Result.Success)
            closeError = false
        }
        for (cycle in listOf(true, false, null)) {
            retryCount = 3
            powerCycle = cycle
            pinError = IOException("PIN_BLOCKED")
            retry()
            advanceUntilIdle()
            assertTrue((result as Fido2Result.PinLocked).message.contains("Remaining retries: 3"))
        }
        attach("pinUvAuthProtocol", com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV2())
        probeFailures = 1
        retry()
        advanceUntilIdle()
        assertTrue(result is Fido2Result.PinLocked)
        probeFailures = 2
        pinTokenErrors.addAll(listOf(IOException("broken pipe"), IOException("PIN_BLOCKED"), IOException("PIN_BLOCKED")))
        pinError = null
        retry()
        advanceUntilIdle()
        assertTrue(result is Fido2Result.PinLocked)
        attach("currentDevice", null)
        pinError = IOException("broken pipe")
        retry()
        advanceUntilIdle()
        assertTrue(result is Fido2Result.Error)
    }

    @Test
    fun usbDeviceFilteringAndDiscoveryFailuresDoNotCrash() = runTest(dispatcher) {
        val supported = mock(UsbDevice::class.java)
        val unsupported = mock(UsbDevice::class.java)
        `when`(supported.vendorId).thenReturn(0x1050)
        `when`(unsupported.vendorId).thenReturn(0)
        `when`(usbManager.deviceList).thenReturn(hashMapOf("other" to unsupported, "key" to supported))
        manager.startUsbDiscovery()
        manager.stopUsbDiscovery()
        `when`(usbManager.hasPermission(supported)).thenReturn(true)
        versions = listOf(1)
        manager.startUsbDiscovery()
        advanceUntilIdle()
        assertTrue(deviceConnected())
        manager.stopUsbDiscovery()
        manager.setPendingPin("1234")
        manager.startUsbDiscovery()
        advanceUntilIdle()
        assertTrue(manager.connectionState.value is Fido2ConnectionState.Connected)
        manager.stopUsbDiscovery()
        discoveryError = true
        manager.startUsbDiscovery()
        manager.stopUsbDiscovery()
        manager.startNfcDiscovery(mock(Activity::class.java))
        manager.stopNfcDiscovery(mock(Activity::class.java))
        discoveryError = false
        connectionError = IOException("gone")
        attach("pendingPin", null)
        manager.startUsbDiscovery()
        advanceUntilIdle()
        manager.stopUsbDiscovery()
    }
}
