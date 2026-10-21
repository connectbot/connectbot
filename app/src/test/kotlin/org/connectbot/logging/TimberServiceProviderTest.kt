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

package org.connectbot.logging

import android.util.Log
import org.connectbot.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.slf4j.LoggerFactory
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
class TimberServiceProviderTest {
    private data class LogEvent(val priority: Int, val message: String, val throwable: Throwable?)

    private val events = mutableListOf<LogEvent>()
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            events.add(LogEvent(priority, "$tag: $message", t))
        }
    }
    private val localTree = LocalTree()

    @Before
    fun setUp() {
        Timber.plant(tree, localTree)
        synchronized(LocalTree.logBuffer) { LocalTree.logBuffer.clear() }
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
        Timber.uproot(localTree)
        synchronized(LocalTree.logBuffer) { LocalTree.logBuffer.clear() }
    }

    @Test
    fun `discovered provider sends formatted library logs to the log viewer`() {
        val logger = LoggerFactory.getLogger("org.connectbot.sshlib.client.SshConnection")
        logger.info("Requesting service: {} (100%)", "ssh-userauth")
        assertEquals(Log.INFO, events.single().priority)
        assertEquals("org.connectbot.sshlib.client.SshConnection: Requesting service: ssh-userauth (100%)", events.single().message)
        assertNull(events.single().throwable)
        synchronized(LocalTree.logBuffer) {
            assertTrue(LocalTree.logBuffer.single().contains(events.single().message))
        }
    }

    @Test
    fun `library failure preserves exception and severity`() {
        val logger = LoggerFactory.getLogger("cbssh")
        val failure = IllegalStateException("test failure")
        logger.error("Failed during {}", "authentication", failure)
        assertEquals(Log.ERROR, events.single().priority)
        assertTrue(events.single().message.contains("Failed during authentication"))
        assertSame(failure, events.single().throwable)
        synchronized(LocalTree.logBuffer) {
            assertTrue(LocalTree.logBuffer.single().contains("IllegalStateException: test failure"))
        }
    }

    @Test
    fun `provider reuses loggers by name across initialization`() {
        val provider = TimberServiceProvider()
        val factory = provider.getLoggerFactory()
        val logger = factory.getLogger("cbssh")

        provider.initialize()

        assertSame(factory, provider.getLoggerFactory())
        assertSame(logger, provider.getLoggerFactory().getLogger("cbssh"))
        assertNotSame(logger, factory.getLogger("cbtelnet"))
        assertEquals("cbssh", logger.name)
        assertEquals("cbtelnet", factory.getLogger("cbtelnet").name)
        assertTrue(provider.getRequestedApiVersion().startsWith("2.0."))
    }

    @Test
    fun `provider supplies reusable markers and a no-op MDC`() {
        val provider = TimberServiceProvider()
        val markers = provider.getMarkerFactory()
        assertSame(markers, provider.getMarkerFactory())
        assertSame(markers.getMarker("connection"), markers.getMarker("connection"))
        assertEquals("connection", markers.getMarker("connection").name)

        val mdc = provider.getMDCAdapter()
        assertSame(mdc, provider.getMDCAdapter())
        mdc.put("connection", "secret")
        assertNull(mdc.get("connection"))
        assertNull(mdc.copyOfContextMap)
    }

    @Test
    fun `level enablement follows build configuration with or without markers`() {
        val provider = TimberServiceProvider()
        val logger = provider.getLoggerFactory().getLogger("cbssh")
        val marker = provider.getMarkerFactory().getMarker("connection")

        assertEquals(BuildConfig.DEBUG, logger.isTraceEnabled)
        assertEquals(BuildConfig.DEBUG, logger.isDebugEnabled)
        assertTrue(logger.isInfoEnabled)
        assertTrue(logger.isWarnEnabled)
        assertTrue(logger.isErrorEnabled)
        assertEquals(BuildConfig.DEBUG, logger.isTraceEnabled(marker))
        assertEquals(BuildConfig.DEBUG, logger.isDebugEnabled(marker))
        assertTrue(logger.isInfoEnabled(marker))
        assertTrue(logger.isWarnEnabled(marker))
        assertTrue(logger.isErrorEnabled(marker))
    }

    @Test
    fun `trace and debug reach Timber only in debug builds and stay out of the log viewer`() {
        val logger = LoggerFactory.getLogger("cbssh")

        logger.trace("Received {} bytes", 42)
        logger.debug("Negotiated {}", "ssh-ed25519")

        val expected = if (BuildConfig.DEBUG) {
            listOf(
                LogEvent(Log.VERBOSE, "cbssh: Received 42 bytes", null),
                LogEvent(Log.DEBUG, "cbssh: Negotiated ssh-ed25519", null),
            )
        } else {
            emptyList()
        }
        assertEquals(expected, events)
        synchronized(LocalTree.logBuffer) {
            assertTrue(LocalTree.logBuffer.isEmpty())
        }
    }

    @Test
    fun `marked warning preserves severity and formats all arguments`() {
        val provider = TimberServiceProvider()
        val logger = provider.getLoggerFactory().getLogger("cbssh")
        val marker = provider.getMarkerFactory().getMarker("connection")

        logger.warn(marker, "Retry {} of {} for {}", 1, 3, "example.org")

        assertEquals(LogEvent(Log.WARN, "cbssh: Retry 1 of 3 for example.org", null), events.single())
        synchronized(LocalTree.logBuffer) {
            assertTrue(LocalTree.logBuffer.single().contains(events.single().message))
        }
    }

    @Test
    fun `plain messages preserve percent signs without formatting arguments`() {
        val logger = LoggerFactory.getLogger("cbssh")

        logger.info("Progress: 100% (%s)")

        assertEquals(LogEvent(Log.INFO, "cbssh: Progress: 100% (%s)", null), events.single())
    }

    @Test
    fun `explicit throwable is forwarded without message arguments`() {
        val logger = LoggerFactory.getLogger("cbssh")
        val failure = IllegalStateException("connection closed")

        logger.warn("Connection failed", failure)

        assertEquals(Log.WARN, events.single().priority)
        assertTrue(events.single().message.startsWith("cbssh: Connection failed\n"))
        assertSame(failure, events.single().throwable)
        synchronized(LocalTree.logBuffer) {
            assertTrue(LocalTree.logBuffer.single().contains("IllegalStateException: connection closed"))
        }
    }
}
