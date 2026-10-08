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

package org.connectbot.ui.screens.help

import android.annotation.TargetApi
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.connectbot.data.LogRepository
import org.connectbot.di.CoroutineDispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LogViewerViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val applicationContext = ApplicationProvider.getApplicationContext<Context>()
    private val context = spy(applicationContext)
    private val activityManager = mock(ActivityManager::class.java)

    private fun createViewModel(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        logRepository: LogRepository = LogRepository(),
    ): LogViewerViewModel {
        `when`(context.packageName).thenReturn("org.connectbot")
        `when`(context.getSystemService(ActivityManager::class.java)).thenReturn(activityManager)
        `when`(context.contentResolver).thenReturn(applicationContext.contentResolver)
        return LogViewerViewModel(logRepository, context, CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
    }

    @TargetApi(30)
    private fun crash(timestamp: Long, reason: Int = ApplicationExitInfo.REASON_CRASH_NATIVE): ApplicationExitInfo = mock(ApplicationExitInfo::class.java).also {
        `when`(it.timestamp).thenReturn(timestamp)
        `when`(it.reason).thenReturn(reason)
    }

    @Test
    @TargetApi(30)
    fun savesNewestNativeTraceWithoutChangingBytesAndClosesStream() = runTest {
        val viewModel = createViewModel(StandardTestDispatcher(testScheduler))
        val older = crash(1)
        val newest = crash(2)
        val anr = crash(3, ApplicationExitInfo.REASON_ANR)
        val bytes = byteArrayOf(0, 10, -1, 127, -128)
        var closed = false
        val input = object : ByteArrayInputStream(bytes) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        `when`(newest.traceInputStream).thenReturn(input)
        `when`(activityManager.getHistoricalProcessExitReasons("org.connectbot", 0, 0))
            .thenReturn(listOf(older, anr, newest))
        val file = temporaryFolder.newFile("tombstone.pb")
        file.writeText("existing content that must be truncated")

        assertEquals(TombstoneSaveResult.SAVED, viewModel.saveTombstone(Uri.fromFile(file)))
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(true, closed)
        verify(older, never()).traceInputStream
        verify(anr, never()).traceInputStream
    }

    @Test
    @TargetApi(30)
    fun skipsRemovedTraceAndExportsOlderRetainedTrace() = runTest {
        val viewModel = createViewModel(StandardTestDispatcher(testScheduler))
        val removed = crash(2)
        val retained = crash(1)
        val bytes = byteArrayOf(8, 1)
        `when`(retained.traceInputStream).thenReturn(ByteArrayInputStream(bytes))
        `when`(activityManager.getHistoricalProcessExitReasons("org.connectbot", 0, 0))
            .thenReturn(listOf(removed, retained))
        val file = temporaryFolder.newFile()

        assertEquals(TombstoneSaveResult.SAVED, viewModel.saveTombstone(Uri.fromFile(file)))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    @TargetApi(30)
    fun missingTraceDoesNotOverwriteDestination() = runTest {
        val viewModel = createViewModel(StandardTestDispatcher(testScheduler))
        val nativeCrash = crash(1)
        `when`(activityManager.getHistoricalProcessExitReasons("org.connectbot", 0, 0))
            .thenReturn(listOf(nativeCrash))
        val file = temporaryFolder.newFile()
        file.writeText("preserve")

        assertEquals(TombstoneSaveResult.UNAVAILABLE, viewModel.saveTombstone(Uri.fromFile(file)))
        assertEquals("preserve", file.readText())
    }

    @Test
    @TargetApi(30)
    fun readFailureReturnsFailure() = runTest {
        val viewModel = createViewModel(StandardTestDispatcher(testScheduler))
        val nativeCrash = crash(1)
        `when`(nativeCrash.traceInputStream).thenThrow(IOException("unavailable"))
        `when`(activityManager.getHistoricalProcessExitReasons("org.connectbot", 0, 0))
            .thenReturn(listOf(nativeCrash))

        assertEquals(TombstoneSaveResult.FAILED, viewModel.saveTombstone(Uri.fromFile(temporaryFolder.newFile())))
    }

    @Test
    @TargetApi(30)
    fun writeFailureClosesTrace() = runTest {
        val viewModel = createViewModel(StandardTestDispatcher(testScheduler))
        val nativeCrash = crash(1)
        var closed = false
        val input = object : ByteArrayInputStream(byteArrayOf(8, 1)) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        `when`(nativeCrash.traceInputStream).thenReturn(input)
        `when`(activityManager.getHistoricalProcessExitReasons("org.connectbot", 0, 0))
            .thenReturn(listOf(nativeCrash))

        assertEquals(TombstoneSaveResult.FAILED, viewModel.saveTombstone(Uri.fromFile(temporaryFolder.root)))
        assertEquals(true, closed)
    }

    @Test
    @Config(sdk = [30])
    @TargetApi(30)
    fun android11DoesNotAttemptToReadNativeTombstone() = runTest {
        val viewModel = createViewModel(StandardTestDispatcher(testScheduler))

        assertEquals(TombstoneSaveResult.UNAVAILABLE, viewModel.saveTombstone(Uri.fromFile(temporaryFolder.newFile())))
        verify(activityManager, never()).getHistoricalProcessExitReasons("org.connectbot", 0, 0)
    }

    @Test
    @Config(sdk = [30])
    @TargetApi(30)
    fun logsIncludeExitMetadataNewestFirstWithoutReadingTraces() = runTest {
        val repository = mock(LogRepository::class.java)
        `when`(repository.getLogs()).thenReturn("existing application log")
        val viewModel = createViewModel(StandardTestDispatcher(testScheduler), repository)
        val older = crash(1000, ApplicationExitInfo.REASON_ANR)
        val newest = crash(2000)
        `when`(newest.processName).thenReturn("org.connectbot")
        `when`(newest.pid).thenReturn(42)
        `when`(newest.status).thenReturn(6)
        `when`(newest.description).thenReturn("native abort")
        `when`(newest.pss).thenReturn(1024L)
        `when`(newest.rss).thenReturn(2048L)
        `when`(activityManager.getHistoricalProcessExitReasons("org.connectbot", 0, 10))
            .thenReturn(listOf(older, newest))

        viewModel.loadLogs()

        val logs = viewModel.uiState.value.logs
        assertTrue(logs.startsWith("existing application log\n\nRecent process exits\n"))
        assertTrue(logs.contains("Time: 1970-01-01 00:00:02 UTC"))
        assertTrue(logs.contains("Process: org.connectbot (PID 42)"))
        assertTrue(logs.contains("Reason: CRASH_NATIVE (5)"))
        assertTrue(logs.contains("Status/signal: 6"))
        assertTrue(logs.contains("Description: native abort"))
        assertTrue(logs.contains("PSS 1024 kB, RSS 2048 kB"))
        assertTrue(logs.contains("Description: No description available"))
        assertTrue(logs.indexOf("CRASH_NATIVE") < logs.indexOf("ANR"))
        verify(newest, never()).traceInputStream
        verify(older, never()).traceInputStream
    }

    @Test
    @TargetApi(30)
    fun emptyExitHistoryKeepsApplicationLogs() = runTest {
        val repository = mock(LogRepository::class.java)
        `when`(repository.getLogs()).thenReturn("existing log")
        val viewModel = createViewModel(StandardTestDispatcher(testScheduler), repository)
        `when`(activityManager.getHistoricalProcessExitReasons("org.connectbot", 0, 10)).thenReturn(emptyList())

        viewModel.loadLogs()

        assertEquals("existing log\n\nRecent process exits\nNo process exit records available", viewModel.uiState.value.logs)
    }

    @Test
    @TargetApi(30)
    fun deniedExitHistoryKeepsApplicationLogs() = runTest {
        val repository = mock(LogRepository::class.java)
        `when`(repository.getLogs()).thenReturn("existing log")
        val viewModel = createViewModel(StandardTestDispatcher(testScheduler), repository)
        `when`(activityManager.getHistoricalProcessExitReasons("org.connectbot", 0, 10)).thenThrow(SecurityException())

        viewModel.loadLogs()

        assertEquals("existing log\n\nRecent process exits\nProcess exit history unavailable", viewModel.uiState.value.logs)
    }

    @Test
    @Config(sdk = [29])
    fun android10ShowsOnlyApplicationLogs() = runTest {
        val repository = mock(LogRepository::class.java)
        `when`(repository.getLogs()).thenReturn("existing log")
        val viewModel = createViewModel(StandardTestDispatcher(testScheduler), repository)

        viewModel.loadLogs()

        assertEquals("existing log", viewModel.uiState.value.logs)
        verifyNoInteractions(activityManager)
    }
}
