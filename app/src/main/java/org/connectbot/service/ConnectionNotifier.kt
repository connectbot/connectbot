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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.ui.MainActivity
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** Immutable connection data; notification work never accesses a live transport. */
data class NotificationSession(
    val id: String,
    val host: Host,
    val connected: Boolean,
    val connecting: Boolean = false,
    val uri: Uri = host.getUri(),
)

/** Serializes notification state and Android IPC on the injected I/O dispatcher. */
@Singleton
class ConnectionNotifier @Inject constructor(dispatchers: CoroutineDispatchers) {
    private data class Notice(val session: NotificationSession, val reason: DisconnectReason?, val time: Long)

    private val work = Channel<() -> Unit>(Channel.UNLIMITED)
    private var sessions = emptyList<NotificationSession>()
    private val notices = linkedMapOf<String, Notice>()
    private val muted = mutableSetOf<String>()
    private var foreground = false
    private var restored = false

    init {
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            for (operation in work) {
                try {
                    operation()
                } catch (e: SecurityException) {
                    // Notification permission may be revoked while sessions are active.
                    Timber.w(e, "Notification permission unavailable")
                } catch (e: IllegalArgumentException) {
                    Timber.w(e, "Unable to update notification")
                }
            }
        }
    }

    private fun enqueue(context: Context, onComplete: () -> Unit = {}, operation: () -> Unit) {
        work.trySend {
            try {
                restoreNotices(context)
                operation()
            } finally {
                onComplete()
            }
        }
    }

    private fun restoreNotices(context: Context) {
        if (restored) return
        // Posted alerts outlive the service and may also outlive this process.
        manager(context).activeNotifications.filter { it.id == ACTIVITY_NOTIFICATION && it.tag != null }.forEach { posted ->
            val extras = posted.notification.extras
            val uri = extras.getString(EXTRA_HOST_URI) ?: return@forEach
            val session = NotificationSession(
                id = posted.tag,
                host = Host(id = extras.getLong(EXTRA_HOST_ID), nickname = extras.getString(EXTRA_HOST_NAME).orEmpty()),
                connected = false,
                uri = uri.toUri(),
            )
            val reason = extras.getString(EXTRA_REASON)?.let { name -> DisconnectReason.entries.find { it.name == name } }
            notices[session.id] = Notice(session, reason, posted.notification.`when`)
        }
        // Retire the previous implementation's single, untagged bell notification.
        manager(context).cancel(ACTIVITY_NOTIFICATION)
        restored = true
    }

    private fun manager(context: Context) = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun builder(context: Context, channel: String): NotificationCompat.Builder {
        val priority = when (channel) {
            SESSION_CHANNEL -> NotificationCompat.PRIORITY_LOW
            ATTENTION_CHANNEL -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_HIGH
        }
        createNotificationChannel(context, channel)
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.notification_icon)
            .setPriority(priority)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
    }

    private fun actionIntent(context: Context, action: String, id: String = ""): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            data = Uri.Builder().scheme("connectbot-notification").authority(action).appendPath(id).build()
            putExtra(EXTRA_SESSION_ID, id)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openIntent(context: Context, session: NotificationSession, reconnect: Boolean = false): PendingIntent = PendingIntent.getActivity(
        context,
        if (reconnect) 1 else 0,
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = session.uri
            // Categories distinguish sessions even when their connection URIs are identical.
            addCategory("org.connectbot.session.${session.id}")
            putExtra(EXTRA_SESSION_ID, session.id)
            putExtra(EXTRA_RECONNECT, reconnect)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun summaryText(context: Context): String {
        val count = sessions.count { it.connected }
        val connected = context.resources.getQuantityString(R.plurals.notification_connected_sessions, count, count)
        if (notices.isEmpty()) return connected
        val attention = context.resources.getQuantityString(R.plurals.notification_attention_sessions, notices.size, notices.size)
        return context.getString(R.string.notification_summary, connected, attention)
    }

    private fun publicVersion(context: Context): Notification = NotificationCompat.Builder(context, SESSION_CHANNEL)
        .setSmallIcon(R.drawable.notification_icon)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(summaryText(context))
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()

    private fun eventText(context: Context, notice: Notice): String = notice.reason?.let {
        context.getString(it.descriptionResource())
    } ?: context.getString(R.string.notification_bell)

    private fun summary(context: Context): Notification {
        val text = summaryText(context)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(text)
        val rows = sessions.associateBy { it.id }.toMutableMap()
        notices.values.forEach { rows.putIfAbsent(it.session.id, it.session) }
        rows.values.sortedWith(
            compareByDescending<NotificationSession> { notices.containsKey(it.id) }
                .thenByDescending { notices[it.id]?.time ?: 0L }
                .thenBy { it.host.nickname },
        ).take(6).forEach { session ->
            val notice = notices[session.id]
            val status = context.getString(
                when {
                    session.connected -> R.string.notification_connected
                    session.connecting -> R.string.notification_connecting
                    else -> R.string.notification_disconnected
                },
            )
            val detail = if (notice != null) {
                context.getString(
                    R.string.notification_event_time,
                    context.getString(R.string.notification_summary, status, eventText(context, notice)),
                    DateFormat.getTimeFormat(context).format(Date(notice.time)),
                )
            } else {
                status
            }
            style.addLine(context.getString(R.string.notification_session_detail, session.host.nickname, detail))
        }
        if (rows.size > 6) {
            val remaining = rows.size - 6
            style.setSummaryText(context.resources.getQuantityString(R.plurals.notification_more_sessions, remaining, remaining))
        }
        val result = builder(context, if (notices.isEmpty()) SESSION_CHANNEL else ATTENTION_CHANNEL)
            .setContentTitle(text)
            .setContentText(text)
            .setStyle(style)
            .setOngoing(foreground)
            .setWhen(0)
            .setSilent(notices.isEmpty())
            .setGroupSummary(true)
            .setPublicVersion(publicVersion(context))
            .setDeleteIntent(actionIntent(context, ACTION_SEEN_ALL))
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    ONLINE_NOTIFICATION,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        if (foreground) {
            result.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.list_host_disconnect),
                PendingIntent.getActivity(
                    context,
                    3,
                    Intent(context, MainActivity::class.java).setAction(MainActivity.DISCONNECT_ACTION),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        return result.build()
    }

    private fun postNotice(context: Context, notice: Notice) {
        val session = notice.session
        val result = builder(context, if (notice.reason == null) BELL_CHANNEL else LOSS_CHANNEL)
            .setContentTitle(session.host.nickname)
            .setContentText(eventText(context, notice))
            .setStyle(NotificationCompat.BigTextStyle().bigText(eventText(context, notice)))
            .setWhen(notice.time)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setPublicVersion(publicVersion(context))
            .setContentIntent(openIntent(context, session))
            .setDeleteIntent(actionIntent(context, ACTION_SEEN, session.id))
            .addExtras(
                Bundle().apply {
                    putLong(EXTRA_HOST_ID, session.host.id)
                    putString(EXTRA_HOST_NAME, session.host.nickname)
                    putString(EXTRA_HOST_URI, session.uri.toString())
                    putString(EXTRA_REASON, notice.reason?.name)
                },
            )
            .addAction(0, context.getString(R.string.notification_mark_seen), actionIntent(context, ACTION_SEEN, session.id))
        if (notice.reason == null) {
            result.addAction(0, context.getString(R.string.notification_mute_bells), actionIntent(context, ACTION_MUTE, session.id))
        } else {
            result.addAction(0, context.getString(R.string.console_menu_reconnect), openIntent(context, session, reconnect = true))
        }
        manager(context).notify(session.id, ACTIVITY_NOTIFICATION, result.build())
    }

    private fun refresh(context: Context) {
        notices.values.forEach { postNotice(context, it) }
        if (foreground || notices.isNotEmpty()) {
            manager(context).notify(ONLINE_NOTIFICATION, summary(context))
        } else {
            manager(context).cancel(ONLINE_NOTIFICATION)
        }
    }

    private fun clearNotice(context: Context, id: String) {
        notices.remove(id)
        manager(context).cancel(id, ACTIVITY_NOTIFICATION)
    }

    private fun replaceSessions(context: Context, current: List<NotificationSession>) {
        // A new connection attempt to the same host gets a new identity and fresh mute/attention state.
        val replaced = notices.values.filter { notice ->
            current.any { it.host.id == notice.session.host.id && it.id != notice.session.id }
        }.map { it.session.id }
        replaced.forEach { clearNotice(context, it) }
        sessions = current
        muted.retainAll(current.map { it.id }.toSet())
    }

    fun updateSessions(context: Context, current: List<NotificationSession>) = enqueue(context) {
        replaceSessions(context, current)
        refresh(context)
    }

    fun showActivityNotification(context: Context, session: NotificationSession) = enqueue(context) {
        if (session.id !in muted && sessions.any { it.id == session.id && it.connected } && notices[session.id]?.reason == null) {
            val notice = Notice(session, null, System.currentTimeMillis())
            notices[session.id] = notice
            refresh(context)
        }
    }

    fun sessionEnded(context: Context, session: NotificationSession, reason: DisconnectReason, alert: Boolean) = enqueue(context) {
        clearNotice(context, session.id)
        muted.remove(session.id)
        sessions = sessions.map { if (it.id == session.id) session else it }
        if (alert && reason !in setOf(DisconnectReason.USER_REQUESTED, DisconnectReason.SESSION_EXIT)) {
            val notice = Notice(session, reason, System.currentTimeMillis())
            notices[session.id] = notice
        }
        refresh(context)
    }

    fun handleAction(context: Context, action: String?, id: String, onComplete: () -> Unit = {}) = enqueue(context, onComplete) {
        when (action) {
            ACTION_SEEN -> clearNotice(context, id)

            ACTION_MUTE -> {
                if (sessions.any { it.id == id }) muted.add(id)
                clearNotice(context, id)
            }

            ACTION_SEEN_ALL -> notices.keys.toList().forEach { clearNotice(context, it) }
        }
        refresh(context)
    }

    fun showRunningNotification(context: Service, current: List<NotificationSession>) = enqueue(context) {
        replaceSessions(context, current)
        foreground = true
        val notification = summary(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.startForeground(ONLINE_NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            context.startForeground(ONLINE_NOTIFICATION, notification)
        }
    }

    fun hideRunningNotification(context: Service) = enqueue(context) {
        foreground = false
        context.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        refresh(context)
    }

    companion object {
        /** Register channels before opening their settings, even if no session has connected yet. */
        @WorkerThread
        fun createNotificationChannels(context: Context) {
            listOf(SESSION_CHANNEL, ATTENTION_CHANNEL, BELL_CHANNEL, LOSS_CHANNEL).forEach {
                createNotificationChannel(context, it)
            }
        }

        private fun createNotificationChannel(context: Context, channel: String) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val importance = when (channel) {
                SESSION_CHANNEL -> NotificationManager.IMPORTANCE_LOW
                ATTENTION_CHANNEL -> NotificationManager.IMPORTANCE_DEFAULT
                else -> NotificationManager.IMPORTANCE_HIGH
            }
            val name = when (channel) {
                BELL_CHANNEL -> R.string.notification_channel_bells
                LOSS_CHANNEL -> R.string.notification_channel_connections
                ATTENTION_CHANNEL -> R.string.notification_channel_attention
                else -> R.string.notification_channel_sessions
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, context.getString(name), importance).apply {
                    setShowBadge(channel != SESSION_CHANNEL)
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }

        const val EXTRA_SESSION_ID = "notificationSessionId"
        const val EXTRA_RECONNECT = "notificationReconnect"
        const val ACTION_SEEN = "org.connectbot.notification.SEEN"
        const val ACTION_MUTE = "org.connectbot.notification.MUTE"
        const val ACTION_SEEN_ALL = "org.connectbot.notification.SEEN_ALL"
        internal const val ONLINE_NOTIFICATION = 1
        internal const val ACTIVITY_NOTIFICATION = 2
        internal const val SESSION_CHANNEL = "connectbot_sessions"
        internal const val ATTENTION_CHANNEL = "connectbot_session_attention"

        // Channel importance cannot be raised after creation; use a new ID for visual pop-ups.
        internal const val BELL_CHANNEL = "connectbot_bell_alerts"
        internal const val LOSS_CHANNEL = "connectbot_connection_alerts"
        private const val EXTRA_HOST_ID = "notificationHostId"
        private const val EXTRA_HOST_NAME = "notificationHostName"
        private const val EXTRA_HOST_URI = "notificationHostUri"
        private const val EXTRA_REASON = "notificationReason"
        private const val GROUP = "org.connectbot.sessions"
    }
}
