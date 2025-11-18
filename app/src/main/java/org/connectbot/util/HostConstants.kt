/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root
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

/**
 * Constants for Host management.
 */
object HostConstants {
    /**
     * Legacy database names for backup purposes.
     * Note: The actual database is now managed by Room as ConnectBot.db,
     * but this constant is kept for backward compatibility with backups.
     */
    const val LEGACY_DB_NAME: String = "hosts"

    /*
	 * Possible colors for hosts in the list.
	 */
    const val COLOR_RED: String = "red"
    const val COLOR_GREEN: String = "green"
    const val COLOR_BLUE: String = "blue"
    const val COLOR_GRAY: String = "gray"

    /*
	 * Possible keys for what is send when backspace is pressed.
	 */
    const val DELKEY_DEL: String = "del"
    const val DELKEY_BACKSPACE: String = "backspace"

    const val DEFAULT_FG_COLOR: Int = 7
    const val DEFAULT_BG_COLOR: Int = 0

    /*
	 * Port forward types
	 */
    const val PORTFORWARD_LOCAL: String = "local"
    const val PORTFORWARD_REMOTE: String = "remote"
    const val PORTFORWARD_DYNAMIC4: String = "dynamic4"
    const val PORTFORWARD_DYNAMIC5: String = "dynamic5"

    /*
	 * Auth agent usage
	 */
    const val AUTHAGENT_NO: String = "no"
    const val AUTHAGENT_CONFIRM: String = "confirm"
    const val AUTHAGENT_YES: String = "yes"

    /*
	 * Old database field names
	 */
    const val FIELD_HOST_PROTOCOL: String = "protocol"
    const val FIELD_HOST_HOSTNAME: String = "hostname"
    const val FIELD_HOST_PORT: String = "port"
    const val FIELD_HOST_USERNAME: String = "username"
    const val FIELD_HOST_NICKNAME: String = "nickname"

    /*
	 * Magic pubkey IDs
	 */
    val PUBKEYID_NEVER: Long = -2
    val PUBKEYID_ANY: Long = -1
}
