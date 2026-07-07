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

class TmuxControlParserTest {
    private val parser = TmuxControlParser()

    /** Runs a whole transcript (ISO-8859-1 lines) through one parser. */
    private fun parseAll(lines: List<String>): List<TmuxParseEvent> =
        lines.map { parser.feed(it) }.filterNot { it == TmuxParseEvent.Consumed }

    private fun fixtureLines(name: String): List<String> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/tmux/$name")) {
            "missing fixture /tmux/$name"
        }
        return stream.bufferedReader(Charsets.ISO_8859_1).readLines()
    }

    private fun notifications(events: List<TmuxParseEvent>): List<TmuxNotification> =
        events.filterIsInstance<TmuxParseEvent.Notification>().map { it.value }

    private fun replies(events: List<TmuxParseEvent>): List<TmuxReply> =
        events.filterIsInstance<TmuxParseEvent.Reply>().map { it.value }

    @Test
    fun `parses recorded session transcript`() {
        val events = parseAll(fixtureLines("transcript-37-session.txt"))
        val replies = replies(events)
        val notifications = notifications(events)

        // Sentinel + list-windows + list-panes + 5 commands (one failing)
        assertThat(replies).hasSize(8)
        assertThat(replies[0].lines).isEmpty() // unsolicited attach reply

        val listWindows = replies[1]
        assertThat(listWindows.ok).isTrue()
        assertThat(listWindows.lines).containsExactly("@0:0:main:1:*")

        val listPanes = replies[2]
        assertThat(listPanes.lines).containsExactly(
            "@0:%0:0:50:30:1",
            "@0:%1:1:49:30:0",
        )

        val error = replies.single { !it.ok }
        assertThat(error.lines).containsExactly("parse error: unknown command: this-is-not-a-command")

        assertThat(notifications.first()).isEqualTo(TmuxNotification.SessionChanged("\$0", "test"))
        assertThat(notifications).contains(
            TmuxNotification.WindowAdd("@1"),
            TmuxNotification.WindowRenamed("@1", "renamed"),
            TmuxNotification.UnlinkedWindowClose("@1"),
            TmuxNotification.Exit(null),
        )

        val output = notifications.filterIsInstance<TmuxNotification.Output>().single()
        assertThat(output.paneId).isEqualTo("%0")
        assertThat(output.bytes.toString(Charsets.UTF_8))
            .isEqualTo("hello \u001b[31mred\u001b[0m café 🚀\u0007\r\n")
    }

    @Test
    fun `parses recorded events transcript`() {
        val events = parseAll(fixtureLines("transcript-37-events.txt"))
        val notifications = notifications(events)

        val layoutChanges = notifications.filterIsInstance<TmuxNotification.LayoutChange>()
        assertThat(layoutChanges).hasSize(2)
        assertThat(layoutChanges[0].windowId).isEqualTo("@0")
        assertThat(layoutChanges[0].layout).isEqualTo("c08a,100x30,0,0[100x15,0,0,0,100x14,0,16,1]")
        assertThat(layoutChanges[0].visibleLayout).isEqualTo(layoutChanges[0].layout)
        assertThat(layoutChanges[0].flags).isEqualTo("*")

        assertThat(notifications).contains(
            TmuxNotification.SessionRenamed("\$0", "maison"),
            TmuxNotification.UnlinkedWindowAdd("@1"),
            TmuxNotification.SessionsChanged,
            TmuxNotification.UnlinkedWindowRenamed("@1", "tmux"),
        )
        assertThat(notifications.last()).isEqualTo(TmuxNotification.Exit(null))
    }

    @Test
    fun `unescapes octal control bytes`() {
        val bytes = TmuxControlParser.unescapeControlBytes("a\\134b\\011c\\015\\012")
        assertThat(bytes).isEqualTo("a\\b\tc\r\n".toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `passes raw utf8 through unescaping`() {
        // "café 🚀" as ISO-8859-1 chars (how the reader delivers raw bytes)
        val raw = String("café 🚀".toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
        val bytes = TmuxControlParser.unescapeControlBytes(raw)
        assertThat(bytes.toString(Charsets.UTF_8)).isEqualTo("café 🚀")
    }

    @Test
    fun `leaves malformed escapes untouched`() {
        assertThat(TmuxControlParser.unescapeControlBytes("end\\"))
            .isEqualTo("end\\".toByteArray())
        assertThat(TmuxControlParser.unescapeControlBytes("a\\9bc"))
            .isEqualTo("a\\9bc".toByteArray())
        assertThat(TmuxControlParser.unescapeControlBytes("a\\01"))
            .isEqualTo("a\\01".toByteArray())
    }

    @Test
    fun `tolerates carriage returns and CC dcs guard`() {
        assertThat(parser.feed("\u001bP1000p%begin 100 2 0\r")).isEqualTo(TmuxParseEvent.Consumed)
        assertThat(parser.feed("body\r")).isEqualTo(TmuxParseEvent.Consumed)
        val reply = parser.feed("%end 100 2 0\r")
        assertThat(reply).isEqualTo(
            TmuxParseEvent.Reply(TmuxReply(number = 2, ok = true, lines = listOf("body"))),
        )
        assertThat(parser.feed("\u001b\\")).isEqualTo(TmuxParseEvent.Consumed)
    }

    @Test
    fun `preserves empty lines inside reply bodies`() {
        parser.feed("%begin 1 5 1")
        parser.feed("first")
        parser.feed("")
        parser.feed("third")
        val reply = (parser.feed("%end 1 5 1") as TmuxParseEvent.Reply).value
        assertThat(reply.lines).containsExactly("first", "", "third")
    }

    @Test
    fun `parses extended output`() {
        val event = parser.feed("%extended-output %3 250 : late\\015\\012")
        val output = (event as TmuxParseEvent.Notification).value as TmuxNotification.Output
        assertThat(output.paneId).isEqualTo("%3")
        assertThat(output.bytes.toString(Charsets.UTF_8)).isEqualTo("late\r\n")
    }

    @Test
    fun `parses flow control and client notifications`() {
        assertThat(notificationOf("%pause %2")).isEqualTo(TmuxNotification.Pause("%2"))
        assertThat(notificationOf("%continue %2")).isEqualTo(TmuxNotification.Continue("%2"))
        assertThat(notificationOf("%client-detached /dev/pts/1"))
            .isEqualTo(TmuxNotification.ClientDetached("/dev/pts/1"))
        assertThat(notificationOf("%client-session-changed /dev/pts/1 \$2 work"))
            .isEqualTo(TmuxNotification.ClientSessionChanged("/dev/pts/1", "\$2", "work"))
        assertThat(notificationOf("%window-pane-changed @1 %4"))
            .isEqualTo(TmuxNotification.WindowPaneChanged("@1", "%4"))
        assertThat(notificationOf("%session-window-changed \$1 @3"))
            .isEqualTo(TmuxNotification.SessionWindowChanged("\$1", "@3"))
        assertThat(notificationOf("%pane-mode-changed %7"))
            .isEqualTo(TmuxNotification.PaneModeChanged("%7"))
        assertThat(notificationOf("%message hello there"))
            .isEqualTo(TmuxNotification.Message("hello there"))
        assertThat(notificationOf("%config-error .tmux.conf:3: unknown"))
            .isEqualTo(TmuxNotification.ConfigError(".tmux.conf:3: unknown"))
        assertThat(notificationOf("%exit detached"))
            .isEqualTo(TmuxNotification.Exit("detached"))
    }

    @Test
    fun `session renamed without id is handled`() {
        assertThat(notificationOf("%session-renamed newname"))
            .isEqualTo(TmuxNotification.SessionRenamed(null, "newname"))
        assertThat(notificationOf("%session-renamed two words"))
            .isEqualTo(TmuxNotification.SessionRenamed(null, "two words"))
        assertThat(notificationOf("%session-renamed \$3 two words"))
            .isEqualTo(TmuxNotification.SessionRenamed("\$3", "two words"))
    }

    @Test
    fun `layout change with minimal arity is handled`() {
        assertThat(notificationOf("%layout-change @2 abcd,80x24,0,0,5"))
            .isEqualTo(TmuxNotification.LayoutChange("@2", "abcd,80x24,0,0,5", null, null))
    }

    @Test
    fun `unknown notifications are preserved not crashed on`() {
        assertThat(notificationOf("%future-thing a b c"))
            .isEqualTo(TmuxNotification.Unknown("%future-thing a b c"))
        assertThat(notificationOf("%output missing-pane-prefix data"))
            .isEqualTo(TmuxNotification.Unknown("%output missing-pane-prefix data"))
    }

    private fun notificationOf(line: String): TmuxNotification =
        (parser.feed(line) as TmuxParseEvent.Notification).value
}
