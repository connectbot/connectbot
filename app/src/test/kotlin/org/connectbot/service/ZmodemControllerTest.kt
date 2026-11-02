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

package org.connectbot.service

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.transport.AbsTransport
import org.connectbot.util.PreferenceConstants
import org.connectbot.zmodemlib.Framing
import org.connectbot.zmodemlib.Header
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.io.IOException
import java.time.Duration
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class ZmodemControllerTest {
    private class Fixture(val controller: ZmodemController, val bridge: TerminalBridge, val transport: AbsTransport, val writes: MutableList<ByteArray>)

    private suspend fun TestScope.fixture(): Fixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = mock(TerminalManager::class.java)
        `when`(manager.prefs).thenReturn(context.getSharedPreferences("zmodem-" + UUID.randomUUID(), Context.MODE_PRIVATE))
        val bridge = mock(TerminalBridge::class.java)
        val transport = mock(AbsTransport::class.java)
        `when`(bridge.manager).thenReturn(manager)
        `when`(manager.contentResolver).thenReturn(context.contentResolver)
        `when`(manager.cacheDir).thenReturn(context.cacheDir)
        `when`(bridge.transport).thenReturn(transport)
        `when`(transport.supportsZmodem).thenReturn(true)
        val writes = mutableListOf<ByteArray>()
        doAnswer {
            assertSame(transport, it.getArgument<AbsTransport>(2))
            writes.add(it.getArgument<ByteArray>(0))
            null
        }.`when`(bridge).writeTransfer(any(ByteArray::class.java) ?: byteArrayOf(), anyBoolean(), any(AbsTransport::class.java) ?: transport)
        val dispatcher = StandardTestDispatcher(testScheduler)
        return Fixture(ZmodemController(bridge, backgroundScope, CoroutineDispatchers(dispatcher, dispatcher, dispatcher)), bridge, transport, writes)
    }

    @Test
    fun cancelDuringFileSelectionSendsAbortAndKeepsConnection() = runTest {
        val f = fixture()
        val job = launch { f.controller.accept(f.transport, Framing.header(Header(Framing.ZRINIT))) }
        runCurrent()
        assertNotNull(f.controller.state.value.picker)
        f.controller.cancel()
        runCurrent()
        job.join()
        assertFalse(f.controller.active)
        assertArrayEquals(Framing.cancelSequence(), f.writes.single())
        verify(f.bridge, never()).dispatchDisconnect(any(DisconnectReason::class.java) ?: DisconnectReason.UNKNOWN)
        val prompt = "shell> ".toByteArray()
        assertArrayEquals(prompt, f.controller.accept(f.transport, prompt))
        // A queued initialization header must not immediately reopen the cancelled picker.
        val stale = Framing.header(Header(Framing.ZRINIT))
        assertArrayEquals(stale, f.controller.accept(f.transport, stale))
        assertFalse(f.controller.active)
    }

    @Test
    fun dismissingThePickerUsesProtocolCancellation() = runTest {
        val f = fixture()
        val job = launch { f.controller.accept(f.transport, Framing.header(Header(Framing.ZRINIT))) }
        runCurrent()
        f.controller.selected(f.controller.state.value.picker!!, emptyList())
        runCurrent()
        job.join()
        assertFalse(f.controller.active)
        assertArrayEquals(Framing.cancelSequence(), f.writes.single())
        verify(f.bridge, never()).dispatchDisconnect(any(DisconnectReason::class.java) ?: DisconnectReason.UNKNOWN)
    }

    @Test
    fun localCancelDuringAnActiveTransferPreservesConnection() = runTest {
        val f = fixture()
        val job = launch { f.controller.accept(f.transport, Framing.header(Header(Framing.ZRQINIT))) }
        runCurrent()
        f.controller.selected(f.controller.state.value.picker!!, listOf(Uri.parse("content://documents/tree/test")))
        runCurrent()
        job.join()
        assertTrue(f.controller.active)
        f.controller.cancel()
        assertTrue(f.controller.state.value.cancelling)
        runCurrent()
        assertFalse(f.controller.active)
        assertArrayEquals(Framing.cancelSequence(), f.writes.last())
        assertArrayEquals("prompt".toByteArray(), f.controller.accept(f.transport, "prompt".toByteArray()))
        verify(f.bridge, never()).dispatchDisconnect(any(DisconnectReason::class.java) ?: DisconnectReason.UNKNOWN)
    }

    @Test
    fun remoteCancellationAlsoKeepsTheShellUsable() = runTest {
        val f = fixture()
        val job = launch { f.controller.accept(f.transport, Framing.header(Header(Framing.ZRQINIT))) }
        runCurrent()
        f.controller.selected(f.controller.state.value.picker!!, listOf(Uri.parse("content://documents/tree/test")))
        runCurrent()
        job.join()
        assertTrue(f.controller.active)
        f.controller.accept(f.transport, byteArrayOf(24, 24))
        f.controller.accept(f.transport, byteArrayOf(24, 24, 24))
        assertFalse(f.controller.active)
        assertTrue(f.controller.state.value.error == null)
        assertArrayEquals("prompt".toByteArray(), f.controller.accept(f.transport, "prompt".toByteArray()))
        verify(f.bridge, never()).dispatchDisconnect(any(DisconnectReason::class.java) ?: DisconnectReason.UNKNOWN)
    }
    private suspend fun TestScope.startDownload(f: Fixture) {
        val job = launch { f.controller.accept(f.transport, Framing.header(Header(Framing.ZRQINIT))) }
        runCurrent()
        f.controller.selected(f.controller.state.value.picker!!, listOf(Uri.parse("content://documents/tree/test")))
        runCurrent()
        job.join()
        assertTrue(f.controller.active)
    }

    @Test
    fun ignoresUnsupportedAndReplacedTransports() = runTest {
        val f = fixture()
        val bytes = Framing.header(Header(Framing.ZRQINIT))
        `when`(f.transport.supportsZmodem).thenReturn(false)
        assertArrayEquals(bytes, f.controller.accept(f.transport, bytes))
        assertArrayEquals(bytes, f.controller.accept(mock(AbsTransport::class.java), bytes))
        assertArrayEquals(byteArrayOf(), f.controller.finish(mock(AbsTransport::class.java)))
        assertFalse(f.controller.active)
        f.controller.cancel()
        assertTrue(f.writes.isEmpty())
    }

    @Test
    fun disabledDetectionFlushesBufferedTerminalBytes() = runTest {
        val f = fixture()
        val partial = byteArrayOf(42, 42, 24)
        assertArrayEquals(byteArrayOf(), f.controller.accept(f.transport, partial))
        f.bridge.manager.prefs.edit().putBoolean(PreferenceConstants.ZMODEM, false).commit()
        assertArrayEquals(partial + "prompt".toByteArray(), f.controller.accept(f.transport, "prompt".toByteArray()))
        assertFalse(f.controller.active)
    }

    @Test
    fun eofFlushesPartialDetection() = runTest {
        val f = fixture()
        val partial = byteArrayOf(42, 42, 24)
        f.controller.accept(f.transport, partial)
        assertArrayEquals(partial, f.controller.finish(f.transport))
    }

    @Test
    fun stalePickerResultIsIgnoredAndSelectionTimesOut() = runTest {
        val f = fixture()
        val job = launch { f.controller.accept(f.transport, Framing.header(Header(Framing.ZRINIT))) }
        runCurrent()
        f.controller.selected(UUID.randomUUID(), listOf(Uri.parse("content://wrong")))
        runCurrent()
        assertTrue(f.controller.active)
        assertNotNull(f.controller.state.value.picker)
        advanceTimeBy(120_001)
        runCurrent()
        job.join()
        assertEquals("Transfer timed out", f.controller.state.value.error)
        assertFalse(f.controller.active)
        assertArrayEquals(Framing.cancelSequence(), f.writes.single())
        f.controller.dismissError()
        assertNull(f.controller.state.value.error)
    }

    @Test
    fun disablingPreferenceCancelsActiveTransfer() = runTest {
        val f = fixture()
        startDownload(f)
        f.bridge.manager.prefs.edit().putBoolean(PreferenceConstants.ZMODEM, false).commit()
        runCurrent()
        assertFalse(f.controller.active)
        assertArrayEquals(Framing.cancelSequence(), f.writes.last())
    }

    @Test
    fun disconnectDuringSelectionCancelsWaitingCoroutine() = runTest {
        val f = fixture()
        val job = launch { f.controller.accept(f.transport, Framing.header(Header(Framing.ZRINIT))) }
        runCurrent()
        f.controller.disconnected()
        runCurrent()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(f.controller.active)
        assertNull(f.controller.state.value.picker)
    }

    @Test
    fun eofDuringTransferCleansUpAndReportsFailure() = runTest {
        val f = fixture()
        startDownload(f)
        try {
            f.controller.finish(f.transport)
            fail("Expected EOF failure")
        } catch (_: IOException) { }
        assertFalse(f.controller.active)
        assertEquals("Remote EOF", f.controller.state.value.error)
    }

    @Test
    fun transportWriteFailureDisconnectsCurrentConnection() = runTest {
        val f = fixture()
        doAnswer { throw IOException("broken pipe") }.`when`(f.bridge).writeTransfer(
            any(ByteArray::class.java) ?: byteArrayOf(),
            anyBoolean(),
            any(AbsTransport::class.java) ?: f.transport,
        )
        val job = launch { f.controller.accept(f.transport, Framing.header(Header(Framing.ZRQINIT))) }
        runCurrent()
        f.controller.selected(f.controller.state.value.picker!!, listOf(Uri.parse("content://documents/tree/test")))
        runCurrent()
        job.join()
        assertFalse(f.controller.active)
        assertEquals("Transfer transport write failed", f.controller.state.value.error)
        verify(f.bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }

    @Test
    fun binaryRevocationAbortsWithoutDisconnectWhenAbortWriteSucceeds() = runTest {
        val f = fixture()
        startDownload(f)
        doAnswer { throw IOException("BINARY revoked") }.`when`(f.transport).verifyTransfer()
        f.controller.accept(f.transport, byteArrayOf(1))
        assertFalse(f.controller.active)
        assertEquals("BINARY revoked", f.controller.state.value.error)
        assertArrayEquals(Framing.cancelSequence(), f.writes.last())
        verify(f.bridge, never()).dispatchDisconnect(any(DisconnectReason::class.java) ?: DisconnectReason.UNKNOWN)
    }

    @Test
    fun idleTransferRetriesThenTimesOut() = runTest {
        val f = fixture()
        startDownload(f)
        repeat(15) {
            ShadowSystemClock.advanceBy(Duration.ofSeconds(11))
            advanceTimeBy(1000)
            runCurrent()
        }
        assertFalse(f.controller.active)
        assertEquals("Transfer timed out", f.controller.state.value.error)
        assertArrayEquals(Framing.cancelSequence(), f.writes.last())
    }

    @Test
    fun retryWriteFailureDisconnectsAndStopsTimer() = runTest {
        val f = fixture()
        startDownload(f)
        doAnswer { throw IOException("broken retry") }.`when`(f.bridge).writeTransfer(
            any(ByteArray::class.java) ?: byteArrayOf(),
            anyBoolean(),
            any(AbsTransport::class.java) ?: f.transport,
        )
        ShadowSystemClock.advanceBy(Duration.ofSeconds(11))
        advanceTimeBy(1001)
        runCurrent()
        assertFalse(f.controller.active)
        assertEquals("Transfer transport write failed", f.controller.state.value.error)
        verify(f.bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }

    @Test
    fun transportCancellationIsReportedAsWriteFailure() = runTest {
        val f = fixture()
        startDownload(f)
        doAnswer { throw kotlinx.coroutines.CancellationException("write timed out") }.`when`(f.bridge).writeTransfer(
            any(ByteArray::class.java) ?: byteArrayOf(),
            anyBoolean(),
            any(AbsTransport::class.java) ?: f.transport,
        )
        f.controller.accept(f.transport, Framing.header(Header(Framing.ZRQINIT)))
        assertFalse(f.controller.active)
        assertEquals("Transfer transport write failed", f.controller.state.value.error)
        verify(f.bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }

    @Test
    fun failedAbortDisconnectsAndStillCleansUp() = runTest {
        val f = fixture()
        startDownload(f)
        doAnswer { throw IOException("broken abort") }.`when`(f.bridge).writeTransfer(
            any(ByteArray::class.java) ?: byteArrayOf(),
            anyBoolean(),
            any(AbsTransport::class.java) ?: f.transport,
        )
        f.controller.cancel()
        runCurrent()
        assertFalse(f.controller.active)
        assertNull(f.controller.state.value.error)
        verify(f.bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }

    @Test
    fun inputArrivingDuringCancellationDoesNotReachSession() = runTest {
        val f = fixture()
        startDownload(f)
        f.controller.cancel()
        assertArrayEquals(byteArrayOf(), f.controller.accept(f.transport, byteArrayOf(1, 2)))
        runCurrent()
        assertFalse(f.controller.active)
        assertArrayEquals(Framing.cancelSequence(), f.writes.last())
    }
}
