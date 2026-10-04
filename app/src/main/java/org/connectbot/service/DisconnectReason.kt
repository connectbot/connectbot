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

package org.connectbot.service

import androidx.annotation.StringRes
import org.connectbot.R

enum class DisconnectReason {
    USER_REQUESTED,
    SESSION_EXIT,
    REMOTE_EOF,
    IO_ERROR,
    NETWORK_LOST,
    AUTH_FAIL,
    UNKNOWN,
}

@StringRes
fun DisconnectReason.descriptionResource(): Int = when (this) {
    DisconnectReason.USER_REQUESTED -> R.string.host_status_user_disconnected
    DisconnectReason.SESSION_EXIT -> R.string.host_status_session_exit
    DisconnectReason.REMOTE_EOF -> R.string.host_status_remote_closed
    DisconnectReason.IO_ERROR -> R.string.host_status_connection_lost
    DisconnectReason.NETWORK_LOST -> R.string.host_status_network_lost
    DisconnectReason.AUTH_FAIL -> R.string.host_status_auth_failed
    DisconnectReason.UNKNOWN -> R.string.host_status_connection_ended
}
