/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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
import com.yubico.yubikit.android.YubiKitManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class Ctap2DiscoveryTest {
    @Test
    fun repeatedUsbStartIsIdempotentAndCloseStopsDiscovery() {
        mockConstruction(YubiKitManager::class.java).use { sdk ->
            var disconnects = 0
            val discovery = Ctap2Discovery(
                mock(Context::class.java),
                { _, _ -> },
                { },
                { disconnects++ },
                MutableStateFlow(Fido2ConnectionState.Disconnected),
            )
            discovery.startUsbDiscovery()
            discovery.startUsbDiscovery()
            discovery.close()
            val manager = sdk.constructed().single()
            verify(manager, times(1)).startUsbDiscovery(any(), any())
            verify(manager).stopUsbDiscovery()
            assertEquals(1, disconnects)
        }
    }

    @Test
    fun closeStopsNfcDiscoveryForActiveActivity() {
        mockConstruction(YubiKitManager::class.java).use { sdk ->
            val activity = mock(Activity::class.java)
            val discovery = Ctap2Discovery(
                mock(Context::class.java),
                { _, _ -> },
                { },
                { },
                MutableStateFlow(Fido2ConnectionState.Disconnected),
            )
            discovery.startNfcDiscovery(activity)
            discovery.close()
            verify(sdk.constructed().single()).stopNfcDiscovery(activity)
        }
    }

    @Test
    fun failedStopDoesNotRetainActivity() {
        val settings = org.mockito.Mockito.withSettings().defaultAnswer { call ->
            if (call.method.name == "stopNfcDiscovery") throw IllegalStateException("reader unavailable")
            org.mockito.Answers.RETURNS_DEFAULTS.answer(call)
        }
        mockConstruction(YubiKitManager::class.java, settings).use { sdk ->
            val activity = mock(Activity::class.java)
            val discovery = Ctap2Discovery(
                mock(Context::class.java),
                { _, _ -> },
                { },
                { },
                MutableStateFlow(Fido2ConnectionState.Disconnected),
            )
            discovery.startNfcDiscovery(activity)
            discovery.stopNfcDiscovery(activity)
            discovery.close()
            verify(sdk.constructed().single(), times(1)).stopNfcDiscovery(activity)
        }
    }
}
