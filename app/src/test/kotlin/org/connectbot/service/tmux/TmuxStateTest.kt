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

package org.connectbot.service.tmux

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TmuxStateTest {
    // ===== TmuxVersion =====

    @Test
    fun `parses release versions`() {
        val v = TmuxVersion.parse("tmux 3.7b")
        assertThat(v).isNotNull
        assertThat(v!!.major).isEqualTo(3)
        assertThat(v.minor).isEqualTo(7)
        assertThat(v.isSupported).isTrue()
        assertThat(v.supportsFlowControl).isTrue()
    }

    @Test
    fun `parses next and master builds as newest`() {
        assertThat(TmuxVersion.parse("tmux next-3.6")!!.atLeast(3, 6)).isTrue()
        assertThat(TmuxVersion.parse("tmux master")!!.isSupported).isTrue()
    }

    @Test
    fun `gates capabilities by version`() {
        val old = TmuxVersion.parse("tmux 2.6")!!
        assertThat(old.isSupported).isTrue()
        assertThat(old.supportsFlowControl).isFalse()

        assertThat(TmuxVersion.parse("tmux 2.5")!!.isSupported).isFalse()
        assertThat(TmuxVersion.parse("tmux 1.8")!!.isSupported).isFalse()
        assertThat(TmuxVersion.parse("tmux 3.1c")!!.supportsFlowControl).isFalse()
        assertThat(TmuxVersion.parse("tmux 3.2a")!!.supportsFlowControl).isTrue()
    }

    @Test
    fun `rejects non-tmux output`() {
        assertThat(TmuxVersion.parse("NO_TMUX")).isNull()
        assertThat(TmuxVersion.parse("bash: tmux: command not found")).isNull()
        assertThat(TmuxVersion.parse("")).isNull()
    }

    // ===== TmuxTarget =====

    @Test
    fun `target round-trips through encoding`() {
        val target = TmuxTarget("\$1", "@3", "%7", "main session")
        assertThat(TmuxTarget.decode(target.encode())).isEqualTo(target)
    }

    @Test
    fun `target with pipes in session name decodes`() {
        val target = TmuxTarget("\$1", "@3", "%7", "weird|name|here")
        assertThat(TmuxTarget.decode(target.encode())).isEqualTo(target)
    }

    @Test
    fun `malformed targets decode to null`() {
        assertThat(TmuxTarget.decode(null)).isNull()
        assertThat(TmuxTarget.decode("")).isNull()
        assertThat(TmuxTarget.decode("$1|@3")).isNull()
        assertThat(TmuxTarget.decode("a|b|c|d")).isNull()
    }

    // ===== Reducer =====

    private val baseState = TmuxHostState(
        availability = TmuxAvailability.READY,
        sessions = listOf(
            TmuxSessionInfo(
                id = "\$0",
                name = "main",
                attachState = TmuxAttachState.ATTACHED,
                activeWindowId = "@0",
                windows = listOf(
                    TmuxWindow(
                        id = "@0",
                        index = 0,
                        name = "shell",
                        active = true,
                        panes = listOf(
                            TmuxPaneRef("%0", 0, 100, 30, active = true),
                            TmuxPaneRef("%1", 1, 100, 29),
                        ),
                        activePaneId = "%0",
                    ),
                    TmuxWindow(id = "@1", index = 1, name = "logs"),
                ),
            ),
        ),
    )

    private fun reduce(notification: TmuxNotification, state: TmuxHostState = baseState): TmuxHostState =
        TmuxSessionManager.reduceSession(state, "\$0", notification)

    @Test
    fun `window close removes window and fixes active id`() {
        val closed = reduce(TmuxNotification.WindowClose("@0"))
        val session = closed.session("\$0")!!
        assertThat(session.windows.map { it.id }).containsExactly("@1")
        assertThat(session.activeWindowId).isEqualTo("@1")
    }

    @Test
    fun `window rename applies wherever the window lives`() {
        val renamed = reduce(TmuxNotification.WindowRenamed("@1", "build"))
        assertThat(renamed.session("\$0")!!.windows[1].name).isEqualTo("build")
    }

    @Test
    fun `window add creates placeholder once`() {
        val added = reduce(TmuxNotification.WindowAdd("@2"))
        assertThat(added.session("\$0")!!.windows.map { it.id }).containsExactly("@0", "@1", "@2")
        val again = TmuxSessionManager.reduceSession(added, "\$0", TmuxNotification.WindowAdd("@2"))
        assertThat(again).isEqualTo(added)
    }

    @Test
    fun `layout change updates pane geometry and drops vanished panes`() {
        val layout = TmuxNotification.LayoutChange(
            windowId = "@0",
            layout = "c08a,100x30,0,0[100x15,0,0,0,100x14,0,16,2]",
            visibleLayout = null,
            flags = null,
        )
        val changed = reduce(layout)
        val window = changed.session("\$0")!!.windows[0]
        assertThat(window.panes.map { it.id }).containsExactly("%0", "%2")
        assertThat(window.panes[0].height).isEqualTo(15)
        assertThat(window.panes[1].top).isEqualTo(16)
        assertThat(window.activePaneId).isEqualTo("%0")
    }

    @Test
    fun `layout change flags update bell and activity`() {
        val layout = TmuxNotification.LayoutChange(
            windowId = "@0",
            layout = "a87d,100x30,0,0,0",
            visibleLayout = null,
            flags = "*#!",
        )
        val changed = reduce(layout)
        val window = changed.session("\$0")!!.windows[0]
        assertThat(window.bell).isTrue()
        assertThat(window.activity).isTrue()
    }

    @Test
    fun `layout change removing active pane moves selection`() {
        val layout = TmuxNotification.LayoutChange(
            windowId = "@0",
            layout = "a87d,100x30,0,0,1",
            visibleLayout = null,
            flags = null,
        )
        val window = reduce(layout).session("\$0")!!.windows[0]
        assertThat(window.activePaneId).isEqualTo("%1")
    }

    @Test
    fun `session renamed with and without id`() {
        assertThat(
            reduce(TmuxNotification.SessionRenamed("\$0", "renamed")).session("\$0")!!.name,
        ).isEqualTo("renamed")
        assertThat(
            reduce(TmuxNotification.SessionRenamed(null, "implicit")).session("\$0")!!.name,
        ).isEqualTo("implicit")
    }

    @Test
    fun `session window changed moves active window`() {
        val changed = reduce(TmuxNotification.SessionWindowChanged("\$0", "@1"))
        assertThat(changed.session("\$0")!!.activeWindowId).isEqualTo("@1")
    }

    @Test
    fun `window pane changed moves active pane`() {
        val changed = reduce(TmuxNotification.WindowPaneChanged("@0", "%1"))
        assertThat(changed.session("\$0")!!.windows[0].activePaneId).isEqualTo("%1")
    }

    @Test
    fun `pause and continue mark the pane`() {
        val paused = reduce(TmuxNotification.Pause("%0"))
        assertThat(paused.session("\$0")!!.windows[0].panes[0].paused).isTrue()
        val resumed = TmuxSessionManager.reduceSession(paused, "\$0", TmuxNotification.Continue("%0"))
        assertThat(resumed.session("\$0")!!.windows[0].panes[0].paused).isFalse()
    }

    @Test
    fun `exit detaches the session`() {
        val exited = reduce(TmuxNotification.Exit(null))
        assertThat(exited.session("\$0")!!.attachState).isEqualTo(TmuxAttachState.DETACHED)
    }

    @Test
    fun `informational notifications leave state untouched`() {
        assertThat(reduce(TmuxNotification.Message("hi"))).isEqualTo(baseState)
        assertThat(reduce(TmuxNotification.Unknown("%future x"))).isEqualTo(baseState)
        assertThat(reduce(TmuxNotification.SessionsChanged)).isEqualTo(baseState)
    }
}
