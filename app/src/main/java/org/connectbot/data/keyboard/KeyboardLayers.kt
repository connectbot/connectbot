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

/** Layer navigation is local UI state; it never sends terminal input or consumes modifiers. */
object KeyboardLayers {
    const val count = 5

    fun populated(items: List<KeyboardItem>): List<Int> = items.filter { it.visible && it.kind != "layer" }
        .map { it.layer }.distinct().sorted()

    fun initial(items: List<KeyboardItem>): Int = populated(items).firstOrNull() ?: 0

    fun destination(items: List<KeyboardItem>, current: Int, target: String): Int? {
        val layers = populated(items)
        if (target != "next") return target.toIntOrNull()?.takeIf { it in layers }
        return layers.firstOrNull { it > current } ?: layers.firstOrNull()
    }

    /** A reachable layer must not strand the user after either a direct jump or FN cycling. */
    fun validateNavigation(items: List<KeyboardItem>) {
        val start = initial(items)
        val edges = populated(items).associateWith { layer ->
            items.filter { it.layer == layer && it.visible && it.kind == "layer" }
                .mapNotNull { destination(items, layer, it.target) }.toSet()
        }
        fun reachable(from: Int): Set<Int> {
            val seen = mutableSetOf(from)
            val pending = ArrayDeque<Int>().apply { add(from) }
            while (pending.isNotEmpty()) {
                edges[pending.removeFirst()].orEmpty().forEach {
                    if (seen.add(it)) pending.add(it)
                }
            }
            return seen
        }
        require(reachable(start).all { start in reachable(it) }) { "Each reachable layer needs a route back to layer $start" }
    }
}
