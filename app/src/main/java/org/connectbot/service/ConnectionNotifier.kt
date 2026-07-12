/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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
import org.connectbot.util.formatDuration
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
            when (id) {
                COMPLETION_CHANNEL -> createNotificationChannel(
                    context,
                    id,
                    context.getString(R.string.notification_channel_completions),
                    NotificationManager.IMPORTANCE_HIGH,
                )

                else -> createNotificationChannel(context, id)
            }
        }

        return builder
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(
        context: Context,
        id: String,
        name: String = context.getString(R.string.app_name),
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
    ) {
        val nc = NotificationChannel(id, name, importance)
        getNotificationManager(context).createNotificationChannel(nc)
    }

    private fun newActivityNotification(
        context: Context,
        host: Host,
        tmuxTarget: String? = null,
        tmuxLabel: String? = null,
    ): Notification {
        val builder = newNotificationBuilder(context, NOTIFICATION_CHANNEL)
        val res = context.resources

        val contentText = if (tmuxLabel != null) {
            res.getString(R.string.notification_text_tmux, host.nickname, tmuxLabel)
        } else {
            res.getString(R.string.notification_text, host.nickname)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            tmuxTarget?.hashCode() ?: 0,
            deepLinkIntent(context, host, tmuxTarget),
            pendingIntentFlags,
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

    /**
     * Intent that opens the console for [host], optionally landing on a tmux
     * window via a `tmux` query parameter ("sessionId|windowId").
     */
    private fun deepLinkIntent(context: Context, host: Host, tmuxTarget: String?): Intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = if (tmuxTarget != null) {
            host.getUri().buildUpon().appendQueryParameter(TMUX_QUERY_PARAM, tmuxTarget).build()
        } else {
            host.getUri()
        }
    }

    private fun newRunningNotification(context: Context): Notification {
        val builder = newNotificationBuilder(context, NOTIFICATION_CHANNEL)
        val res = context.resources

        val pendingIntent = PendingIntent.getActivity(
            context,
            ONLINE_NOTIFICATION,
            Intent(context, MainActivity::class.java),
            pendingIntentFlags,
        )

        val disconnectIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.DISCONNECT_ACTION
        }

        val disconnectPendingIntent = PendingIntent.getActivity(
            context,
            ONLINE_DISCONNECT_NOTIFICATION,
            disconnectIntent,
            pendingIntentFlags,
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
                disconnectPendingIntent,
            )

        return builder.build()
    }

    fun showActivityNotification(context: Service, host: Host) {
        getNotificationManager(context).notify(ACTIVITY_NOTIFICATION, newActivityNotification(context, host))
    }

    /**
     * Activity notification for a tmux window bell: tapping it deep-links to
     * that session/window via a `tmux` query parameter on the host URI.
     * @param tmuxTarget "sessionId|windowId"
     * @param tmuxLabel human-readable "session:window" for the text
     */
    fun showTmuxActivityNotification(context: Service, host: Host, tmuxTarget: String, tmuxLabel: String) {
        getNotificationManager(context).notify(
            ACTIVITY_NOTIFICATION,
            newActivityNotification(context, host, tmuxTarget, tmuxLabel),
        )
    }

    /**
     * Heads-up notification for a long-running command that finished while the
     * app was in the background. Terminal output is intentionally excluded
     * because notification listeners can read private notification content.
     * Repeat completions on the same target (host shell or tmux window) update
     * in place; distinct targets stack.
     *
     * @param tmuxTarget "sessionId|windowId" when the command ran in a tmux
     *   window, null for the host shell
     * @param tmuxLabel human-readable "session:window", null for the host shell
     */
    fun showCommandCompletionNotification(
        context: Service,
        host: Host,
        tmuxTarget: String?,
        tmuxLabel: String?,
        durationMs: Long,
    ) {
        val notificationId = completionNotificationId(host, tmuxTarget)
        getNotificationManager(context).notify(
            notificationId,
            newCommandCompletionNotification(
                context,
                host,
                tmuxTarget,
                tmuxLabel,
                durationMs,
            ),
        )
    }

    internal fun newCommandCompletionNotification(
        context: Context,
        host: Host,
        tmuxTarget: String?,
        tmuxLabel: String?,
        durationMs: Long,
    ): Notification {
        val res = context.resources

        val title = if (tmuxLabel != null) {
            res.getString(R.string.notification_command_finished, "${host.nickname} · $tmuxLabel")
        } else {
            res.getString(R.string.notification_command_finished, host.nickname)
        }
        val status = res.getString(
            R.string.notification_command_status,
            formatDuration(durationMs),
        )

        val notificationId = completionNotificationId(host, tmuxTarget)
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            deepLinkIntent(context, host, tmuxTarget),
            pendingIntentFlags,
        )

        val publicNotification = NotificationCompat.Builder(context, COMPLETION_CHANNEL)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(res.getString(R.string.notification_channel_completions))
            .setContentText(status)
            .build()

        return newNotificationBuilder(context, COMPLETION_CHANNEL)
            .setContentTitle(title)
            .setContentText(status)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()
    }

    private fun completionNotificationId(host: Host, tmuxTarget: String?): Int = "completion:${host.id}:${tmuxTarget ?: "shell"}".hashCode()

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
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING,
        )
    }

    fun hideRunningNotification(context: Service) {
        context.stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val ONLINE_NOTIFICATION = 1
        private const val ACTIVITY_NOTIFICATION = 2

        /** Query parameter carrying a "sessionId|windowId" tmux deep-link target. */
        const val TMUX_QUERY_PARAM = "tmux"
        private const val ONLINE_DISCONNECT_NOTIFICATION = 3
        private const val NOTIFICATION_CHANNEL = "my_connectbot_channel"
        private const val COMPLETION_CHANNEL = "command_completion_channel"
    }
}
