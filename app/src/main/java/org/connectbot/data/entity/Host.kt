/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

package org.connectbot.data.entity

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SSH/Telnet/Local connection configuration entity.
 */
@Entity(
    tableName = "hosts",
    indices = [
        Index(value = ["nickname"], unique = true),
        Index(value = ["protocol", "username", "hostname", "port"])
    ]
)
data class Host(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val nickname: String = "",

    val protocol: String = "ssh",

    val username: String = "",

    val hostname: String = "",

    val port: Int = 22,

    @ColumnInfo(name = "host_key_algo")
    val hostKeyAlgo: String? = null,

    @ColumnInfo(name = "last_connect")
    val lastConnect: Long = 0,

    val color: String? = null,

    @ColumnInfo(name = "use_keys")
    val useKeys: Boolean = true,

    @ColumnInfo(name = "use_auth_agent")
    val useAuthAgent: String? = "no",

    @ColumnInfo(name = "post_login")
    val postLogin: String? = null,

    @ColumnInfo(name = "pubkey_id")
    val pubkeyId: Long = -1L,

    @ColumnInfo(name = "want_session")
    val wantSession: Boolean = true,

    val compression: Boolean = false,

    val encoding: String = "UTF-8",

    @ColumnInfo(name = "stay_connected")
    val stayConnected: Boolean = false,

    @ColumnInfo(name = "quick_disconnect")
    val quickDisconnect: Boolean = false,

    @ColumnInfo(name = "font_size")
    val fontSize: Int = 10,

    @ColumnInfo(name = "color_scheme_id")
    val colorSchemeId: Long = -1L,

    @ColumnInfo(name = "del_key")
    val delKey: String = "DEL",

    @ColumnInfo(name = "scrollback_lines")
    val scrollbackLines: Int = 140,

    @ColumnInfo(name = "use_ctrl_alt_as_meta_key")
    val useCtrlAltAsMetaKey: Boolean = false,

    /**
     * Optional jump host ID for ProxyJump support.
     * When set, connections to this host will be tunneled through the jump host.
     * A value of null means no jump host (direct connection).
     */
    @ColumnInfo(name = "jump_host_id")
    val jumpHostId: Long? = null,

    /**
     * Optional font family for terminal display.
     * When null, uses the global default font setting.
     * Value should be a TerminalFont enum name (e.g., "JETBRAINS_MONO").
     */
    @ColumnInfo(name = "font_family")
    val fontFamily: String? = null
) {
    /**
     * Check if this host is temporary (not saved to database).
     * Temporary hosts have negative IDs.
     */
    val isTemporary: Boolean
        get() = id < 0L

    /**
     * Create a copy of this Host with a different font size.
     * Helper method for Java interop.
     */
    fun withFontSize(newFontSize: Int): Host = copy(fontSize = newFontSize)

    /**
     * Get the URI representation of this host (Java interop helper).
     */
    fun getUri(): Uri {
        val builder = Uri.Builder()
            .scheme(protocol)

        // Build authority based on protocol
        when (protocol) {
            "local" -> {
                builder.fragment(nickname)
            }
            "ssh", "telnet" -> {
                // Build authority with hostname and port
                val authority = buildString {
                    if (username.isNotEmpty() && protocol == "ssh") {
                        append(username)
                        append('@')
                    }
                    append(hostname)
                    if (port > 0) {
                        append(':')
                        append(port)
                    }
                }
                builder.authority(authority)
                builder.fragment(nickname)

            }
        }

        return builder.build()
    }

    companion object {
        /**
         * Create a new SSH host with default values (Java interop helper).
         */
        @JvmStatic
        fun createSshHost(nickname: String, hostname: String, port: Int, username: String): Host {
            return Host(
                id = 0L,
                nickname = nickname,
                protocol = "ssh",
                username = username,
                hostname = hostname,
                port = port
            )
        }

        /**
         * Create a new Telnet host with default values (Java interop helper).
         */
        @JvmStatic
        fun createTelnetHost(nickname: String, hostname: String, port: Int): Host {
            return Host(
                id = 0L,
                nickname = nickname,
                protocol = "telnet",
                username = "",
                hostname = hostname,
                port = port
            )
        }

        /**
         * Create a new Local host with default values (Java interop helper).
         */
        @JvmStatic
        fun createLocalHost(nickname: String): Host {
            return Host(
                id = 0L,
                nickname = nickname,
                protocol = "local",
                username = "",
                hostname = "",
                port = 0
            )
        }
    }
}
