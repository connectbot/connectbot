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

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class ConnectionNotifierTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var notifier: ConnectionNotifier
    private lateinit var service: NotificationTestService
    private lateinit var manager: NotificationManager
    private val first = NotificationSession("session-1", Host(id = 1L, nickname = "production"), connected = true)
    private val second = NotificationSession("session-2", Host(id = 2L, nickname = "staging"), connected = true)

    @Before
    fun setUp() {
        service = Robolectric.buildService(NotificationTestService::class.java).create().get()
        manager = service.getSystemService(NotificationManager::class.java)
        notifier = ConnectionNotifier(CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
    }

    private fun summary(): Notification = manager.activeNotifications.single { it.id == ConnectionNotifier.ONLINE_NOTIFICATION }.notification

    private fun alert(id: String): Notification = manager.activeNotifications.single { it.tag == id }.notification

    private fun text(notification: Notification): String = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    private fun title(notification: Notification): String = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    @Test
    fun summaryCountsOnlyLiveSessionsAndExpandsWithConnectionStates() = runTest(dispatcher) {
        notifier.showRunningNotification(service, listOf(first, second.copy(connected = false, connecting = true)))
        advanceUntilIdle()

        val notification = summary()
        assertEquals("1 session connected", title(notification))
        assertEquals("1 session connected", notification.extras.getCharSequence(Notification.EXTRA_TITLE_BIG).toString())
        assertEquals("production", text(notification))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        val rows = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)!!.map { it.toString() }
        assertEquals(listOf("production: Connected", "staging: Connecting…"), rows)
        assertEquals("1 session connected", text(notification.publicVersion))
        assertFalse(notification.publicVersion.extras.toString().contains("production"))
    }

    @Test
    fun collapsedSummaryLimitsConnectedServerNamesAndKeepsPublicVersionPrivate() = runTest(dispatcher) {
        val sessions = listOf("delta", "charlie", "bravo", "alpha", "echo").mapIndexed { index, nickname ->
            NotificationSession("session-$index", Host(id = index.toLong(), nickname = nickname), connected = true)
        }
        notifier.showRunningNotification(service, sessions)
        advanceUntilIdle()

        val notification = summary()
        assertEquals("5 sessions connected", title(notification))
        assertEquals("alpha, bravo, charlie · +2 more sessions", text(notification))
        assertEquals("5 sessions connected", text(notification.publicVersion))
        sessions.forEach { assertFalse(notification.publicVersion.extras.toString().contains(it.host.nickname)) }
    }

    @Test
    fun collapsedSummaryOmitsServerNamesWhenNoSessionsAreConnected() = runTest(dispatcher) {
        notifier.showRunningNotification(service, listOf(first.copy(connected = false, connecting = true)))
        advanceUntilIdle()

        assertEquals("0 sessions connected", title(summary()))
        assertNull(summary().extras.getCharSequence(Notification.EXTRA_TEXT))
    }

    @Test
    fun bellsInDifferentSessionsDoNotReplaceEachOtherAndSortAttentionFirst() = runTest(dispatcher) {
        notifier.showRunningNotification(service, listOf(first, second))
        notifier.showActivityNotification(service, second)
        notifier.showActivityNotification(service, first)
        advanceUntilIdle()

        assertEquals(3, manager.activeNotifications.size)
        assertEquals("2 sessions connected · 2 need attention", title(summary()))
        assertEquals("production, staging", text(summary()))
        assertEquals("Terminal bell", text(alert(first.id)))
        assertEquals(alert(first.id).group, summary().group)
        assertNotEquals(alert(first.id).contentIntent, alert(second.id).contentIntent)
        assertNotEquals(alert(first.id).deleteIntent, alert(second.id).deleteIntent)
        assertEquals(Notification.VISIBILITY_PRIVATE, alert(first.id).visibility)
        assertTrue(alert(first.id).flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(2, alert(first.id).actions.size)
        assertEquals("Mark seen", alert(first.id).actions[0].title)
        assertEquals("Mute bells", alert(first.id).actions[1].title)
        val target = shadowOf(alert(first.id).contentIntent).savedIntent
        assertEquals(first.host.getUri(), target.data)
        assertEquals(first.id, target.getStringExtra(ConnectionNotifier.EXTRA_SESSION_ID))
    }

    @Test
    fun repeatedBellsUpdateOneAlertAndLatestTimestamp() = runTest(dispatcher) {
        notifier.updateSessions(service, listOf(first))
        notifier.showActivityNotification(service, first)
        advanceUntilIdle()
        val originalTime = alert(first.id).`when`
        notifier.showActivityNotification(service, first)
        advanceUntilIdle()
        assertEquals(2, manager.activeNotifications.size)
        assertEquals("1 session connected · 1 needs attention", title(summary()))
        assertTrue(alert(first.id).`when` >= originalTime)
    }

    @Test
    fun swipeIntentAcknowledgesOnlyItsSessionAndKeepsForegroundSummary() = runTest(dispatcher) {
        notifier.showRunningNotification(service, listOf(first, second))
        notifier.showActivityNotification(service, first)
        notifier.showActivityNotification(service, second)
        advanceUntilIdle()
        val swipe = shadowOf(alert(first.id).deleteIntent).savedIntent
        assertEquals(NotificationActionReceiver::class.java.name, swipe.component!!.className)
        notifier.handleAction(service, swipe.action, swipe.getStringExtra(ConnectionNotifier.EXTRA_SESSION_ID)!!)
        advanceUntilIdle()
        assertEquals(2, manager.activeNotifications.size)
        assertEquals("2 sessions connected · 1 needs attention", title(summary()))
        assertNotNull(alert(second.id))
    }

    @Test
    fun mutedBellsDoNotHideConnectionFailuresAndNewConnectionIsUnmuted() = runTest(dispatcher) {
        notifier.updateSessions(service, listOf(first))
        notifier.showActivityNotification(service, first)
        notifier.handleAction(service, ConnectionNotifier.ACTION_MUTE, first.id)
        notifier.showActivityNotification(service, first)
        advanceUntilIdle()
        assertTrue(manager.activeNotifications.isEmpty())

        notifier.sessionEnded(service, first.copy(connected = false), DisconnectReason.NETWORK_LOST, alert = true)
        advanceUntilIdle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals(ConnectionNotifier.LOSS_CHANNEL, alert(first.id).channelId)
        }
        assertEquals("Reconnect", alert(first.id).actions[1].title)
        assertTrue(shadowOf(alert(first.id).actions[1].actionIntent).savedIntent.getBooleanExtra(ConnectionNotifier.EXTRA_RECONNECT, false))

        val reconnected = first.copy(id = "new-session")
        notifier.updateSessions(service, listOf(reconnected))
        notifier.showActivityNotification(service, reconnected)
        advanceUntilIdle()
        assertFalse(manager.activeNotifications.any { it.tag == first.id })
        assertEquals("Terminal bell", text(alert(reconnected.id)))
    }

    @Test
    fun staleActionsDoNotAcknowledgeOrMuteNewConnection() = runTest(dispatcher) {
        notifier.updateSessions(service, listOf(first))
        val newSession = first.copy(id = "new-session")
        notifier.updateSessions(service, listOf(newSession))
        notifier.showActivityNotification(service, newSession)
        notifier.handleAction(service, ConnectionNotifier.ACTION_MUTE, first.id)
        notifier.showActivityNotification(service, newSession)
        advanceUntilIdle()
        assertEquals("Terminal bell", text(alert(newSession.id)))
        assertEquals("1 session connected · 1 needs attention", title(summary()))
    }

    @Test
    fun finalConnectionFailureSurvivesStoppingForegroundUntilSeen() = runTest(dispatcher) {
        notifier.showRunningNotification(service, listOf(first))
        notifier.sessionEnded(service, first.copy(connected = false), DisconnectReason.IO_ERROR, alert = true)
        notifier.updateSessions(service, emptyList())
        notifier.hideRunningNotification(service)
        advanceUntilIdle()
        assertEquals("0 sessions connected · 1 needs attention", title(summary()))
        assertFalse(summary().flags and Notification.FLAG_ONGOING_EVENT != 0)
        notifier.handleAction(service, ConnectionNotifier.ACTION_SEEN, first.id)
        advanceUntilIdle()
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun intentionalDisconnectsAndNormalExitsDoNotAlert() = runTest(dispatcher) {
        notifier.updateSessions(service, listOf(first, second))
        notifier.showActivityNotification(service, first)
        notifier.sessionEnded(service, first.copy(connected = false), DisconnectReason.USER_REQUESTED, alert = true)
        notifier.sessionEnded(service, second.copy(connected = false), DisconnectReason.SESSION_EXIT, alert = true)
        notifier.updateSessions(service, emptyList())
        advanceUntilIdle()
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun pendingAlertsRaisePriorityWithoutSoundAndPublicAlertsContainOnlyCounts() = runTest(dispatcher) {
        notifier.showRunningNotification(service, listOf(first, second))
        notifier.showActivityNotification(service, first)
        notifier.sessionEnded(service, second.copy(connected = false), DisconnectReason.AUTH_FAIL, alert = true)
        advanceUntilIdle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(ConnectionNotifier.SESSION_CHANNEL).importance)
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, manager.getNotificationChannel(ConnectionNotifier.ATTENTION_CHANNEL).importance)
            listOf(ConnectionNotifier.BELL_CHANNEL, ConnectionNotifier.LOSS_CHANNEL).forEach {
                val alerts = manager.getNotificationChannel(it)
                assertEquals(NotificationManager.IMPORTANCE_HIGH, alerts.importance)
                assertNull(alerts.sound)
                assertFalse(alerts.shouldVibrate())
            }
            assertEquals(ConnectionNotifier.ATTENTION_CHANNEL, summary().channelId)
        }
        assertEquals(NotificationCompat.PRIORITY_HIGH, alert(first.id).priority)
        assertEquals(NotificationCompat.PRIORITY_HIGH, alert(second.id).priority)
        assertEquals(NotificationCompat.PRIORITY_DEFAULT, summary().priority)
        val public = alert(second.id).publicVersion
        assertEquals("1 session connected · 2 need attention", text(public))
        assertEquals("ConnectBot", public.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(public.actions.isNullOrEmpty())
        assertFalse(public.extras.toString().contains("staging"))
        notifier.handleAction(service, ConnectionNotifier.ACTION_SEEN_ALL, "")
        advanceUntilIdle()
        assertEquals(NotificationCompat.PRIORITY_LOW, summary().priority)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals(ConnectionNotifier.SESSION_CHANNEL, summary().channelId)
        }
    }

    @Test
    fun summaryDismissalAcknowledgesAllAlerts() = runTest(dispatcher) {
        notifier.updateSessions(service, listOf(first, second))
        notifier.showActivityNotification(service, first)
        notifier.showActivityNotification(service, second)
        notifier.handleAction(service, ConnectionNotifier.ACTION_SEEN_ALL, "")
        advanceUntilIdle()
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun postedAlertsRemainActionableAfterNotifierIsRecreated() = runTest(dispatcher) {
        notifier.updateSessions(service, listOf(first, second))
        notifier.sessionEnded(service, first.copy(connected = false), DisconnectReason.NETWORK_LOST, alert = true)
        notifier.sessionEnded(service, second.copy(connected = false), DisconnectReason.IO_ERROR, alert = true)
        advanceUntilIdle()
        val afterRestart = ConnectionNotifier(CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
        afterRestart.handleAction(service, ConnectionNotifier.ACTION_SEEN, first.id)
        advanceUntilIdle()
        assertEquals("0 sessions connected · 1 needs attention", title(summary()))
        assertEquals(second.host.getUri(), shadowOf(alert(second.id).contentIntent).savedIntent.data)
        afterRestart.handleAction(service, ConnectionNotifier.ACTION_SEEN, second.id)
        advanceUntilIdle()
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun lateBellCannotReplaceALostConnectionAlert() = runTest(dispatcher) {
        notifier.updateSessions(service, listOf(first))
        notifier.sessionEnded(service, first.copy(connected = false), DisconnectReason.NETWORK_LOST, alert = true)
        notifier.showActivityNotification(service, first)
        advanceUntilIdle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals(ConnectionNotifier.LOSS_CHANNEL, alert(first.id).channelId)
        }
    }

    @Test
    @Config(sdk = [24])
    fun preChannelAndroidStillShowsGroupedSoundlessBellAlerts() = runTest(dispatcher) {
        notifier.showRunningNotification(service, listOf(first))
        notifier.showActivityNotification(service, first)
        advanceUntilIdle()
        assertEquals(NotificationCompat.PRIORITY_HIGH, alert(first.id).priority)
        assertEquals("1 session connected · 1 needs attention", title(summary()))
        assertEquals(alert(first.id).group, summary().group)
    }
}

class NotificationTestService : Service() {
    override fun onBind(intent: Intent): IBinder? = null
}
