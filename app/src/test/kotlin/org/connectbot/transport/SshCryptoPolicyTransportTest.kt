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

package org.connectbot.transport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.HostCryptoConfig
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.sshlib.AlgorithmPolicyWarningReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SshCryptoPolicyTransportTest {
    @Test
    fun unavailableEntriesAreRetainedForConnectionInfoAfterClose() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        val host = Host(hostname = "example.com", crypto = HostCryptoConfig(preset = "CUSTOM", kex = "+future@example.com"))
        val ssh = SSH(host, null, null)
        assertNotNull(ssh.resolveCryptoPolicy(host, dispatchers))
        ssh.close()
        val details = ssh.getSshPolicyDetails().single()
        assertEquals("CUSTOM", details.preset)
        assertEquals(2048, details.minimumRsaBits)
        assertEquals("future@example.com", details.warnings.single().algorithm)
        assertEquals(AlgorithmPolicyWarningReason.UNSUPPORTED, details.warnings.single().reason)
    }

    @Test
    fun emptyExactListResolutionFailsWithoutFallingBackAndKeepsDiagnostics() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        val host = Host(hostname = "example.com", crypto = HostCryptoConfig(preset = "CUSTOM", cipher = "future@example.com"))
        val ssh = SSH(host, null, null)
        assertNull(ssh.resolveCryptoPolicy(host, dispatchers))
        assertNotNull(ssh.getSshPolicyDetails().single().error)
    }

    @Test
    fun eachHopKeepsItsOwnPresetAndLimits() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        val destination = Host(hostname = "destination", crypto = HostCryptoConfig(preset = "FUTURE"))
        val jump = Host(hostname = "jump", crypto = HostCryptoConfig(preset = "LEGACY"))
        val ssh = SSH(destination, null, null)
        ssh.resolveCryptoPolicy(jump, dispatchers)
        ssh.resolveCryptoPolicy(destination, dispatchers)
        val details = ssh.getSshPolicyDetails().associateBy { it.host }
        assertEquals(1024, details.getValue("jump").minimumRsaBits)
        assertEquals(3072, details.getValue("destination").minimumRsaBits)
    }

    @Test
    fun invalidPresetRetainsFailureAndReportsItToTheTerminal() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = mock(TerminalManager::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(manager.res).thenReturn(context.resources)
        val host = Host(hostname = "example.com", crypto = HostCryptoConfig(preset = "UNKNOWN"))
        val ssh = SSH(host, bridge, manager)
        assertNull(ssh.resolveCryptoPolicy(host, CoroutineDispatchers(dispatcher, dispatcher, dispatcher)))
        val details = ssh.getSshPolicyDetails().single()
        assertEquals("example.com", details.host)
        assertNotNull(details.error)
        verify(bridge).outputLine(details.error!!)
    }

    @Test
    fun warningsAndEmptyCategoriesAreReportedWithLocalizedDiagnostics() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = mock(TerminalManager::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(manager.res).thenReturn(context.resources)
        val host = Host(nickname = "My server", hostname = "example.com", crypto = HostCryptoConfig(preset = "CUSTOM", cipher = "future@example.com"))
        val ssh = SSH(host, bridge, manager)
        assertNull(ssh.resolveCryptoPolicy(host, CoroutineDispatchers(dispatcher, dispatcher, dispatcher)))
        val details = ssh.getSshPolicyDetails().single()
        assertEquals("My server", details.host)
        val warning = details.warnings.single()
        verify(bridge).outputLine(context.getString(R.string.crypto_connection_warning, "My server", context.getString(R.string.crypto_category_cipher), warning.algorithm, context.getString(R.string.crypto_reason_unsupported)))
        verify(bridge).outputLine(details.error!!)
    }

    @Test
    fun invalidPresetWithoutTerminalStillRetainsFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = Host(hostname = "example.com", crypto = HostCryptoConfig(preset = "UNKNOWN"))
        val ssh = SSH(host, null, null)
        assertNull(ssh.resolveCryptoPolicy(host, CoroutineDispatchers(dispatcher, dispatcher, dispatcher)))
        assertNotNull(ssh.getSshPolicyDetails().single().error)
    }
}
