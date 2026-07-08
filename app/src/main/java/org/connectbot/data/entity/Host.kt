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

package org.connectbot.data.entity

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SSH connection configuration entity.
 */
@Entity(
    tableName = "hosts",
    indices = [
        Index(value = ["nickname"], unique = true),
        Index(value = ["protocol", "username", "hostname", "port"]),
    ],
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

    @ColumnInfo(name = "stay_connected")
    val stayConnected: Boolean = false,

    @ColumnInfo(name = "quick_disconnect")
    val quickDisconnect: Boolean = false,

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
     * Profile ID for terminal-specific settings (font, colors, encoding, etc.).
     * Defaults to 1 (the Default profile).
     */
    @ColumnInfo(name = "profile_id")
    val profileId: Long? = 1L,

    /**
     * IP version preference for connections.
     * Values: "IPV4_AND_IPV6" (default), "IPV4_ONLY", "IPV6_ONLY"
     */
    @ColumnInfo(name = "ip_version", defaultValue = "IPV4_AND_IPV6")
    val ipVersion: String = "IPV4_AND_IPV6",

    /**
     * tmux integration mode for this host.
     * Values: "AUTO" (detect tmux and offer the native session UI, default),
     * "OFF" (never probe for or surface tmux on this host).
     */
    @ColumnInfo(name = "tmux_mode", defaultValue = "AUTO")
    val tmuxMode: String = TMUX_MODE_AUTO,

    /**
     * Last viewed tmux target for silent reattach, encoded as
     * "sessionId|windowId|paneId|sessionName" (e.g. "$1|@3|%7|main").
     * Null when the host was not left inside a tmux session.
     */
    @ColumnInfo(name = "tmux_last_target")
    val tmuxLastTarget: String? = null,

    /**
     * Whether the "start a persistent tmux session" offer was permanently
     * dismissed for this host. Unlike tmuxMode = OFF, existing sessions are
     * still detected and surfaced.
     */
    @ColumnInfo(name = "tmux_offer_dismissed", defaultValue = "0")
    val tmuxOfferDismissed: Boolean = false,

    /**
     * Whether to enable IME keyboard suggestions (autocomplete/predictive text)
     * for this host by activating the terminal's compose mode on connect.
     * Off by default so passwords are not fed to the keyboard's suggestion engine.
     */
    @ColumnInfo(name = "keyboard_suggestions", defaultValue = "0")
    val keyboardSuggestions: Boolean = false,

    /**
     * Optional per-host override for the special-keys bar layout.
     * null means use the global default; negative values select a built-in
     * layout; positive values reference a `keyboard_layouts` row. Plain nullable
     * Long (no foreign key), consistent with [profileId].
     */
    @ColumnInfo(name = "keyboard_layout_id")
    val keyboardLayoutId: Long? = null,
) {
    /**
     * Check if this host is temporary (not saved to database).
     * Temporary hosts have negative IDs.
     */
    val isTemporary: Boolean
        get() = id < 0L

    /**
     * Get the URI representation of this host (Java interop helper).
     */
    fun getUri(): Uri {
        val builder = Uri.Builder()
            .scheme(protocol)

        // Build authority with hostname and port
        val authority = buildString {
            if (username.isNotEmpty()) {
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

        return builder.build()
    }

    companion object {
        /** [tmuxMode] value: detect tmux and surface the native session UI. */
        const val TMUX_MODE_AUTO = "AUTO"

        /** [tmuxMode] value: never probe for or surface tmux on this host. */
        const val TMUX_MODE_OFF = "OFF"

        /**
         * Create a new SSH host with default values (Java interop helper).
         */
        @JvmStatic
        fun createSshHost(nickname: String, hostname: String, port: Int, username: String): Host = Host(
            id = 0L,
            nickname = nickname,
            protocol = "ssh",
            username = username,
            hostname = hostname,
            port = port,
        )

    }
}
