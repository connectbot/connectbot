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

package org.connectbot.data.keyboard

import java.io.Serializable
import java.util.UUID

/** A layout and newly created macros waiting for the owning editor to commit. */
data class KeyboardDraft(
    val layout: KeyboardLayout,
    val items: List<KeyboardItem>,
    val macros: List<KeyboardMacro> = emptyList(),
) : Serializable {
    fun duplicate(name: String): KeyboardDraft {
        val copy = layout.copy(id = UUID.randomUUID(), name = name)
        return copy(layout = copy, items = items.map { it.copy(id = UUID.randomUUID(), layoutId = copy.id, rowSpan = 1) })
    }
}

data class KeyboardUsage(val kind: String, val referenceId: UUID, val ownerId: UUID, val name: String)
