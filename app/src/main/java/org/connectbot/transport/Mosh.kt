/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2012-2026 Kenny Root
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
import android.net.Uri
import android.os.Process
import androidx.core.net.toUri
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.service.DisconnectReason
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.util.HostConstants
import org.connectbot.util.InstallMosh
import org.mosh.MoshClient
import timber.log.Timber
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale
import java.util.regex.Pattern

/**
 * Mosh transport implementation for ConnectBot.
 *
 * Mosh (Mobile Shell) provides a more robust connection than SSH for mobile devices.
 * It uses SSH for initial authentication and then switches to a UDP-based protocol
 * that handles roaming and intermittent connectivity gracefully.
 *
 * The connection flow is:
 * 1. Establish SSH connection to target host
 * 2. Run mosh-server on remote host via SSH
 * 3. Parse MOSH CONNECT response to get UDP port and key
 * 4. Close SSH connection
 * 5. Fork the downloaded mosh-client executable to connect via UDP
 *
 * @author Daniel Drown (original transport layer)
 * @author bqv (Tony O) - ConnectBot integration
 */
class Mosh : SSH {

    private var moshClientFd: FileDescriptor? = null
    private var moshProcessId: Long = 0

    private var moshInputStream: FileInputStream? = null
    private var moshOutputStream: FileOutputStream? = null

    @Volatile
    private var moshConnected = false

    @Volatile
    private var initialSshDetached = false

    constructor() : super()

    constructor(host: Host?, bridge: TerminalBridge?, manager: TerminalManager?) : super(host, bridge, manager)

    override fun instanceProtocolName(): String = PROTOCOL

    override fun finishConnection() {
        authenticated = true
    }

