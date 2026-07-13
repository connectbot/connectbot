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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

interface TailscaleNetworkDetector {
    /** Whether ConnectBot's active network is a Tailscale VPN. */
    fun isActive(): Boolean
}

@Singleton
class AndroidTailscaleNetworkDetector @Inject constructor(
    @ApplicationContext context: Context,
) : TailscaleNetworkDetector {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isActive(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return false
            isTailscaleVpn(
                isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                linkAddresses = linkProperties.linkAddresses.map { it.address },
                dnsServers = linkProperties.dnsServers,
            )
        } catch (e: RuntimeException) {
            Timber.w(e, "Unable to determine Tailscale network state")
            false
        }
    }

    internal companion object {
        private val MAGIC_DNS_ADDRESSES = setOf("100.100.100.100", "fd7a:115c:a1e0::53")

        fun isTailscaleVpn(
            isVpn: Boolean,
            linkAddresses: Iterable<InetAddress>,
            dnsServers: Iterable<InetAddress>,
        ): Boolean = isVpn &&
            (
                linkAddresses.any { TailscaleResolver.isTailscaleIp(it) } ||
                    dnsServers.any { it.hostAddress?.substringBefore('%')?.lowercase() in MAGIC_DNS_ADDRESSES }
                )
    }
}
