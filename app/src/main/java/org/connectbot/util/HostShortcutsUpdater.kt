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

package org.connectbot.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.ui.MainActivity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes the most recently connected hosts as dynamic launcher shortcuts
 * so users can long-press the app icon (or use launcher widgets/search) to
 * jump straight into a favorite host.
 *
 * Shortcut IDs use the same "host-{id}" scheme as pinned shortcuts created
 * via [MainActivity], so pinned shortcuts stay in sync when a host is renamed.
 */
@Singleton
class HostShortcutsUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hostRepository: HostRepository,
    dispatchers: CoroutineDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    /**
     * Begin observing the host list and keep dynamic shortcuts up to date.
     * Safe to call once from [android.app.Application.onCreate].
     */
    fun start() {
        scope.launch {
            hostRepository.observeHosts()
                .map { hosts -> selectShortcutHosts(hosts, maxShortcutCount()) }
                .distinctUntilChanged()
                .collect { hosts -> publishShortcuts(hosts) }
        }
    }

    private fun maxShortcutCount(): Int =
        ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            .coerceAtMost(MAX_DYNAMIC_SHORTCUTS)

    private fun publishShortcuts(hosts: List<Host>) {
        val shortcuts = hosts.mapIndexed { rank, host ->
            buildShortcutInfo(context, host, rank)
        }
        try {
            if (!ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)) {
                Timber.w("Dynamic shortcut update was rate-limited")
            } else {
                Timber.d("Published ${shortcuts.size} dynamic host shortcuts")
            }
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Failed to publish dynamic host shortcuts")
        } catch (e: IllegalStateException) {
            Timber.w(e, "Failed to publish dynamic host shortcuts")
        }
    }

    companion object {
        private const val MAX_DYNAMIC_SHORTCUTS = 4

        /**
         * Pick the hosts to expose as launcher shortcuts: most recently
         * connected first, with never-connected hosts filling remaining
         * slots alphabetically.
         */
        fun selectShortcutHosts(hosts: List<Host>, max: Int): List<Host> = hosts
            .filter { !it.isTemporary && it.nickname.isNotBlank() }
            .sortedWith(compareByDescending<Host> { it.lastConnect }.thenBy { it.nickname })
            .take(max.coerceAtLeast(0))

        /**
         * Build the shortcut for a host, launching the same ACTION_VIEW
         * connection URI used by pinned shortcuts.
         */
        fun buildShortcutInfo(context: Context, host: Host, rank: Int): ShortcutInfoCompat {
            val intent = Intent(Intent.ACTION_VIEW, host.getUri())
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            return ShortcutInfoCompat.Builder(context, "host-${host.id}")
                .setShortLabel(host.nickname)
                .setLongLabel(host.nickname)
                .setIcon(ShortcutIconGenerator.generateShortcutIcon(context, host.color, IconStyle.TERMINAL))
                .setIntent(intent)
                .setRank(rank)
                .build()
        }
    }
}
