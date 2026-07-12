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
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConnectionNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun commandCompletionNotificationRedactsPublicContent() {
        val host = Host(id = 7, nickname = "private-host", hostname = "example.com")
        val privateTmuxLabel = "private-session:private-window"
        val notification = ConnectionNotifier().newCommandCompletionNotification(
            context = context,
            host = host,
            tmuxTarget = "\$1|@2",
            tmuxLabel = privateTmuxLabel,
            durationMs = 60_000,
        )

        assertThat(notification.visibility).isEqualTo(NotificationCompat.VISIBILITY_PRIVATE)
        val privateTitle = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        assertThat(privateTitle).contains("private-host", privateTmuxLabel)
        assertThat(notification.extras.containsKey(Notification.EXTRA_BIG_TEXT)).isFalse()

        val publicNotification = requireNotNull(notification.publicVersion)
        val publicTitle = publicNotification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val publicText = publicNotification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertThat(publicTitle)
            .isEqualTo(context.getString(R.string.notification_channel_completions))
        assertThat(publicTitle).doesNotContain("private-host", privateTmuxLabel)
        assertThat(publicText).doesNotContain("private-host", privateTmuxLabel)
        assertThat(publicNotification.extras.containsKey(Notification.EXTRA_BIG_TEXT)).isFalse()
    }
}
