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

package org.connectbot.ui.navigation

object NavDestinations {
    const val HOST_LIST = "host_list"
    const val CONSOLE = "console"
    const val HOST_EDITOR = "host_editor"
    const val PUBKEY_LIST = "pubkey_list"
    const val GENERATE_PUBKEY = "generate_pubkey"
    const val PUBKEY_EDITOR = "pubkey_editor"
    const val PORT_FORWARD_LIST = "port_forward_list"
    const val SETTINGS = "settings"
    const val COLORS = "colors"
    const val PALETTE_EDITOR = "palette_editor"
    const val PROFILES = "profiles"
    const val PROFILE_EDITOR = "profile_editor"
    const val HELP = "help"
    const val EULA = "eula"
    const val HINTS = "hints"
    const val CONTACT = "contact"
}

object NavArgs {
    const val HOST_ID = "hostId"
    const val BRIDGE_NAME = "bridgeName"
    const val PUBKEY_ID = "pubkeyId"
    const val SCHEME_ID = "schemeId"
    const val PROFILE_ID = "profileId"
}
