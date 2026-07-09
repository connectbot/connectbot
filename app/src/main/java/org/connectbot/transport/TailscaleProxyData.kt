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

import org.connectbot.util.TailscaleResolver
import java.net.InetAddress

/**
 * ProxyData implementation that resolves `*.ts.net` (Tailscale MagicDNS)
 * hostnames and opens a plain TCP socket to the resolved address, so host
 * keys stay bound to the MagicDNS name rather than a tailnet IP.
 *
 * @param resolver resolver used to look up the `.ts.net` name
 * @param ipVersion the host's IP version preference ([org.connectbot.util.HostConstants] value)
 * @param onResolved optional callback invoked with the resolved address, for status output
 */
class TailscaleProxyData(
    resolver: TailscaleResolver,
    ipVersion: String,
    onResolved: ((InetAddress) -> Unit)? = null,
) : ResolvingProxyData("MagicDNS", ipVersion, onResolved, resolver::resolve)
