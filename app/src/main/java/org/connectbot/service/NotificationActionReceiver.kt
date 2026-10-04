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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Private PendingIntent target. Actions are processed without opening or restarting a session. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var notifier: ConnectionNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        notifier.handleAction(
            context.applicationContext,
            intent.action,
            intent.getStringExtra(ConnectionNotifier.EXTRA_SESSION_ID).orEmpty(),
            onComplete = pending::finish,
        )
    }
}
