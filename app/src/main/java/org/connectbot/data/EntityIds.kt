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

package org.connectbot.data

import org.connectbot.util.HostConstants
import java.util.UUID

/** Stable identifiers for virtual resources. Persisted user objects use random UUIDs. */
object EntityIds {
    val NONE: UUID = UUID(0, 0)
    const val LOCAL_TEXT = "b6ba8cb6-02d4-492d-8de9-5747abbf3b21"
    val LOCAL: UUID = UUID.fromString(LOCAL_TEXT)
    const val DEFAULT_PROFILE_TEXT = "4b7eed63-d8fc-49d0-a9b5-a360c0f950db"
    val DEFAULT_PROFILE: UUID = UUID.fromString(DEFAULT_PROFILE_TEXT)
    val DEFAULT_SCHEME: UUID get() = preset(0)
    fun preset(index: Int): UUID = UUID.nameUUIDFromBytes("org.connectbot.preset:$index".toByteArray(Charsets.UTF_8))
    fun presetIndex(id: UUID): Int = ColorSchemePresets.builtInSchemes.indices.firstOrNull { preset(it) == id } ?: -1
    fun isPreset(id: UUID): Boolean = presetIndex(id) >= 0
    fun legacy(table: String, id: Long): UUID = UUID.nameUUIDFromBytes("org.connectbot.legacy:$table:$id".toByteArray(Charsets.UTF_8))
    fun legacyKey(id: Long): UUID = when (id) {
        -1L -> HostConstants.PUBKEYID_ANY
        -2L -> HostConstants.PUBKEYID_NEVER
        else -> legacy("pubkeys", id)
    }
    fun legacyScheme(id: Long): UUID = if (id < 0) preset((-id - 1).toInt()) else legacy("color_schemes", id)
    fun isKey(id: UUID): Boolean = id != HostConstants.PUBKEYID_ANY && id != HostConstants.PUBKEYID_NEVER
    fun parse(value: String?): UUID = try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        NONE
    } catch (_: NullPointerException) {
        NONE
    }
}
