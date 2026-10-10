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

import org.connectbot.R
import org.connectbot.data.entity.Host
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JumpHostChainTest {
    private val target = Host(id = 1, jumpHostId = 2)

    @Test
    fun resolvesNearestToOutermost() {
        val outer = Host(id = 3)
        val inner = Host(id = 2, jumpHostId = 3)
        val hosts = listOf(inner, outer).associateBy { it.id }
        assertEquals(listOf(inner, outer), resolveJumpHostChain(target, hosts::get))
    }

    @Test
    fun missingNestedHostReportsMissingHost() {
        val error = assertThrows(JumpHostChainException::class.java) {
            resolveJumpHostChain(target) { if (it == 2L) Host(id = 2, jumpHostId = 3) else null }
        }
        assertEquals(R.string.terminal_jump_not_found, error.messageResId)
    }

    @Test
    fun acceptsChainAtLimit() {
        val hosts = (2..MAX_JUMP_HOSTS + 1).map { id ->
            Host(id = id.toLong(), jumpHostId = if (id <= MAX_JUMP_HOSTS) id.toLong() + 1 else null)
        }.associateBy { it.id }
        assertEquals(MAX_JUMP_HOSTS, resolveJumpHostChain(target, hosts::get).size)
    }

    @Test
    fun rejectsChainOverLimit() {
        val error = assertThrows(JumpHostChainException::class.java) {
            resolveJumpHostChain(target) { Host(id = it, jumpHostId = it + 1) }
        }
        assertEquals(R.string.terminal_jump_too_deep, error.messageResId)
    }

    @Test
    fun directConnectionDoesNotLookUpHosts() {
        assertEquals(emptyList<Host>(), resolveJumpHostChain(Host()) { error("Unexpected lookup") })
    }
}
