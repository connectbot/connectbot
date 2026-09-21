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

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.service.DisconnectReason
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.service.requestBooleanPrompt
import org.connectbot.service.requestHostKeyFingerprintPrompt
import org.connectbot.service.requestStringPrompt
import org.connectbot.sshlib.AuthHandler
import org.connectbot.sshlib.AuthPublicKey
import org.connectbot.sshlib.AuthResult
import org.connectbot.sshlib.ConnectResult
import org.connectbot.sshlib.HostKeyVerifier
import org.connectbot.sshlib.KeyFingerprint
import org.connectbot.sshlib.PortForwarder
import org.connectbot.sshlib.PublicKey
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshClientConfig
import org.connectbot.sshlib.SshSession
import org.connectbot.sshlib.SshSigning
import org.connectbot.sshlib.transport.IpVersion
import org.connectbot.sshlib.transport.TransportFactory
import org.connectbot.util.HostConstants
import org.connectbot.util.PubkeyUtils
import org.connectbot.util.SshKeyType
import org.connectbot.util.UrlUtils
import timber.log.Timber
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyPair
import java.util.Base64
import java.util.regex.Pattern

/** ConnectBot's blocking terminal adapter over cbssh's coroutine API. */
class SSH : AbsTransport {
    private var compression = false

    @Volatile private var connected = false

    @Volatile private var authenticated = false

    @Volatile private var sessionOpen = false
    private var client: SshClient? = null
    private val jumpClients = mutableListOf<SshClient>()
    private var session: SshSession? = null
    private var stdout: ReceiveChannel<ByteArray>? = null
    private var remainder = ByteArray(0)
    private var remainderOffset = 0
    private val forwards = mutableListOf<PortForward>()
    private var columns = 80
    private var rows = 24
    private var width = 0
    private var height = 0

    constructor() : super()
    constructor(host: Host?, bridge: TerminalBridge?, manager: TerminalManager?) : super(host, bridge, manager)

    internal fun getKeyType(openSshKeyType: String): String? = SshKeyType.fromOpenSshType(openSshKeyType)?.storedName

    private inner class Verifier(private val target: Host) : HostKeyVerifier {
        override suspend fun verify(key: PublicKey): Boolean {
            val known = manager?.hostRepository?.getKnownHostsForHostBlocking(target.id).orEmpty()
                .filter { it.hostname == target.hostname && it.port == target.port }
            if (known.any { it.hostKeyAlgo == key.type && it.hostKey.contentEquals(key.encoded) }) return true
            val type = SshKeyType.fromOpenSshType(key.type)?.storedName ?: key.type
            val accepted = if (known.any { it.hostKeyAlgo == key.type }) {
                bridge?.outputLine(manager?.res?.getString(R.string.host_verification_failure_warning))
                bridge?.requestBooleanPrompt(null, manager?.res?.getString(R.string.prompt_continue_connecting) ?: "") == true
            } else {
                bridge?.requestHostKeyFingerprintPrompt(
                    target.hostname,
                    type,
                    0,
                    key.encoded,
                    KeyFingerprint.randomArt(key.encoded, type, 0),
                    KeyFingerprint.bubblebabble(key.encoded),
                    KeyFingerprint.sha256(key.encoded),
                    KeyFingerprint.md5(key.encoded),
                ) == true
            }
            if (accepted) manager?.hostRepository?.saveKnownHostBlocking(target, target.hostname, target.port, key.type, key.encoded)
            return accepted
        }
    }

    private fun createClient(target: Host, transportFactory: TransportFactory? = null) = SshClient(
        SshClientConfig {
            host = target.hostname
            port = target.port
            hostKeyVerifier = Verifier(target)
            enableCompression = compression || target.compression
            ipVersion = when (target.ipVersion) {
                HostConstants.IPVERSION_IPV4_ONLY -> IpVersion.IPV4_ONLY
                HostConstants.IPVERSION_IPV6_ONLY -> IpVersion.IPV6_ONLY
                else -> IpVersion.AUTO
            }
            this.transportFactory = transportFactory
        },
    )

    private fun keys(target: Host): List<Pair<String, KeyPair>> = when (target.pubkeyId) {
        HostConstants.PUBKEYID_NEVER -> emptyList()

        HostConstants.PUBKEYID_ANY -> manager?.loadedKeypairs?.mapNotNull { (name, holder) -> holder.pair?.let { name to it } }.orEmpty()

        else -> manager?.pubkeyRepository?.getByIdBlocking(target.pubkeyId)?.let { key ->
            manager?.getKey(key.nickname)?.let { listOf(key.nickname to it) }
                ?: runCatching { PubkeyUtils.convertToKeyPair(key, if (key.encrypted) bridge?.requestStringPrompt(null, manager?.res?.getString(R.string.prompt_pubkey_password, key.nickname), true) else null) }
                    .getOrNull()?.let { listOf(key.nickname to it) }.orEmpty()
        }.orEmpty()
    }

