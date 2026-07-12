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

package org.connectbot.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.connectbot.di.CoroutineDispatchers
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** A resolved SSH service advertised over DNS-SD/mDNS. */
data class DiscoveredSshServer(
    val key: String,
    val serviceName: String,
    val hostname: String,
    val port: Int,
    val username: String?,
)

sealed interface SshDiscoveryEvent {
    data class Found(val server: DiscoveredSshServer) : SshDiscoveryEvent

    data class Lost(val key: String) : SshDiscoveryEvent

    data class Failed(val errorCode: Int?) : SshDiscoveryEvent
}

interface SshServiceDiscovery {
    /** Discover services until collection is cancelled. */
    fun discover(): Flow<SshDiscoveryEvent>
}

/** Android Network Service Discovery implementation for `_ssh._tcp`. */
@Singleton
class AndroidSshServiceDiscovery @Inject constructor(
    @ApplicationContext context: Context,
    private val dispatchers: CoroutineDispatchers,
) : SshServiceDiscovery {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    @Suppress("DEPRECATION")
    override fun discover(): Flow<SshDiscoveryEvent> = callbackFlow {
        val servicesToResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val discoveryActive = AtomicBoolean(false)
        val unavailableServices = ConcurrentHashMap.newKeySet<String>()

        val resolutionJob = launch {
            for (serviceInfo in servicesToResolve) {
                resolve(serviceInfo)?.toDiscoveredServer()?.let { server ->
                    if (server.key !in unavailableServices) {
                        trySend(SshDiscoveryEvent.Found(server))
                    }
                }
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                discoveryActive.set(true)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.normalizedServiceType() == SSH_SERVICE_TYPE.normalizedServiceType()) {
                    unavailableServices.remove(serviceInfo.discoveryKey())
                    servicesToResolve.trySend(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val key = serviceInfo.discoveryKey()
                unavailableServices.add(key)
                trySend(SshDiscoveryEvent.Lost(key))
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryActive.set(false)
                trySend(SshDiscoveryEvent.Failed(errorCode))
                close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onDiscoveryStopped(serviceType: String) {
                discoveryActive.set(false)
            }
        }

        try {
            discoveryActive.set(true)
            nsdManager.discoverServices(SSH_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: RuntimeException) {
            discoveryActive.set(false)
            trySend(SshDiscoveryEvent.Failed(null))
            close()
        }

        awaitClose {
            servicesToResolve.close()
            resolutionJob.cancel()
            if (discoveryActive.getAndSet(false)) {
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (_: RuntimeException) {
                    // The framework can race a stop callback with cancellation.
                }
            }
        }
    }.flowOn(dispatchers.io)

    @Suppress("DEPRECATION")
    private suspend fun resolve(serviceInfo: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            try {
                nsdManager.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {
                        override fun onServiceResolved(resolvedService: NsdServiceInfo) {
                            if (continuation.isActive) continuation.resume(resolvedService)
                        }

                        override fun onResolveFailed(failedService: NsdServiceInfo, errorCode: Int) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            } catch (_: RuntimeException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun NsdServiceInfo.toDiscoveredServer(): DiscoveredSshServer? {
        val port = port.takeIf { it in 1..65535 } ?: return null
        val resolvedAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hostAddresses.firstOrNull { it is Inet4Address } ?: hostAddresses.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            host
        }
        val rawHostname = resolvedAddress?.hostName?.takeIf { it.isNotBlank() } ?: return null
        val normalizedHostname = rawHostname.trim().removeSuffix(".").let { hostname ->
            if (':' in hostname && !hostname.startsWith('[')) "[$hostname]" else hostname
        }
        if (normalizedHostname.isBlank()) return null

        val name = serviceName.trim().takeIf { it.isNotEmpty() } ?: normalizedHostname
        val username = attributes[USERNAME_ATTRIBUTE]
            ?.toString(Charsets.UTF_8)
            ?.trim()
            ?.takeIf { value ->
                value.isNotEmpty() && value.length <= MAX_USERNAME_LENGTH &&
                    value.none { it.isISOControl() || it == '@' }
            }

        return DiscoveredSshServer(
            key = discoveryKey(),
            serviceName = name,
            hostname = normalizedHostname,
            port = port,
            username = username,
        )
    }

    private fun NsdServiceInfo.discoveryKey(): String =
        "${serviceName.trim()}\u0000${serviceType.normalizedServiceType()}"

    private fun String.normalizedServiceType(): String = trim().trimEnd('.').lowercase()

    private companion object {
        const val SSH_SERVICE_TYPE = "_ssh._tcp."
        const val USERNAME_ATTRIBUTE = "u"
        const val MAX_USERNAME_LENGTH = 255
    }
}
