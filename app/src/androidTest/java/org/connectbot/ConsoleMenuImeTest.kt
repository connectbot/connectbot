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

package org.connectbot

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.service.DisconnectReason
import org.connectbot.service.TerminalBridge
import org.connectbot.terminal.TerminalDimensions
import org.connectbot.ui.AppUiState
import org.connectbot.ui.LocalTerminalManager
import org.connectbot.ui.MainActivity
import org.connectbot.ui.screens.console.ConsoleScreen
import org.connectbot.ui.theme.ConnectBotTheme
import org.connectbot.util.PreferenceConstants
import org.connectbot.util.TestUriBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/** Exercises the actual focusable popup and software IME; requires an unlocked emulator with an IME. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ConsoleMenuImeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: TestRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        TestRule { base, _ -> base }
    }

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var activity: MainActivity

    @Before
    fun setUp() {
        hiltRule.inject()
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(PreferenceConstants.TITLEBARHIDE, false)
            putBoolean(PreferenceConstants.KEY_ALWAYS_VISIBLE, true)
        }
    }

    @Test
    fun menuDismissal_restoresVisibleKeyboard_withoutResizingTerminal() = withLocalConsole { bridge ->
        waitUntil("Keyboard did not open") { imeVisible() }
        settle()
        val original = bridge.terminalEmulator.dimensions
        val observed = mutableSetOf<TerminalDimensions>()
        val sampler = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                observed.add(bridge.terminalEmulator.dimensions)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback(sampler) }
        try {
            // Repeat to catch stale restoration state left over from the first dismissal.
            repeat(2) {
                openMenu()
                // Android may keep the IME up or temporarily hide it for the popup.
                // Either way the PTY dimensions must remain unchanged.
                settle()
                if (it == 0) {
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                } else {
                    tapOutsideMenu()
                }
                waitUntil("Dismissing the menu did not restore the keyboard") {
                    activity.hasWindowFocus() && imeVisible()
                }
                settle()
                composeRule.onNodeWithText(context.getString(R.string.console_menu_resize)).assertDoesNotExist()
            }
        } finally {
            instrumentation.runOnMainSync { Choreographer.getInstance().removeFrameCallback(sampler) }
        }
        assertEquals("Menu transitions must not resize the terminal", setOf(original), observed)

        // A genuine Back dismissal after the round trip must still work.
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitUntil("Back did not hide the keyboard") { !imeVisible() }
        waitUntil("Terminal resizing remained suspended after menu dismissal") {
            bridge.terminalEmulator.dimensions != original
        }
        settle()
        instrumentation.runOnMainSync { assertFalse(imeVisible()) }
    }

    @Test
    fun menuItemDismissal_restoresVisibleKeyboard() = withLocalConsole {
        waitUntil("Keyboard did not open") { imeVisible() }
        settle()
        openMenu()
        settle()
        composeRule.onNodeWithText(context.getString(R.string.console_menu_compose_mode)).performClick()
        waitUntil("Selecting a menu item did not restore the keyboard") {
            activity.hasWindowFocus() && imeVisible()
        }
        settle()
        composeRule.onNodeWithText(context.getString(R.string.console_menu_resize)).assertDoesNotExist()
    }

    @Test
    fun menuDismissal_keepsUserDismissedKeyboardHidden() = withLocalConsole {
        waitUntil("Keyboard did not open") { imeVisible() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitUntil("Back did not hide the keyboard") { !imeVisible() }
        settle()
        openMenu()
        settle()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitUntil("Menu did not release window focus") { activity.hasWindowFocus() }
        settle()
        composeRule.onNodeWithText(context.getString(R.string.console_menu_resize)).assertDoesNotExist()
        instrumentation.runOnMainSync { assertFalse("Menu must not reopen a dismissed keyboard", imeVisible()) }
    }

    private fun openMenu() {
        composeRule.onNodeWithContentDescription(context.getString(R.string.button_more_options)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.console_menu_resize)).assertIsDisplayed()
    }

    private fun tapOutsideMenu() {
        var y = 0f
        instrumentation.runOnMainSync { y = activity.window.decorView.height / 2f }
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, 10f, y, 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
    }

    private fun imeVisible(): Boolean = ViewCompat.getRootWindowInsets(activity.window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun waitUntil(message: String, condition: () -> Boolean) {
        // Advance Compose's clock as well as waiting for real Android events.
        // Otherwise effects that open the session or restore its layout never run.
        composeRule.waitUntil(conditionDescription = message, timeoutMillis = 10_000) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            ready
        }
    }

    private fun settle() {
        // Real popup/IME animations are independent of Compose's test clock.
        SystemClock.sleep(500)
        composeRule.waitForIdle()
    }

    private fun withLocalConsole(test: (TerminalBridge) -> Unit) {
        val intent = Intent(Intent.ACTION_VIEW, TestUriBuilder.local("MenuImeTest"), context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity = it }
            var bridge: TerminalBridge? = null
            waitUntil("Local session did not open") {
                val state = activity.appViewModel.uiState.value as? AppUiState.Ready
                bridge = state?.terminalManager?.bridgesFlow?.value?.firstOrNull { it.host.nickname == "MenuImeTest" }
                bridge?.isSessionOpen == true
            }
            val localBridge = requireNotNull(bridge)
            try {
                // Test ConsoleScreen directly, independently of startup deep-link navigation.
                instrumentation.runOnMainSync {
                    val manager = (activity.appViewModel.uiState.value as AppUiState.Ready).terminalManager
                    activity.setContent {
                        // This suite covers the software-keyboard menu contract. Hardware/Compose
                        // restoration policy belongs to termlib, not a ConnectBot menu workaround.
                        val configuration = Configuration(LocalConfiguration.current).apply {
                            keyboard = Configuration.KEYBOARD_NOKEYS
                            hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES
                        }
                        CompositionLocalProvider(
                            LocalTerminalManager provides manager,
                            LocalConfiguration provides configuration,
                        ) {
                            ConnectBotTheme {
                                ConsoleScreen(onNavigateBack = {}, onNavigateToPortForwards = {})
                            }
                        }
                    }
                }
                waitUntil("Console did not gain window focus") { activity.hasWindowFocus() }
                val showKeyboard = composeRule.onAllNodesWithContentDescription(
                    context.getString(R.string.image_description_show_keyboard),
                )
                val hideKeyboard = composeRule.onAllNodesWithContentDescription(
                    context.getString(R.string.image_description_hide_keyboard),
                )
                composeRule.waitUntil(conditionDescription = "Console keyboard controls", timeoutMillis = 10_000) {
                    showKeyboard.fetchSemanticsNodes().isNotEmpty() || hideKeyboard.fetchSemanticsNodes().isNotEmpty()
                }
                settle()
                waitUntil("Software keyboard did not open automatically") { imeVisible() }
                test(localBridge)
            } finally {
                localBridge.dispatchDisconnect(DisconnectReason.USER_REQUESTED)
            }
        }
    }
}
