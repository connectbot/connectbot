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

package org.connectbot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.ui.MainActivity
import org.connectbot.util.HostConstants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages notifications for ConnectBot connections.
 *
 * @author Kenny Root
 *
 * Based on the concept from jasta's blog post.
 */
@Singleton
class ConnectionNotifier @Inject constructor() {
    private val pendingIntentFlags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    private fun getNotificationManager(context: Context): NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun newNotificationBuilder(context: Context, id: String): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, id)
            .setSmallIcon(R.drawable.notification_icon)
            .setWhen(System.currentTimeMillis())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context, id)
        }

        return builder
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(context: Context, id: String) {
        val nc = NotificationChannel(
            id,
            context.getString(R.string.app_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getNotificationManager(context).createNotificationChannel(nc)
    }

    private fun newActivityNotification(context: Context, host: Host): Notification {
        val builder = newNotificationBuilder(context, NOTIFICATION_CHANNEL)
        val res = context.resources

        val contentText = res.getString(R.string.notification_text, host.nickname)

        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = host.getUri()
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            pendingIntentFlags
        )

        builder.setContentTitle(res.getString(R.string.app_name))
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        val ledOnMS = 300
        val ledOffMS = 1000
        builder.setDefaults(Notification.DEFAULT_LIGHTS)

        val ledColor = when (host.color) {
            HostConstants.COLOR_RED -> Color.RED
            HostConstants.COLOR_GREEN -> Color.GREEN
            HostConstants.COLOR_BLUE -> Color.BLUE
            else -> Color.WHITE
        }
        builder.setLights(ledColor, ledOnMS, ledOffMS)

        return builder.build()
    }

    private fun newRunningNotification(context: Context): Notification {
        val builder = newNotificationBuilder(context, NOTIFICATION_CHANNEL)
        val res = context.resources

        val pendingIntent = PendingIntent.getActivity(
            context,
            ONLINE_NOTIFICATION,
            Intent(context, MainActivity::class.java),
            pendingIntentFlags
        )

        val disconnectIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.DISCONNECT_ACTION
        }

        val disconnectPendingIntent = PendingIntent.getActivity(
            context,
            ONLINE_DISCONNECT_NOTIFICATION,
            disconnectIntent,
            pendingIntentFlags
        )

        builder.setOngoing(true)
            .setWhen(0)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setContentTitle(res.getString(R.string.app_name))
            .setContentText(res.getString(R.string.app_is_running))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                res.getString(R.string.list_host_disconnect),
                disconnectPendingIntent
            )

        return builder.build()
    }

    fun showActivityNotification(context: Service, host: Host) {
        getNotificationManager(context).notify(ACTIVITY_NOTIFICATION, newActivityNotification(context, host))
    }

    fun showRunningNotification(context: Service) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            showRunningNotificationWithType(context)
            return
        }

        context.startForeground(ONLINE_NOTIFICATION, newRunningNotification(context))
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun showRunningNotificationWithType(context: Service) {
        context.startForeground(
            ONLINE_NOTIFICATION,
            newRunningNotification(context),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        )
    }

    fun hideRunningNotification(context: Service) {
        context.stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val ONLINE_NOTIFICATION = 1
        private const val ACTIVITY_NOTIFICATION = 2
        private const val ONLINE_DISCONNECT_NOTIFICATION = 3
        private const val NOTIFICATION_CHANNEL = "my_connectbot_channel"
    }
}
