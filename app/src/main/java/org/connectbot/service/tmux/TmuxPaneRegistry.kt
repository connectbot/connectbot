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

package org.connectbot.service.tmux

/**
 * Holds the live [TmuxPaneTerminal]s for one host, capped at [maxLivePanes].
 *
 * Recency is updated only by [acquire] (user navigation), never by output
 * routing — a flooding background pane must not evict the pane the user is
 * reading. Eviction destroys the terminal and reports it via [onEvicted]
 * (the manager then stops its output stream on tmux ≥3.2).
 *
 * Eviction closes each termlib emulator immediately so its native peer and
 * pending callbacks are released without waiting for garbage collection.
 */
class TmuxPaneRegistry(
    private val maxLivePanes: Int = DEFAULT_MAX_LIVE_PANES,
    private val onEvicted: (TmuxPaneTerminal) -> Unit = {},
) {
    private val lock = Any()
    private val terminals = HashMap<String, TmuxPaneTerminal>()

    /** Keys ordered least- to most-recently acquired. */
    private val recency = ArrayDeque<String>()

    fun get(sessionId: String, paneId: String): TmuxPaneTerminal? = synchronized(lock) { terminals[key(sessionId, paneId)] }

    /**
     * Returns the existing terminal for the pane (marking it most recent) or
     * registers [create]'s result, evicting the least recently used terminals
     * beyond the cap. The caller backfills a newly created terminal.
     * @return the terminal and whether it was just created
     */
    fun acquire(
        sessionId: String,
        paneId: String,
        create: () -> TmuxPaneTerminal,
    ): Pair<TmuxPaneTerminal, Boolean> {
        val evicted = mutableListOf<TmuxPaneTerminal>()
        val result: Pair<TmuxPaneTerminal, Boolean>
        synchronized(lock) {
            val key = key(sessionId, paneId)
            val existing = terminals[key]
            if (existing != null) {
                recency.remove(key)
                recency.addLast(key)
                result = existing to false
            } else {
                val terminal = create()
                terminals[key] = terminal
                recency.addLast(key)
                while (terminals.size > maxLivePanes) {
                    val victim = recency.removeFirst()
                    terminals.remove(victim)?.let { evicted.add(it) }
                }
                result = terminal to true
            }
        }
        evicted.forEach {
            it.destroy()
            onEvicted(it)
        }
        return result
    }

    /** Routes one `%output` event without touching recency. */
    fun route(sessionId: String, output: TmuxNotification.Output) {
        get(sessionId, output.paneId)?.handleOutput(output)
    }

    /** Destroys all terminals belonging to [sessionId] (detach/close). */
    fun removeSession(sessionId: String) {
        val removed: List<TmuxPaneTerminal>
        synchronized(lock) {
            val keys = terminals.keys.filter { it.startsWith("$sessionId|") }
            removed = keys.mapNotNull { terminals.remove(it) }
            recency.removeAll(keys.toSet())
        }
        removed.forEach { it.destroy() }
    }

    /** Destroys everything (transport loss/shutdown). */
    fun clear() {
        val removed: List<TmuxPaneTerminal>
        synchronized(lock) {
            removed = terminals.values.toList()
            terminals.clear()
            recency.clear()
        }
        removed.forEach { it.destroy() }
    }

    fun liveCount(): Int = synchronized(lock) { terminals.size }

    private fun key(sessionId: String, paneId: String) = "$sessionId|$paneId"

    companion object {
        const val DEFAULT_MAX_LIVE_PANES = 6
    }
}
