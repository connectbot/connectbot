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

import androidx.annotation.StringRes
import org.connectbot.R
import org.connectbot.data.entity.Host
import java.io.IOException

internal const val MAX_JUMP_HOSTS = 32

internal class JumpHostChainException(@StringRes val messageResId: Int) : IOException("Invalid jump host chain")

/** Resolve the whole chain before opening sockets, ordered from nearest to outermost hop. */
internal fun resolveJumpHostChain(target: Host, findHost: (Long) -> Host?): List<Host> {
    val chain = mutableListOf<Host>()
    val visited = mutableSetOf(target.id)
    var nextId = target.jumpHostId
    while (nextId != null && nextId > 0) {
        if (!visited.add(nextId)) throw JumpHostChainException(R.string.terminal_jump_cycle)
        if (chain.size >= MAX_JUMP_HOSTS) throw JumpHostChainException(R.string.terminal_jump_too_deep)
        val nextHost = findHost(nextId) ?: throw JumpHostChainException(R.string.terminal_jump_not_found)
        chain.add(nextHost)
        nextId = nextHost.jumpHostId
    }
    return chain
}
