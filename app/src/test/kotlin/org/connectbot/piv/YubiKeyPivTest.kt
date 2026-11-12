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
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.nfc.NfcAdapter
import android.os.Looper
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.android.transport.usb.UsbYubiKeyDevice
import com.yubico.yubikit.core.application.InvalidPinException
import com.yubico.yubikit.core.keys.PublicKeyValues
import com.yubico.yubikit.core.smartcard.ApduException
import com.yubico.yubikit.core.smartcard.SW
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.core.util.Callback
import com.yubico.yubikit.core.util.Result
import com.yubico.yubikit.piv.KeyType
import com.yubico.yubikit.piv.PinPolicy
import com.yubico.yubikit.piv.PivSession
import com.yubico.yubikit.piv.Slot
import com.yubico.yubikit.piv.SlotMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.connectbot.sshlib.SshSigning
import org.connectbot.util.PreferenceConstants
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.any
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class YubiKeyPivTest {
    private val activityController = Robolectric.buildActivity(Activity::class.java).setup()
    private val activity = activityController.get()
    private val executor = Executors.newSingleThreadExecutor()
    private val scheduler = mock(ScheduledExecutorService::class.java)
    private val manager = mock(YubiKitManager::class.java)
    private val connections = mutableMapOf<SmartCardConnection, PivSession>()
    private val timers = mutableListOf<Runnable>()
    private var usbDevices = listOf<UsbYubiKeyDevice>()
    private var nfcDevices = listOf<NfcYubiKeyDevice>()
    private var nfcUnavailable = false
    private var nfcStarts = 0
    private val discoveryStarted = CompletableDeferred<Unit>()
    private var factoryFailure: Exception? = null
    private var holdIdleMs = 5 * 60_000L
    private val verifiedPins = mutableSetOf<SmartCardConnection>()
    private var keyStaysOnReader = false
    private val removalCallbacks = mutableListOf<Runnable>()
    private lateinit var client: YubiKeyPiv.Client
    private val key = newKey()
    private val data = "SSH authentication payload".toByteArray()

    private data class Card(val device: UsbYubiKeyDevice, val connection: SmartCardConnection, val session: PivSession)

    @Before
    fun setup() {
        doAnswer {
            timers.add(it.getArgument(0))
            mock(ScheduledFuture::class.java)
        }.`when`(scheduler).schedule(any(Runnable::class.java), anyLong(), any(TimeUnit::class.java))
        doAnswer {
            val callback = it.getArgument<Callback<UsbYubiKeyDevice>>(1)
            usbDevices.forEach(callback::invoke)
            discoveryStarted.complete(Unit)
            null
        }.`when`(manager).startUsbDiscovery(any(), any())
        doAnswer {
            nfcStarts++
            if (nfcUnavailable) throw NfcNotAvailable("disabled", true)
            val callback = it.getArgument<Callback<NfcYubiKeyDevice>>(2)
            nfcDevices.forEach(callback::invoke)
            null
        }.`when`(manager).startNfcDiscovery(any(), any(), any())
        client = YubiKeyPiv.Client(executor, scheduler, { manager }, {
            factoryFailure?.let { failure -> throw failure }
            connections.getValue(it)
        }, deviceTimeoutMs = 1_000, holdIdleMs = { holdIdleMs })
    }

    @After
    fun cleanup() {
        // Expire retained sessions through the real timer callbacks, including receiver cleanup.
        executor.submit { timers.toList().forEach(Runnable::run) }.get(5, TimeUnit.SECONDS)
        executor.shutdownNow()
        activityController.pause().stop().destroy()
    }

    private fun newKey(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun metadata(pair: KeyPair, type: KeyType, policy: PinPolicy): SlotMetadata {
        val metadata = mock(SlotMetadata::class.java)
        doReturn(type).`when`(metadata).keyType
        doReturn(policy).`when`(metadata).pinPolicy
        doReturn(PublicKeyValues.fromPublicKey(pair.public)).`when`(metadata).publicKeyValues
        return metadata
    }

    /**
     * [layout] maps slots to the key they hold, and slots missing from it are empty. Without a layout every
     * slot holds [pair].
     */
    private fun session(
        policy: PinPolicy = PinPolicy.ONCE,
        pair: KeyPair = key,
        type: KeyType = KeyType.ECCP256,
        layout: Map<Slot, KeyPair>? = null,
    ): PivSession {
        val session = mock(PivSession::class.java)
        doReturn(true).`when`(session).supports(PivSession.FEATURE_METADATA)
        if (layout == null) {
            doReturn(metadata(pair, type, policy)).`when`(session).getSlotMetadata(any(Slot::class.java))
        } else {
            doAnswer {
                val held = layout[it.getArgument<Slot>(0)] ?: throw ApduException(byteArrayOf(), SW.REFERENCED_DATA_NOT_FOUND)
                metadata(held, type, policy)
            }.`when`(session).getSlotMetadata(any(Slot::class.java))
        }
        doAnswer {
            val signer = layout?.get(it.getArgument<Slot>(0)) ?: pair
            assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(data), it.getArgument<ByteArray>(2))
            Signature.getInstance("SHA256withECDSA").run {
                initSign(signer.private)
                update(data)
                sign()
            }
        }.`when`(session).rawSignOrDecrypt(any(Slot::class.java), eq(KeyType.ECCP256), any(ByteArray::class.java))
        return session
    }

    // An empty VERIFY is answered with 9000 while the PIN is verified, otherwise with 63Cx and the attempts left.
    private fun answerPinProbe(connection: SmartCardConnection, session: PivSession) {
        doAnswer {
            if (connection in verifiedPins) byteArrayOf(0x90.toByte(), 0x00) else byteArrayOf(0x63, 0xC3.toByte())
        }.`when`(connection).sendAndReceive(any(ByteArray::class.java))
        doAnswer {
            verifiedPins.add(connection)
            null
        }.`when`(session).verifyPin(any(CharArray::class.java))
    }

    private fun usb(
        name: String = "usb-A",
        policy: PinPolicy = PinPolicy.ONCE,
        pair: KeyPair = key,
        layout: Map<Slot, KeyPair>? = null,
    ): Card {
        val device = mock(UsbYubiKeyDevice::class.java)
        val usb = mock(UsbDevice::class.java)
        val connection = mock(SmartCardConnection::class.java)
        val session = session(policy, pair, layout = layout)
        doReturn(name).`when`(usb).deviceName
        doReturn(usb).`when`(device).usbDevice
        doReturn(true).`when`(device).supportsConnection(SmartCardConnection::class.java)
        doReturn(connection).`when`(device).openConnection(SmartCardConnection::class.java)
        connections[connection] = session
        answerPinProbe(connection, session)
        usbDevices = listOf(device)
        return Card(device, connection, session)
    }

    private fun nfc(session: PivSession = session()): SmartCardConnection {
        val device = mock(NfcYubiKeyDevice::class.java)
        val connection = mock(SmartCardConnection::class.java)
        connections[connection] = session
        answerPinProbe(connection, session)
        doReturn(true).`when`(device).supportsConnection(SmartCardConnection::class.java)
        doAnswer {
            it.getArgument<Callback<Result<SmartCardConnection, IOException>>>(1).invoke(Result.success(connection))
            null
        }.`when`(device).requestConnection(eq(SmartCardConnection::class.java), any())
        // By default the key is lifted from the phone as soon as it was used.
        doAnswer {
            val callback = it.getArgument<Runnable>(0)
            if (keyStaysOnReader) removalCallbacks.add(callback) else callback.run()
            null
        }.`when`(device).remove(any(Runnable::class.java))
        nfcDevices = listOf(device)
        return connection
    }

    private suspend fun sign(pin: String = "123456", expected: KeyPair = key): ByteArray = client.sign(activity, Slot.AUTHENTICATION, expected.public, pin.toCharArray(), data)

    private suspend inline fun <reified T : Throwable> failure(crossinline action: suspend () -> Unit): T {
        try {
            action()
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
        throw AssertionError("Expected ${T::class.java.name}")
    }

    @Test
    fun identityEncodingAndSlotValidation() {
        assertThrows(IllegalArgumentException::class.java) { YubiKeyPiv.slotFromId(null) }
        assertThrows(IllegalArgumentException::class.java) { YubiKeyPiv.slotId(Slot.ATTESTATION) }
        for (slot in Slot.values().filter { it != Slot.ATTESTATION }) {
            assertEquals(slot, YubiKeyPiv.slotFromId(YubiKeyPiv.slotId(slot)))
        }
        assertThrows(IllegalArgumentException::class.java) { YubiKeyPiv.slotFromId(0xF9) }
        assertThrows(IllegalArgumentException::class.java) { YubiKeyPiv.slotFromId(0) }
        assertEquals(SshSigning.encodePublicKey(key), YubiKeyPiv.encodePublicKey(key.public))
        val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertThrows(IllegalArgumentException::class.java) { YubiKeyPiv.encodePublicKey(rsa.public) }
    }

    @Test
    fun usbReadAndSigningReuseSessionAndClearPinCopy() = runBlocking<Unit> {
        val card = usb()
        assertEquals(key.public, client.readPublicKey(activity, Slot.AUTHENTICATION))
        var cardPin: CharArray? = null
        doAnswer {
            cardPin = it.getArgument(0)
            assertEquals("123456", String(cardPin!!))
            verifiedPins.add(card.connection)
            null
        }
            .`when`(card.session).verifyPin(any(CharArray::class.java))
        val signature = sign()
        assertTrue(signature.isNotEmpty())
        assertTrue(cardPin!!.all { it == '\u0000' })
        sign("")
        assertTrue(client.canSignWithoutPin(Slot.AUTHENTICATION))
        verify(card.device, times(1)).openConnection(SmartCardConnection::class.java)
        verify(card.session, times(1)).verifyPin(any(CharArray::class.java))
        verify(manager).stopUsbDiscovery()
        verify(manager).stopNfcDiscovery(activity)
    }

    @Test
    fun neverPolicyDoesNotVerifyPin() = runBlocking<Unit> {
        val card = usb(policy = PinPolicy.NEVER)
        sign()
        assertTrue(client.canSignWithoutPin(Slot.AUTHENTICATION))
        verify(card.session, never()).verifyPin(any(CharArray::class.java))
    }

    @Test
    fun alwaysPolicyRequiresPinAndCannotUseAutomaticProbe() = runBlocking<Unit> {
        val card = usb(policy = PinPolicy.ALWAYS)
        failure<YubiKeyPiv.PinRequired> { sign("") }
        assertFalse(client.canSignWithoutPin(Slot.AUTHENTICATION))
        sign()
        verify(card.session).verifyPin(any(CharArray::class.java))
    }

    @Test
    fun oncePolicyRequestsPinWhenCardForgotIt() = runBlocking<Unit> {
        val card = usb()
        doThrow(ApduException(byteArrayOf(), SW.SECURITY_CONDITION_NOT_SATISFIED)).`when`(card.session)
            .rawSignOrDecrypt(any(Slot::class.java), eq(KeyType.ECCP256), any(ByteArray::class.java))
        failure<YubiKeyPiv.PinRequired> { sign("") }
        failure<ApduException> { sign() }
    }

    @Test
    fun cardErrorsAndWrongPinAreNotRetried() = runBlocking<Unit> {
        val card = usb()
        val wrongPin = InvalidPinException(2)
        doThrow(wrongPin).`when`(card.session).verifyPin(any(CharArray::class.java))
        assertSame(wrongPin, failure<InvalidPinException> { sign() })
        assertSame(wrongPin, failure<InvalidPinException> { sign() })
        verify(card.device, times(1)).openConnection(SmartCardConnection::class.java)
        verify(card.session, times(2)).verifyPin(any(CharArray::class.java))
    }

    @Test
    fun freshWrongKeyNeverReceivesPin() = runBlocking<Unit> {
        val card = usb(pair = newKey())
        failure<YubiKeyPiv.WrongKey> { sign() }
        verify(card.session, never()).verifyPin(any(CharArray::class.java))
        verify(card.session, never()).rawSignOrDecrypt(any(Slot::class.java), any(), any(ByteArray::class.java))
    }

    @Test
    fun wrongSignatureIsRejected() = runBlocking<Unit> {
        val card = usb()
        val other = newKey()
        val bad = Signature.getInstance("SHA256withECDSA").run {
            initSign(other.private)
            update(data)
            sign()
        }
        doReturn(bad).`when`(card.session).rawSignOrDecrypt(any(Slot::class.java), any(), any(ByteArray::class.java))
        failure<YubiKeyPiv.WrongKey> { sign() }
    }

    @Test
    fun rejectedHeldKeyIsExcludedSoNfcCanSign() = runBlocking<Unit> {
        val old = usb(pair = newKey())
        client.readPublicKey(activity, Slot.AUTHENTICATION)
        nfc()
        sign()
        verify(old.connection).close()
        verify(old.device, times(1)).openConnection(SmartCardConnection::class.java)
        assertFalse(client.canSignWithoutPin(Slot.AUTHENTICATION))
    }

    @Test
    fun emptyHeldSlotIsReleasedAndDiscoveryContinues() = runBlocking<Unit> {
        val old = usb()
        client.readPublicKey(activity, Slot.AUTHENTICATION)
        doThrow(ApduException(byteArrayOf(), SW.REFERENCED_DATA_NOT_FOUND)).`when`(old.session).getSlotMetadata(any(Slot::class.java))
        nfc()
        sign()
        verify(old.connection).close()
        verify(old.session, never()).verifyPin(any(CharArray::class.java))
    }

    @Test
    fun unsupportedHeldKeyFallsBackButReadStillReportsError() = runBlocking<Unit> {
        val old = usb()
        client.readPublicKey(activity, Slot.AUTHENTICATION)
        doReturn(false).`when`(old.session).supports(PivSession.FEATURE_METADATA)
        failure<YubiKeyPiv.UnsupportedKey> { client.readPublicKey(activity, Slot.AUTHENTICATION) }
        assertFalse(client.canSignWithoutPin(Slot.AUTHENTICATION))
        nfc()
        sign()
        verify(old.connection).close()
    }

    @Test
    fun unsupportedKeyTypeIsRejected() = runBlocking<Unit> {
        val card = usb()
        val metadata = mock(SlotMetadata::class.java)
        doReturn(KeyType.RSA2048).`when`(metadata).keyType
        doReturn(metadata).`when`(card.session).getSlotMetadata(any(Slot::class.java))
        val failure = failure<YubiKeyPiv.UnsupportedKey> { sign() }
        assertEquals(YubiKeyPiv.UnsupportedKey.Reason.ALGORITHM, failure.reason)
        assertEquals("RSA 2048", failure.algorithm)
        verify(card.session, never()).verifyPin(any(CharArray::class.java))
    }

    @Test
    fun missingMetadataSupportIsReportedAsFirmware() = runBlocking<Unit> {
        val card = usb()
        doReturn(false).`when`(card.session).supports(PivSession.FEATURE_METADATA)
        assertEquals(YubiKeyPiv.UnsupportedKey.Reason.FIRMWARE, failure<YubiKeyPiv.UnsupportedKey> { sign() }.reason)
    }

    @Test
    fun readingAnEmptySlotIsReportedAsEmpty() = runBlocking<Unit> {
        usb(layout = emptyMap())
        failure<YubiKeyPiv.EmptySlot> { client.readPublicKey(activity, Slot.AUTHENTICATION) }
    }

    @Test
    fun pinIsAskedForBeforeAnyTouchWhenTheCardDoesNotRememberIt() = runBlocking<Unit> {
        // A connection is held, for instance from reading the public key, but no PIN was verified on it.
        val card = usb()
        client.readPublicKey(activity, Slot.AUTHENTICATION)
        assertFalse(client.canSignWithoutPin(Slot.AUTHENTICATION))
        failure<YubiKeyPiv.PinRequired> { sign("") }
        // The card was never asked to sign, so it never waited for a touch only to then refuse for the PIN.
        verify(card.session, never()).rawSignOrDecrypt(any(Slot::class.java), any(KeyType::class.java), any(ByteArray::class.java))
    }

    @Test
    fun cardForgettingThePinOnAHeldConnectionAsksForItAgain() = runBlocking<Unit> {
        val card = usb()
        sign()
        assertTrue(client.canSignWithoutPin(Slot.AUTHENTICATION))
        verifiedPins.remove(card.connection)
        assertFalse(client.canSignWithoutPin(Slot.AUTHENTICATION))
        failure<YubiKeyPiv.PinRequired> { sign("") }
        verify(card.session, times(1)).rawSignOrDecrypt(any(Slot::class.java), any(KeyType::class.java), any(ByteArray::class.java))
    }

    @Test
    fun cardRefusingAfterTheProbeAnsweredVerifiedStillAsksForThePin() = runBlocking<Unit> {
        val card = usb()
        verifiedPins.add(card.connection)
        doThrow(ApduException(byteArrayOf(), SW.SECURITY_CONDITION_NOT_SATISFIED)).`when`(card.session)
            .rawSignOrDecrypt(any(Slot::class.java), any(KeyType::class.java), any(ByteArray::class.java))
        failure<YubiKeyPiv.PinRequired> { sign("") }
    }

    @Test
    fun nfcReaderModeStaysOnUntilTheKeyIsLifted() = runBlocking<Unit> {
        keyStaysOnReader = true
        nfc()
        sign()
        // The key is still on the phone: turning reader mode off now would hand the tag to Android.
        verify(manager, never()).stopNfcDiscovery(activity)
        assertEquals(1, removalCallbacks.size)
        removalCallbacks.single().run()
        verify(manager, times(1)).stopNfcDiscovery(activity)
        // Later triggers, such as the timeout, must not stop it a second time.
        executor.submit { timers.toList().forEach(Runnable::run) }.get(5, TimeUnit.SECONDS)
        verify(manager, times(1)).stopNfcDiscovery(activity)
    }

    @Test
    fun nfcReaderModeIsStoppedAfterATimeoutIfTheKeyIsNeverLifted() = runBlocking<Unit> {
        keyStaysOnReader = true
        nfc()
        sign()
        verify(manager, never()).stopNfcDiscovery(activity)
        verify(scheduler).schedule(any(Runnable::class.java), eq(10_000L), eq(TimeUnit.MILLISECONDS))
        executor.submit { timers.toList().forEach(Runnable::run) }.get(5, TimeUnit.SECONDS)
        verify(manager, times(1)).stopNfcDiscovery(activity)
    }

    @Test
    fun nfcReaderModeIsStoppedAtOnceIfRemovalCannotBeWatched() = runBlocking<Unit> {
        val connection = nfc()
        doThrow(IllegalStateException("executor shut down")).`when`(nfcDevices.single()).remove(any(Runnable::class.java))
        sign()
        verify(manager, times(1)).stopNfcDiscovery(activity)
        assertTrue(connections.containsKey(connection))
    }

    @Test
    fun usbOnlySessionsStopNfcReaderModeImmediately() = runBlocking<Unit> {
        usb()
        sign()
        verify(manager, times(1)).stopNfcDiscovery(activity)
    }

    @Test
    fun configuredIdleTimeIsUsedForTheIdleTimer() = runBlocking<Unit> {
        usb()
        holdIdleMs = 120_000
        sign()
        verify(scheduler).schedule(any(Runnable::class.java), eq(120_000L), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun zeroIdleTimeClosesTheConnectionRightAway() = runBlocking<Unit> {
        val card = usb()
        holdIdleMs = 0
        sign()
        verify(card.connection).close()
        verify(scheduler, never()).schedule(any(Runnable::class.java), anyLong(), any(TimeUnit::class.java))
        assertFalse(client.canSignWithoutPin(Slot.AUTHENTICATION))
        // Nothing is held, so the next login connects again and has to be given the PIN again.
        sign()
        verify(card.device, times(2)).openConnection(SmartCardConnection::class.java)
        verify(card.session, times(2)).verifyPin(any(CharArray::class.java))
    }

    @Test
    fun idleTimePreferenceIsReadWithFiveMinutesAsDefault() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        assertEquals(5 * 60_000L, YubiKeyPiv.preferredHoldIdleMs(activity))
        for ((value, expected) in listOf("0" to 0L, "60" to 60_000L, "3600" to 3_600_000L, "999999" to 24 * 3_600_000L, "-5" to 0L, "junk" to 5 * 60_000L)) {
            prefs.edit().putString(PreferenceConstants.PIV_SESSION_IDLE_SECONDS, value).commit()
            assertEquals(value, expected, YubiKeyPiv.preferredHoldIdleMs(activity))
        }
    }

    @Test
    fun keyMovedToAnotherSlotIsFoundAndSignedThere() = runBlocking<Unit> {
        val card = usb(layout = mapOf(Slot.RETIRED5 to key, Slot.SIGNATURE to newKey()))
        assertTrue(sign().isNotEmpty())
        verify(card.session).rawSignOrDecrypt(eq(Slot.RETIRED5), eq(KeyType.ECCP256), any(ByteArray::class.java))
        verify(card.session, never()).rawSignOrDecrypt(eq(Slot.AUTHENTICATION), any(KeyType::class.java), any(ByteArray::class.java))
    }

    @Test
    fun searchUsesThePinPolicyOfTheSlotItFound() = runBlocking<Unit> {
        val card = usb(policy = PinPolicy.ALWAYS, layout = mapOf(Slot.CARD_AUTH to key))
        failure<YubiKeyPiv.PinRequired> { sign("") }
        sign()
        verify(card.session).verifyPin(any(CharArray::class.java))
    }

    @Test
    fun theSavedSlotIsTriedBeforeSearching() = runBlocking<Unit> {
        val card = usb(layout = mapOf(Slot.AUTHENTICATION to key, Slot.SIGNATURE to key))
        sign()
        verify(card.session, times(1)).getSlotMetadata(any(Slot::class.java))
        verify(card.session).rawSignOrDecrypt(eq(Slot.AUTHENTICATION), any(KeyType::class.java), any(ByteArray::class.java))
    }

    @Test
    fun searchMatchesPublicKeysSoOtherKeysAreNeverUsed() = runBlocking<Unit> {
        val card = usb(layout = mapOf(Slot.SIGNATURE to newKey(), Slot.RETIRED1 to newKey(), Slot.RETIRED2 to newKey()))
        failure<YubiKeyPiv.WrongKey> { sign() }
        verify(card.session, never()).verifyPin(any(CharArray::class.java))
        verify(card.session, never()).rawSignOrDecrypt(any(Slot::class.java), any(KeyType::class.java), any(ByteArray::class.java))
    }

    @Test
    fun searchSkipsSlotsWithUnusableKeysAndMetadataErrors() = runBlocking<Unit> {
        val card = usb(layout = mapOf(Slot.RETIRED20 to key))
        val unusable = mock(SlotMetadata::class.java)
        doReturn(KeyType.X25519).`when`(unusable).keyType
        doReturn(unusable).`when`(card.session).getSlotMetadata(Slot.SIGNATURE)
        doThrow(ApduException(byteArrayOf(), SW.COMMAND_ABORTED)).`when`(card.session).getSlotMetadata(Slot.KEY_MANAGEMENT)
        assertTrue(sign().isNotEmpty())
        verify(card.session).rawSignOrDecrypt(eq(Slot.RETIRED20), any(KeyType::class.java), any(ByteArray::class.java))
    }

    @Test
    fun emptySavedSlotWithTheKeyNowhereReportsWrongKey() = runBlocking<Unit> {
        usb(layout = emptyMap())
        failure<YubiKeyPiv.WrongKey> { sign() }
    }

    @Test
    fun metadataErrorsOtherThanMissingSlotPropagate() = runBlocking<Unit> {
        val card = usb()
        val error = ApduException(byteArrayOf(), SW.COMMAND_ABORTED)
        doThrow(error).`when`(card.session).getSlotMetadata(any(Slot::class.java))
        assertSame(error, failure<ApduException> { sign() })
    }

    @Test
    fun transportFailureDropsHeldConnectionAndRediscovers() = runBlocking<Unit> {
        val old = usb()
        client.readPublicKey(activity, Slot.AUTHENTICATION)
        doThrow(IOException("unplugged")).`when`(old.session).getSlotMetadata(any(Slot::class.java))
        val replacement = usb("usb-B")
        sign()
        verify(old.connection).close()
        verify(replacement.device).openConnection(SmartCardConnection::class.java)
    }

    @Test
    fun sessionConstructionFailureClosesConnection() = runBlocking<Unit> {
        val card = usb()
        factoryFailure = IOException("cannot select PIV")
        failure<IOException> { sign() }
        verify(card.connection).close()
        assertFalse(client.canSignWithoutPin(Slot.AUTHENTICATION))
    }

    @Test
    fun openingFailureCleansUpDiscovery() = runBlocking<Unit> {
        val card = usb()
        doThrow(IOException("gone")).`when`(card.device).openConnection(SmartCardConnection::class.java)
        failure<IOException> { sign() }
        verify(manager).stopUsbDiscovery()
        verify(manager).stopNfcDiscovery(activity)
    }

    @Test
    fun freshTransportFailureClosesConnection() = runBlocking<Unit> {
        val card = usb()
        doThrow(IOException("gone")).`when`(card.session).getSlotMetadata(any(Slot::class.java))
        failure<IOException> { sign() }
        verify(card.connection).close()
    }

    @Test
    fun nfcReadAndSignDoNotRetainConnection() = runBlocking<Unit> {
        nfc()
        assertEquals(key.public, client.readPublicKey(activity, Slot.AUTHENTICATION))
        assertFalse(client.canSignWithoutPin(Slot.AUTHENTICATION))
        sign()
        verify(manager, times(2)).stopNfcDiscovery(activity)
    }

    @Test
    fun nfcFailureIsDeliveredAndDiscoveryStops() = runBlocking<Unit> {
        val session = session()
        nfc(session)
        doThrow(InvalidPinException(1)).`when`(session).verifyPin(any(CharArray::class.java))
        failure<InvalidPinException> { sign() }
        verify(manager).stopNfcDiscovery(activity)
    }

    @Test
    fun noDeviceTimesOutAndStopsDiscovery() = runBlocking<Unit> {
        failure<YubiKeyPiv.DeviceTimeout> { sign() }
        verify(manager).stopUsbDiscovery()
        verify(manager).stopNfcDiscovery(activity)
    }

    @Test
    fun unsupportedDeviceIsIgnored() = runBlocking<Unit> {
        val card = usb()
        doReturn(false).`when`(card.device).supportsConnection(SmartCardConnection::class.java)
        failure<YubiKeyPiv.DeviceTimeout> { sign() }
        verify(card.device, never()).openConnection(SmartCardConnection::class.java)
    }

    @Test
    fun onlyFirstDiscoveredDeviceIsUsed() = runBlocking<Unit> {
        val first = usb("usb-A")
        val second = usb("usb-B")
        usbDevices = listOf(first.device, second.device)
        sign()
        verify(first.device).openConnection(SmartCardConnection::class.java)
        verify(second.device, never()).openConnection(SmartCardConnection::class.java)
    }

    @Test
    fun nfcDisabledStillAllowsUsb() = runBlocking<Unit> {
        nfcUnavailable = true
        usb()
        sign()
        verify(manager, never()).stopNfcDiscovery(activity)
    }

    @Test
    fun nfcEnabledDuringDiscoveryRestartsReader() = runBlocking<Unit> {
        nfcUnavailable = true
        val result = async(start = CoroutineStart.UNDISPATCHED) { sign() }
        discoveryStarted.await()
        assertEquals(1, nfcStarts)
        activity.sendBroadcast(Intent(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED).putExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, nfcStarts)
        nfcUnavailable = false
        nfc()
        activity.sendBroadcast(Intent(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED).putExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(result.await().isNotEmpty())
        assertEquals(2, nfcStarts)
    }

    @Test
    fun cancellationStopsDiscovery() = runBlocking<Unit> {
        val result = launch(start = CoroutineStart.UNDISPATCHED) { sign() }
        discoveryStarted.await()
        result.cancel()
        result.join()
        verify(manager).stopUsbDiscovery()
        verify(manager).stopNfcDiscovery(activity)
    }

    @Test
    fun cancelledProbePropagatesCancellation() = runBlocking<Unit> {
        var cancelled = false
        launch {
            cancel()
            try {
                client.canSignWithoutPin(Slot.AUTHENTICATION)
            } catch (_: CancellationException) {
                cancelled = true
            }
        }.join()
        assertTrue(cancelled)
    }

    @Test
    fun idleExpiryClosesAndForcesRediscovery() = runBlocking<Unit> {
        val card = usb()
        sign()
        executor.submit { timers.last().run() }.get()
        verify(card.connection).close()
        assertFalse(client.canSignWithoutPin(Slot.AUTHENTICATION))
        sign()
        verify(card.device, times(2)).openConnection(SmartCardConnection::class.java)
    }

    @Test
    fun detachOnlyReleasesMatchingUsbDevice() = runBlocking<Unit> {
        val card = usb()
        sign()
        val unrelated = mock(UsbDevice::class.java)
        doReturn("usb-other").`when`(unrelated).deviceName
        activity.sendBroadcast(Intent(UsbManager.ACTION_USB_DEVICE_DETACHED).putExtra(UsbManager.EXTRA_DEVICE, unrelated))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(client.canSignWithoutPin(Slot.AUTHENTICATION))
        activity.sendBroadcast(Intent(UsbManager.ACTION_USB_DEVICE_DETACHED).putExtra(UsbManager.EXTRA_DEVICE, card.device.usbDevice))
        shadowOf(Looper.getMainLooper()).idle()
        executor.submit {}.get()
        assertFalse(client.canSignWithoutPin(Slot.AUTHENTICATION))
        verify(card.connection).close()
    }
}