    private fun handler(target: Host) = object : AuthHandler {
        private val pairs by lazy { keys(target) }
        private val publicKeys by lazy { pairs.associateBy { SshSigning.encodePublicKey(it.second) } }
        override suspend fun onPublicKeysNeeded(): List<AuthPublicKey> = publicKeys.keys.toList()
        override suspend fun onSignatureRequest(key: AuthPublicKey, dataToSign: ByteArray) = publicKeys[key]?.let {
            SshSigning.signWithKeyPair(key.algorithmName, it.second, dataToSign)
        }
        override suspend fun onKeyboardInteractivePrompt(name: String, instruction: String, prompts: List<org.connectbot.sshlib.KeyboardInteractiveCallback.Prompt>) = prompts.map { bridge?.requestStringPrompt(instruction, it.text, !it.echo) ?: "" }
        override suspend fun onPasswordNeeded() = manager?.securePasswordStorage?.getPassword(target.id) ?: bridge?.requestStringPrompt(null, manager?.res?.getString(R.string.prompt_password), true)
        override suspend fun onBanner(message: String) = handleAuthBanner(target.nickname, message)
    }

    override fun connect() {
        val target = host ?: return
        try {
            val transportFactory = target.jumpHostId?.takeIf { it > 0 }?.let { jumpHostId ->
                val jumpHost = manager?.hostRepository?.findHostByIdBlocking(jumpHostId)
                    ?: throw IOException("Jump host not found")
                val jumpClient = createClient(jumpHost)
                check(runBlocking { jumpClient.connect() } is ConnectResult.Success) { "Jump host connection failed" }
                check(runBlocking { jumpClient.authenticate(jumpHost.username, handler(jumpHost)) } is AuthResult.Success) {
                    "Jump host authentication failed"
                }
                jumpClients += jumpClient
                jumpClient.openDirectTcpipTransport(target.hostname, target.port)
                    ?: throw IOException("Could not open jump host tunnel")
            }
            client = createClient(target, transportFactory)
            check(runBlocking { client!!.connect() } is ConnectResult.Success) { "SSH connection failed" }
            connected = true
            check(runBlocking { client!!.authenticate(target.username, handler(target)) } is AuthResult.Success) { "SSH authentication failed" }
            authenticated = true
            forwards.forEach { enablePortForward(it) }
            if (target.wantSession) openSession()
            bridge?.onConnected()
        } catch (e: Exception) {
            Timber.e(e, "SSH connection failed")
            bridge?.outputLine(e.message ?: "SSH connection failed")
            close()
            bridge?.dispatchDisconnect(DisconnectReason.IO_ERROR)
        }
    }

    private fun openSession() {
        val opened = runBlocking { client?.openSession() } ?: throw IOException("Unable to open SSH session")
        check(runBlocking { opened.requestPty(getEmulation() ?: "xterm", columns, rows, width, height) }) { "PTY request refused" }
        check(runBlocking { opened.requestShell() }) { "Shell request refused" }
        session = opened
        stdout = opened.stdout
        sessionOpen = true
    }

