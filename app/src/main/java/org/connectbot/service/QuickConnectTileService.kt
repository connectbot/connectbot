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

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.R
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.ui.MainActivity
import org.connectbot.util.HostShortcutsUpdater
import timber.log.Timber
import javax.inject.Inject

/**
 * Quick Settings tile that jumps straight into the most recently connected
 * host. When no host has been connected yet, tapping the tile opens the
 * host list instead.
 */
@AndroidEntryPoint
class QuickConnectTileService : TileService() {

    @Inject
    lateinit var hostRepository: HostRepository

    @Inject
    lateinit var dispatchers: CoroutineDispatchers

    private lateinit var scope: CoroutineScope

    /** The host the tile currently targets; refreshed each time QS is opened. */
    private var favoriteHost: Host? = null

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            favoriteHost = withContext(dispatchers.io) {
                HostShortcutsUpdater.selectShortcutHosts(hostRepository.getHosts(), 1).firstOrNull()
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val host = favoriteHost
        if (isLocked) {
            unlockAndRun { launchHost(host) }
        } else {
            launchHost(host)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val host = favoriteHost
        if (host != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = host.nickname
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.qs_tile_label)
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.qs_tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.qs_tile_no_hosts)
            }
        }
        tile.updateTile()
    }

    private fun launchHost(host: Host?) {
        Timber.d("Quick Settings tile launching host: ${host?.nickname}")
        val intent = if (host != null) {
            Intent(Intent.ACTION_VIEW, host.getUri()).setClass(this, MainActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
