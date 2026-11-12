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
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
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

    private var lastDer: ByteArray? = null

    private fun keyTypeOf(pair: KeyPair): KeyType = when (val public = pair.public) {
        is ECPublicKey -> if (public.params.order.bitLength() == 384) KeyType.ECCP384 else KeyType.ECCP256

        is RSAPublicKey -> when (public.modulus.bitLength()) {
            3072 -> KeyType.RSA3072
            4096 -> KeyType.RSA4096
            else -> KeyType.RSA2048
        }

        else -> KeyType.ED25519
    }

    private fun metadata(pair: KeyPair, type: KeyType, policy: PinPolicy): SlotMetadata {
        val metadata = mock(SlotMetadata::class.java)
        doReturn(type).`when`(metadata).keyType
        doReturn(policy).`when`(metadata).pinPolicy
        doReturn(PublicKeyValues.fromPublicKey(pair.public)).`when`(metadata).publicKeyValues
        return metadata
    }

    // What the card does with the input it is given, checked against what the SSH layer should send.
    private fun cardSign(pair: KeyPair, type: KeyType, input: ByteArray): ByteArray = when (type) {
        KeyType.ECCP256, KeyType.ECCP384 -> {
            val bits = if (type == KeyType.ECCP384) 384 else 256
            assertArrayEquals(MessageDigest.getInstance("SHA-$bits").digest(data), input)
            Signature.getInstance("SHA${bits}withECDSA").run {
                initSign(pair.private)
                update(data)
                sign()
            }.also { lastDer = it }
        }

        KeyType.ED25519 -> {
            assertArrayEquals(data, input)
            Signature.getInstance("Ed25519").run {
                initSign(pair.private)
                update(data)
                sign()
            }
        }

        else -> {
            // The raw RSA private-key operation. The padded block must be exactly what a JCA signature signs.
            val private = pair.private as RSAPrivateKey
            val raw = BigInteger(1, input).modPow(private.privateExponent, private.modulus).toByteArray().let {
                val length = (private.modulus.bitLength() + 7) / 8
                ByteArray(length).also { padded -> it.takeLast(minOf(it.size, length)).toByteArray().copyInto(padded, length - minOf(it.size, length)) }
            }
            val references = listOf("SHA256withRSA", "SHA512withRSA").map { algorithm ->
                Signature.getInstance(algorithm).run {
                    initSign(private)
                    update(data)
                    sign()
                }
            }
            assertTrue("padded block is not a PKCS#1 v1.5 signature input", references.any { it.contentEquals(raw) })
            raw
        }
    }

    /**
     * [layout] maps slots to the key they hold, and slots missing from it are empty. Without a layout every
     * slot holds [pair].
     */
    private fun session(
        policy: PinPolicy = PinPolicy.ONCE,
        pair: KeyPair = key,
        type: KeyType = keyTypeOf(pair),
        layout: Map<Slot, KeyPair>? = null,
    ): PivSession {
        val session = mock(PivSession::class.java)
        doReturn(true).`when`(session).supports(PivSession.FEATURE_METADATA)
        if (layout == null) {
            doReturn(metadata(pair, type, policy)).`when`(session).getSlotMetadata(any(Slot::class.java))
        } else {
            doAnswer {
                val held = layout[it.getArgument<Slot>(0)] ?: throw ApduException(byteArrayOf(), SW.REFERENCED_DATA_NOT_FOUND)
                metadata(held, keyTypeOf(held), policy)
            }.`when`(session).getSlotMetadata(any(Slot::class.java))
        }
        doAnswer {
            cardSign(layout?.get(it.getArgument<Slot>(0)) ?: pair, it.getArgument(1), it.getArgument(2))
        }.`when`(session).rawSignOrDecrypt(any(Slot::class.java), any(KeyType::class.java), any(ByteArray::class.java))
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

    private suspend fun sign(
        pin: String = "123456",
        expected: KeyPair = key,
        algorithm: String? = null,
        slot: Slot = Slot.AUTHENTICATION,
    ): ByteArray = client.sign(activity, slot, expected.public, pin.toCharArray(), data, algorithm)

    private fun sshStrings(blob: ByteArray): List<ByteArray> {
        val buffer = ByteBuffer.wrap(blob)
        return buildList { while (buffer.hasRemaining()) add(ByteArray(buffer.int).also(buffer::get)) }
    }

    private fun generate(algorithm: String, init: KeyPairGenerator.() -> Unit): KeyPair = KeyPairGenerator.getInstance(algorithm).apply(init).generateKeyPair()

    private val p384Key by lazy { generate("EC") { initialize(ECGenParameterSpec("secp384r1")) } }
    private val rsaKey by lazy { generate("RSA") { initialize(2048) } }
    private val ed25519Key by lazy { generate("Ed25519") {} }

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
        assertEquals(listOf(SshSigning.encodePublicKey(key)), YubiKeyPiv.offers(key.public))
        assertEquals(listOf("ecdsa-sha2-nistp384"), YubiKeyPiv.offers(p384Key.public).map { it.algorithmName })
        assertEquals(listOf("ssh-ed25519"), YubiKeyPiv.offers(ed25519Key.public).map { it.algorithmName })
        // RSA is offered with SHA-2 only, both under the same key blob.
        val rsaOffers = YubiKeyPiv.offers(rsaKey.public)
        assertEquals(listOf("rsa-sha2-512", "rsa-sha2-256"), rsaOffers.map { it.algorithmName })
        assertEquals(1, rsaOffers.map { it.publicKeyBlob.toList() }.distinct().size)
        assertEquals(SshSigning.encodePublicKey(rsaKey).publicKeyBlob.toList(), rsaOffers.first().publicKeyBlob.toList())
        val unsupported = assertThrows(YubiKeyPiv.UnsupportedKey::class.java) {
            YubiKeyPiv.offers(generate("RSA") { initialize(1024) }.public)
        }
        assertEquals(YubiKeyPiv.UnsupportedKey.Reason.ALGORITHM, unsupported.reason)
        assertEquals("RSA 1024", unsupported.algorithm)
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
    fun unsupportedKeyTypeInSlotNamesTheAlgorithm() = runBlocking<Unit> {
        val card = usb()
        val metadata = mock(SlotMetadata::class.java)
        doReturn(KeyType.RSA1024).`when`(metadata).keyType
        doReturn(metadata).`when`(card.session).getSlotMetadata(any(Slot::class.java))
        val failure = failure<YubiKeyPiv.UnsupportedKey> { sign() }
        assertEquals(YubiKeyPiv.UnsupportedKey.Reason.ALGORITHM, failure.reason)
        assertEquals("RSA 1024", failure.algorithm)
        verify(card.session, never()).verifyPin(any(CharArray::class.java))
    }

    @Test
    fun missingMetadataSupportIsReportedAsFirmware() = runBlocking<Unit> {
        val card = usb()
        doReturn(false).`when`(card.session).supports(PivSession.FEATURE_METADATA)
        assertEquals(YubiKeyPiv.UnsupportedKey.Reason.FIRMWARE, failure<YubiKeyPiv.UnsupportedKey> { sign() }.reason)
    }

    @Test
    fun unsupportedSavedKeyIsRejectedBeforeTheCardIsUsed() = runBlocking<Unit> {
        usb()
        val p521 = generate("EC") { initialize(ECGenParameterSpec("secp521r1")) }
        val failure = failure<YubiKeyPiv.UnsupportedKey> { sign(expected = p521) }
        assertEquals("EC P-521", failure.algorithm)
        verify(manager, never()).startUsbDiscovery(any(), any())
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
    fun p384SignsTheSha384DigestAndEncodesAnSshSignature() = runBlocking<Unit> {
        usb(pair = p384Key)
        val blob = sign(expected = p384Key)
        assertArrayEquals(SshSigning.encodeEcdsaSignature("ecdsa-sha2-nistp384", lastDer!!), blob)
        assertEquals("ecdsa-sha2-nistp384", String(sshStrings(blob).first()))
    }

    @Test
    fun rsaSignsWithTheRequestedSha2Algorithm() = runBlocking<Unit> {
        usb(pair = rsaKey)
        for ((name, jca) in listOf("rsa-sha2-256" to "SHA256withRSA", "rsa-sha2-512" to "SHA512withRSA")) {
            val strings = sshStrings(sign(expected = rsaKey, algorithm = name))
            assertEquals(2, strings.size)
            assertEquals(name, String(strings[0]))
            assertTrue(
                Signature.getInstance(jca).run {
                    initVerify(rsaKey.public)
                    update(data)
                    verify(strings[1])
                },
            )
        }
    }

    @Test
    fun rsaDefaultsToSha512AndNeverSignsWithSha1() = runBlocking<Unit> {
        usb(pair = rsaKey)
        assertEquals("rsa-sha2-512", String(sshStrings(sign(expected = rsaKey)).first()))
        val failure = failure<YubiKeyPiv.UnsupportedKey> { sign(expected = rsaKey, algorithm = "ssh-rsa") }
        assertEquals(YubiKeyPiv.UnsupportedKey.Reason.ALGORITHM, failure.reason)
    }

    @Test
    fun ed25519SignsTheMessageItself() = runBlocking<Unit> {
        usb(pair = ed25519Key)
        val strings = sshStrings(sign(expected = ed25519Key))
        assertEquals("ssh-ed25519", String(strings[0]))
        assertTrue(
            Signature.getInstance("Ed25519").run {
                initVerify(ed25519Key.public)
                update(data)
                verify(strings[1])
            },
        )
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
        val card = usb(layout = mapOf(Slot.SIGNATURE to newKey(), Slot.RETIRED1 to p384Key, Slot.RETIRED2 to rsaKey))
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