    override fun close() {
        connected = false
        authenticated = false
        sessionOpen = false
        session?.close()
        session = null
        stdout = null
        runCatching { runBlocking { client?.disconnect() } }
        client = null
        jumpClients.asReversed().forEach { jumpClient -> runCatching { runBlocking { jumpClient.disconnect() } } }
        jumpClients.clear()
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remainderOffset == remainder.size) {
            remainder = runBlocking { stdout?.receiveCatching()?.getOrNull() } ?: run {
                bridge?.dispatchDisconnect(DisconnectReason.REMOTE_EOF)
                throw IOException("Remote end closed connection")
            }
            remainderOffset = 0
        }
        val size = minOf(length, remainder.size - remainderOffset)
        remainder.copyInto(buffer, offset, remainderOffset, remainderOffset + size)
        remainderOffset += size
        return size
    }
    override fun write(buffer: ByteArray) {
        runBlocking { session?.write(buffer) }
    }
    override fun write(c: Int) = write(byteArrayOf(c.toByte()))
    override fun flush() = Unit
    override fun isConnected() = connected
    override fun isSessionOpen() = sessionOpen
    override fun setDimensions(columns: Int, rows: Int, width: Int, height: Int) {
        this.columns = columns
        this.rows = rows
        this.width = width
        this.height = height
        if (sessionOpen) runBlocking { session?.resizeTerminal(columns, rows, width, height) }
    }
    override fun setCompression(compression: Boolean) {
        this.compression = compression
    }
    override fun getOptions() = mapOf("compression" to compression.toString())
    override fun setOptions(options: Map<String, String>) {
        compression = options["compression"]?.toBoolean() ?: compression
    }
    override fun canForwardPorts() = true
    override fun getPortForwards() = forwards
    override fun addPortForward(portForward: PortForward) = forwards.add(portForward)
    override fun removePortForward(portForward: PortForward): Boolean {
        disablePortForward(portForward)
        return forwards.remove(portForward)
    }
    override fun enablePortForward(portForward: PortForward): Boolean = try {
        val destination = portForward.destAddr ?: return false
        val forwarder = when (portForward.type) {
            HostConstants.PORTFORWARD_LOCAL -> runBlocking { client?.localPortForward(InetSocketAddress(InetAddress.getLoopbackAddress(), portForward.sourcePort), destination, portForward.destPort) }
            HostConstants.PORTFORWARD_REMOTE -> runBlocking { client?.remotePortForward(portForward.sourceAddr, portForward.sourcePort, destination, portForward.destPort) }
            HostConstants.PORTFORWARD_DYNAMIC5 -> runBlocking { client?.dynamicPortForward(InetSocketAddress(InetAddress.getLoopbackAddress(), portForward.sourcePort)) }
            else -> null
        } ?: return false
        portForward.setIdentifier(forwarder)
        portForward.setEnabled(true)
        true
    } catch (e: Exception) {
        Timber.e(e, "Unable to create port forward")
        false
    }
    override fun disablePortForward(portForward: PortForward): Boolean = (portForward.getIdentifier() as? PortForwarder)?.let { runCatching { it.close() }.isSuccess.also { ok -> if (ok) portForward.setEnabled(false) } } ?: false
    fun handleAuthBanner(source: String, banner: String?) {
        val message = banner?.trim().orEmpty()
        if (message.isNotEmpty()) {
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_banner_header, source) ?: "[$source] Authentication message:")
            bridge?.outputLine(message)
            UrlUtils.extractUrls(message).takeIf { it.isNotEmpty() }?.let { bridge?.enqueueAuthBanner(source, message, it, null) }
        }
    }
    override fun getDefaultPort() = DEFAULT_PORT
    override fun getDefaultNickname(username: String?, hostname: String?, port: Int) = if (port == DEFAULT_PORT) "$username@$hostname" else "$username@$hostname:$port"
    override fun createHost(uri: Uri) = Host.createSshHost(getDefaultNickname(uri.userInfo, uri.host, if (uri.port < 0) DEFAULT_PORT else uri.port), uri.host ?: "", if (uri.port < 0) DEFAULT_PORT else uri.port, uri.userInfo ?: "")
    override fun getSelectionArgs(uri: Uri, selection: MutableMap<String, String>) {
        selection[HostConstants.FIELD_HOST_PROTOCOL] = PROTOCOL
        selection[HostConstants.FIELD_HOST_NICKNAME] = uri.fragment ?: ""
        selection[HostConstants.FIELD_HOST_HOSTNAME] = uri.host ?: ""
        selection[HostConstants.FIELD_HOST_PORT] = (if (uri.port < 0) DEFAULT_PORT else uri.port).toString()
        selection[HostConstants.FIELD_HOST_USERNAME] = uri.userInfo ?: ""
    }
    override fun usesNetwork() = true
    override fun getLocalIpAddress(): String? = null
    companion object {
        private const val PROTOCOL = "ssh"
        private const val DEFAULT_PORT = 22
        private val hostmask = Pattern.compile("^(.+)@((?:[0-9a-z._-]+)|(?:\\[[a-f:0-9]+(?:%[-_.a-z0-9]+)?\\]))(?::(\\d+))?$", Pattern.CASE_INSENSITIVE)

        @JvmStatic fun getProtocolName() = PROTOCOL

        @JvmStatic fun getFormatHint(context: android.content.Context) = context.getString(R.string.hostpref_nickname_title)

        @JvmStatic fun getUri(input: String): Uri? {
            val m = hostmask.matcher(input)
            return if (m.matches()) "$PROTOCOL://${Uri.encode(m.group(1))}@${Uri.encode(m.group(2))}${m.group(3)?.let { ":$it" } ?: ""}".toUri() else null
        }
    }
}