    override fun connect() {
        val currentHost = host ?: return
        val terminalManager = manager
        if (terminalManager == null || !InstallMosh.isMoshSupportEnabled(terminalManager)) {
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_mosh_disabled))
            return
        }

        InstallMosh.startInstall(terminalManager)

        // Wait for mosh installation to complete
        if (!InstallMosh.waitForInstall(60_000)) {
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_mosh_install_timeout))
            Timber.w("Mosh installation timeout")
            return
        }

        bridge?.outputLine(manager?.res?.getString(R.string.terminal_mosh_starting))

        // First, establish SSH connection
        super.connect()

        // Check if SSH authentication succeeded
        if (!authenticated) {
            Timber.d("SSH authentication failed, cannot start mosh")
            return
        }

        // Now launch mosh-server via SSH and get connection credentials
        try {
            val moshCredentials = launchMoshServer(currentHost)
            if (moshCredentials == null) {
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_mosh_server_failed))
                close()
                bridge?.dispatchDisconnect(DisconnectReason.IO_ERROR)
                return
            }

            // The SSH leg is only a launcher for mosh-server. Once the server
            // has emitted credentials, detach it so later TCP loss does not
            // tear down the roaming UDP session.
            detachInitialSsh()

            // Start native mosh-client
            if (!startMoshClient(moshCredentials, currentHost)) {
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_mosh_client_failed))
                close()
                bridge?.dispatchDisconnect(DisconnectReason.IO_ERROR)
                return
            }

            moshConnected = true
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_mosh_connected))
            bridge?.onConnected()
        } catch (e: Exception) {
            Timber.e(e, "Failed to establish mosh connection")
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_mosh_error, e.message ?: "Unknown error"))
            close()
            bridge?.dispatchDisconnect(DisconnectReason.IO_ERROR)
        }
    }

    /**
     * Launch mosh-server on the remote host via SSH and parse the connection credentials.
     *
     * @param currentHost The host configuration
     * @return MoshCredentials containing IP, port, and key, or null on failure
     */
    private fun launchMoshServer(currentHost: Host): MoshCredentials? {
        try {
            val moshServerCmd = buildMoshServerCommand(currentHost)
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_mosh_launching_server))
            Timber.d("Launching mosh-server: $moshServerCmd")

            // Open a session and execute mosh-server
            val moshSession = connection?.openSession() ?: return null
            moshSession.execCommand(moshServerCmd)

            // Read the output to get MOSH CONNECT line
            val stdout = moshSession.stdout
            val buffer = ByteArray(4096)
            val outputBuilder = StringBuilder()

            // Wait for mosh-server to output connection info
            var attempts = 0
            while (attempts < 50) { // 5 second timeout (100ms * 50)
                val available = stdout.available()
                if (available > 0) {
                    val bytesRead = stdout.read(buffer, 0, minOf(available, buffer.size))
                    if (bytesRead > 0) {
                        outputBuilder.append(String(buffer, 0, bytesRead))

                        // Check if we have the MOSH CONNECT line
                        if (outputBuilder.contains("MOSH CONNECT")) {
                            break
                        }
                    }
                }
                Thread.sleep(100)
                attempts++
            }

            moshSession.close()

            val output = outputBuilder.toString()
            Timber.d("Mosh-server output: $output")

            val explicitIp = parseExplicitIp(output)
            val sshConnectionIp = parseSshConnectionServerIp(output)
            val clientIp = chooseMoshClientIp(currentHost, explicitIp, sshConnectionIp)
            if (clientIp == null) {
                Timber.e("Failed to determine mosh client IP from host ${currentHost.hostname} and output: $output")
                return null
            }
            Timber.d(
                "Using mosh client IP: $clientIp (explicitIp=$explicitIp, sshConnectionIp=$sshConnectionIp)",
            )

            // Parse MOSH CONNECT line. Format: MOSH CONNECT <port> <key>
            val connectMatcher = MOSH_CONNECT_PATTERN.matcher(output)
            if (!connectMatcher.find()) {
                Timber.e("Failed to parse MOSH CONNECT from output: $output")
                return null
            }

            val port = connectMatcher.group(1) ?: return null
            val key = connectMatcher.group(2) ?: return null

            return MoshCredentials(
                ip = clientIp,
                port = port,
                key = key,
            )
        } catch (e: Exception) {
            Timber.e(e, "Error launching mosh-server")
            return null
        }
    }

    /**
     * Build the mosh-server command based on host configuration.
     */
    private fun buildMoshServerCommand(currentHost: Host): String {
        val serverCmd = currentHost.moshServer?.takeIf { it.isNotBlank() } ?: "mosh-server"

        val portArg = if (currentHost.moshPort > 0) {
            " -p ${currentHost.moshPort}"
        } else {
            ""
        }

        val locale = currentHost.locale.takeIf { it.isNotBlank() } ?: "en_US.UTF-8"

        // Ask for SSH_CONNECTION as a fallback. We do not pass mosh-server -s,
        // so the server keeps its default bind behavior instead of being pinned
        // to the SSH-facing interface.
        return "sh -c '[ -n \"\$SSH_CONNECTION\" ] && printf \"\\nMOSH SSH_CONNECTION %s\\n\" \"\$SSH_CONNECTION\"; " +
            "exec env LANG=$locale LC_ALL=$locale $serverCmd$portArg new'"
    }

    private fun parseExplicitIp(output: String): String? {
        val ipMatcher = MOSH_IP_PATTERN.matcher(output)
        if (ipMatcher.find()) {
            return ipMatcher.group(1)
        }

        return null
    }

    private fun parseSshConnectionServerIp(output: String): String? {
        val sshConnectionMatcher = MOSH_SSH_CONNECTION_PATTERN.matcher(output)
        if (sshConnectionMatcher.find()) {
            return sshConnectionMatcher.group(1)
        }

        return null
    }

    private fun chooseMoshClientIp(currentHost: Host, explicitIp: String?, sshConnectionIp: String?): String? {
        explicitIp?.let { return it }

        val resolvedHostIp = resolveHostAddress(currentHost)
        if (resolvedHostIp != null && !resolvedHostIp.isPrivateOrLocalAddress()) {
            return resolvedHostIp.hostAddressForMosh() ?: sshConnectionIp
        }

        return sshConnectionIp ?: resolvedHostIp?.hostAddressForMosh()
    }

    private fun resolveHostAddress(currentHost: Host): InetAddress? {
        val hostname = currentHost.hostname.trim().trim('[', ']')
        if (hostname.isEmpty()) {
            return null
        }

        return try {
            val allAddresses = InetAddress.getAllByName(hostname).toList()
            val addresses = if (HostConstants.isIpAddress(hostname)) {
                allAddresses
            } else {
                when (currentHost.ipVersion) {
                    HostConstants.IPVERSION_IPV4_ONLY -> allAddresses.filterIsInstance<Inet4Address>()
                    HostConstants.IPVERSION_IPV6_ONLY -> allAddresses.filterIsInstance<Inet6Address>()
                    else -> allAddresses
                }
            }

            addresses.firstOrNull()
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve mosh host address: $hostname")
            null
        }
    }

    private fun InetAddress.hostAddressForMosh(): String? = hostAddress?.substringBefore('%')

    private fun InetAddress.isPrivateOrLocalAddress(): Boolean = isAnyLocalAddress ||
        isLoopbackAddress ||
        isLinkLocalAddress ||
        isSiteLocalAddress ||
        isUniqueLocalIpv6()

    private fun InetAddress.isUniqueLocalIpv6(): Boolean = address.size == IPV6_ADDRESS_LENGTH && (address[0].toInt() and IPV6_UNIQUE_LOCAL_MASK) == IPV6_UNIQUE_LOCAL_PREFIX

    private fun detachInitialSsh() {
        initialSshDetached = true
        super.close()
        session?.close()
        session = null
        sessionOpen = false
        stdin = null
        stdout = null
        stderr = null
    }

    /**
     * Start the external mosh-client process.
     *
     * @param credentials The MOSH CONNECT credentials
     * @param currentHost The host configuration
     * @return true if mosh-client started successfully
     */
    private fun startMoshClient(credentials: MoshCredentials, currentHost: Host): Boolean {
        try {
            val terminfoPath = InstallMosh.getTerminfoPath()
            if (terminfoPath == null) {
                Timber.e("Mosh terminfo database is not installed")
                return false
            }

            val moshClientPath = manager?.let { InstallMosh.getMoshClientPath(it) }
            if (moshClientPath == null) {
                Timber.e("mosh-client native payload is not installed")
                return false
            }

            val locale = currentHost.locale.takeIf { it.isNotBlank() } ?: "en_US.UTF-8"
            val initialRows = rows.takeIf { it > 0 } ?: 24
            val initialColumns = columns.takeIf { it > 0 } ?: 80
            val initialWidth = width.takeIf { it > 0 } ?: 0
            val initialHeight = height.takeIf { it > 0 } ?: 0

            val processIdArray = LongArray(1)
            moshClientFd = MoshClient.forkExec(
                moshClientPath,
                credentials.ip,
                credentials.port,
                credentials.key,
                terminfoPath,
                locale,
                initialRows,
                initialColumns,
                initialWidth,
                initialHeight,
                processIdArray,
            )

            if (moshClientFd == null) {
                Timber.e("Failed to fork mosh-client")
                return false
            }

            moshProcessId = processIdArray[0]
            Timber.d("Mosh-client started with PID: $moshProcessId")

            // Set up I/O streams
            moshInputStream = FileInputStream(moshClientFd)
            moshOutputStream = FileOutputStream(moshClientFd)

            // Start exit watcher thread
            startExitWatcher()

            return true
        } catch (e: Exception) {
            Timber.e(e, "Error starting mosh-client")
            return false
        }
    }

    /**
     * Start a thread to watch for mosh-client process exit.
     */
    private fun startExitWatcher() {
        Thread {
            val exitCode = MoshClient.waitFor(moshProcessId)
            Timber.d("Mosh-client exited with code: $exitCode")
            if (moshConnected) {
                moshConnected = false
                bridge?.dispatchDisconnect(DisconnectReason.REMOTE_EOF)
            }
        }.apply {
            name = "MoshExitWatcher"
            isDaemon = true
            start()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val inputStream = moshInputStream ?: throw IOException("Mosh client not connected")

        val bytesRead = inputStream.read(buffer, offset, length)
        if (bytesRead < 0) {
            moshConnected = false
            throw IOException("Mosh client disconnected")
        }
        return bytesRead
    }

    override fun write(buffer: ByteArray) {
        moshOutputStream?.write(buffer)
    }

    override fun write(c: Int) {
        moshOutputStream?.write(c)
    }

    override fun flush() {
        moshOutputStream?.flush()
    }

    override fun close() {
        moshConnected = false
        initialSshDetached = false

        // Close mosh client streams
        try {
            moshOutputStream?.close()
        } catch (e: IOException) {
            Timber.d(e, "Error closing mosh output stream")
        }
        moshOutputStream = null

        try {
            moshInputStream?.close()
        } catch (e: IOException) {
            Timber.d(e, "Error closing mosh input stream")
        }
        moshInputStream = null

        // Kill mosh-client process
        if (moshProcessId > 0) {
            MoshClient.kill(moshProcessId, SIGTERM)
            moshProcessId = 0
        }

        moshClientFd = null

        // Close SSH connection
        super.close()
    }

    override fun isConnected(): Boolean = moshConnected || super.isConnected()

    override fun isSessionOpen(): Boolean = moshConnected

    override fun connectionLost(reason: Throwable) {
        if (initialSshDetached || moshConnected) {
            Timber.d("Ignoring initial SSH connection loss after mosh handoff")
            return
        }

        super.connectionLost(reason)
    }

    override fun setDimensions(columns: Int, rows: Int, width: Int, height: Int) {
        this.columns = columns
        this.rows = rows
        this.width = width
        this.height = height

        moshClientFd?.let { fd ->
            try {
                MoshClient.setPtyWindowSize(fd, rows, columns, width, height)
            } catch (e: Exception) {
                Timber.e(e, "Failed to set mosh PTY window size")
            }
        }
    }

    override fun getDefaultPort(): Int = DEFAULT_PORT

    override fun getDefaultNickname(username: String?, hostname: String?, port: Int): String = if (port == DEFAULT_PORT) {
        String.format(Locale.US, "mosh:%s@%s", username, hostname)
    } else {
        String.format(Locale.US, "mosh:%s@%s:%d", username, hostname, port)
    }

    override fun createHost(uri: Uri): Host {
        val hostname = uri.host
        val username = uri.userInfo
        var port = uri.port
        if (port < 0) {
            port = DEFAULT_PORT
        }
        val nickname = getDefaultNickname(username, hostname, port)

        return Host.createMoshHost(
            nickname = nickname,
            hostname = hostname ?: "",
            port = port,
            username = username ?: "",
        )
    }

    override fun getSelectionArgs(uri: Uri, selection: MutableMap<String, String>) {
        selection["protocol"] = PROTOCOL
        selection["nickname"] = uri.fragment ?: ""
        selection["hostname"] = uri.host ?: ""

        var port = uri.port
        if (port < 0) {
            port = DEFAULT_PORT
        }
        selection["port"] = port.toString()
        selection["username"] = uri.userInfo ?: ""
    }

    // Lifecycle methods for mosh-client process management

    override fun onBackground() {
        if (moshProcessId > 0 && moshConnected) {
            Timber.d("Sending SIGSTOP to mosh-client")
            MoshClient.kill(moshProcessId, SIGSTOP)
        }
    }

    override fun onForeground() {
        if (moshProcessId > 0 && moshConnected) {
            Timber.d("Sending SIGCONT to mosh-client")
            MoshClient.kill(moshProcessId, SIGCONT)
        }
    }

    override fun onScreenOff() {
        // Optionally pause mosh-client when screen is off to save battery
        onBackground()
    }

    override fun onScreenOn() {
        // Resume mosh-client when screen turns on
        onForeground()
    }

    /**
     * Mosh handles network roaming internally, so we don't need to reset on connection changes.
     */
    override fun resetOnConnectionChange(): Boolean = false

    /**
     * Mosh connection credentials parsed from MOSH CONNECT output.
     */
    private data class MoshCredentials(
        val ip: String,
        val port: String,
        val key: String,
    )

    companion object {
        private const val PROTOCOL = "mosh"
        private const val DEFAULT_PORT = 22 // SSH port for initial connection

        // Signal constants
        private const val SIGSTOP = 19
        private const val SIGCONT = 18
        private const val SIGTERM = 15
        private const val IPV6_ADDRESS_LENGTH = 16
        private const val IPV6_UNIQUE_LOCAL_MASK = 0xfe
        private const val IPV6_UNIQUE_LOCAL_PREFIX = 0xfc

        // Pattern to match MOSH CONNECT output
        // Format: MOSH CONNECT <port> <key>
        private val MOSH_CONNECT_PATTERN = Pattern.compile(
            "MOSH CONNECT (\\d+) (\\S+)",
            Pattern.MULTILINE,
        )
        private val MOSH_IP_PATTERN = Pattern.compile(
            "^MOSH IP (\\S+)\\s*$",
            Pattern.MULTILINE,
        )
        private val MOSH_SSH_CONNECTION_PATTERN = Pattern.compile(
            "^MOSH SSH_CONNECTION \\S+ \\d+ (\\S+) \\d+\\s*$",
            Pattern.MULTILINE,
        )

        private val hostmask = Pattern.compile(
            "^(.+)@((?:[0-9a-z._-]+)|(?:\\[[a-f:0-9]+(?:%[-_.a-z0-9]+)?\\]))(?::(\\d+))?\$",
            Pattern.CASE_INSENSITIVE,
        )

        @JvmStatic
        fun getProtocolName(): String = PROTOCOL

        @JvmStatic
        fun getUri(input: String): Uri? {
            val matcher = hostmask.matcher(input)

            if (!matcher.matches()) {
                return null
            }

            val sb = StringBuilder()

            sb.append(PROTOCOL)
                .append("://")
                .append(Uri.encode(matcher.group(1)))
                .append('@')
                .append(Uri.encode(matcher.group(2)))

            val portString = matcher.group(3)
            var port = DEFAULT_PORT
            if (portString != null) {
                try {
                    port = portString.toInt()
                    if (port !in 1..65535) {
                        port = DEFAULT_PORT
                    }
                } catch (_: NumberFormatException) {
                    // Keep the default port
                }
            }

            if (port != DEFAULT_PORT) {
                sb.append(':')
                    .append(port)
            }

            sb.append("/#")
                .append(Uri.encode(input))

            return sb.toString().toUri()
        }

        @JvmStatic
        fun getFormatHint(context: Context): String = String.format(
            "%s@%s:%s",
            context.getString(R.string.format_username),
            context.getString(R.string.format_hostname),
            context.getString(R.string.format_port),
        )
    }
}
