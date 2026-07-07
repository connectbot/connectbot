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
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.entity.Host
import org.connectbot.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HostShortcutsUpdaterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun host(
        id: Long,
        nickname: String,
        lastConnect: Long = 0,
    ) = Host(
        id = id,
        nickname = nickname,
        protocol = "ssh",
        username = "user",
        hostname = "example.com",
        port = 22,
        lastConnect = lastConnect,
    )

    @Test
    fun `most recently connected hosts come first`() {
        val hosts = listOf(
            host(1, "alpha", lastConnect = 100),
            host(2, "bravo", lastConnect = 300),
            host(3, "charlie", lastConnect = 200),
        )

        val selected = HostShortcutsUpdater.selectShortcutHosts(hosts, 4)

        assertThat(selected.map { it.nickname })
            .containsExactly("bravo", "charlie", "alpha")
    }

    @Test
    fun `never connected hosts fill remaining slots alphabetically`() {
        val hosts = listOf(
            host(1, "zebra"),
            host(2, "apple"),
            host(3, "recent", lastConnect = 500),
        )

        val selected = HostShortcutsUpdater.selectShortcutHosts(hosts, 4)

        assertThat(selected.map { it.nickname })
            .containsExactly("recent", "apple", "zebra")
    }

    @Test
    fun `selection is capped at max`() {
        val hosts = (1L..10L).map { host(it, "host$it", lastConnect = it) }

        val selected = HostShortcutsUpdater.selectShortcutHosts(hosts, 4)

        assertThat(selected).hasSize(4)
        assertThat(selected.first().nickname).isEqualTo("host10")
    }

    @Test
    fun `temporary and unnamed hosts are excluded`() {
        val hosts = listOf(
            host(-1, "temporary", lastConnect = 900),
            host(5, "", lastConnect = 800),
            host(6, "named", lastConnect = 100),
        )

        val selected = HostShortcutsUpdater.selectShortcutHosts(hosts, 4)

        assertThat(selected.map { it.nickname }).containsExactly("named")
    }

    @Test
    fun `negative max returns empty list`() {
        val hosts = listOf(host(1, "alpha"))

        assertThat(HostShortcutsUpdater.selectShortcutHosts(hosts, -1)).isEmpty()
    }

    @Test
    fun `shortcut info targets main activity with host uri`() {
        val target = host(42, "prod-web", lastConnect = 100)

        val shortcut = HostShortcutsUpdater.buildShortcutInfo(context, target, rank = 1)

        assertThat(shortcut.id).isEqualTo("host-42")
        assertThat(shortcut.shortLabel).isEqualTo("prod-web")
        assertThat(shortcut.rank).isEqualTo(1)

        val intent = shortcut.intent
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.data).isEqualTo(target.getUri())
        assertThat(intent.component?.className).isEqualTo(MainActivity::class.java.name)
    }
}
