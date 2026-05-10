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

import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class GoogleLanguagePackManagerTest {

    private fun makeSessionState(status: Int, sessionId: Int = 42): SplitInstallSessionState = mock {
        on { this.status() } doReturn status
        on { this.sessionId() } doReturn sessionId
    }

    @Test
    fun getInstalledLanguages_delegatesToSplitInstallManager() {
        val splitInstallManager = mock<SplitInstallManager> {
            on { installedLanguages } doReturn setOf("de", "fr")
        }
        val manager = LanguagePackManagerImpl(splitInstallManager)
        assertThat(manager.getInstalledLanguages()).containsExactlyInAnyOrder("de", "fr")
    }

    @Test
    fun requestLanguagePack_emptyTag_callsOnResultTrueImmediately() {
        val splitInstallManager = mock<SplitInstallManager>()
        val manager = LanguagePackManagerImpl(splitInstallManager)

        var result: Boolean? = null
        manager.requestLanguagePack("") { result = it }

        assertThat(result).isTrue()
        verify(splitInstallManager, never()).startInstall(any())
    }

    @Test
    fun requestLanguagePack_installed_callsOnResultTrue() {
        val listenerCaptor = argumentCaptor<SplitInstallStateUpdatedListener>()
        val splitInstallManager = mock<SplitInstallManager> {
            on { startInstall(any<SplitInstallRequest>()) } doReturn Tasks.forResult(42)
        }
        val manager = LanguagePackManagerImpl(splitInstallManager)

        var result: Boolean? = null
        manager.requestLanguagePack("de") { result = it }

        verify(splitInstallManager).registerListener(listenerCaptor.capture())
        // Idle the looper so Tasks.forResult fires addOnSuccessListener and sets targetSessionId = 42
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        listenerCaptor.firstValue.onStateUpdate(makeSessionState(SplitInstallSessionStatus.INSTALLED))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(result).isTrue()
    }

    @Test
    fun requestLanguagePack_failed_callsOnResultFalse() {
        val listenerCaptor = argumentCaptor<SplitInstallStateUpdatedListener>()
        val sessionState = mock<SplitInstallSessionState> {
            on { status() } doReturn SplitInstallSessionStatus.FAILED
            on { sessionId() } doReturn 42
            on { errorCode() } doReturn 0
        }
        val splitInstallManager = mock<SplitInstallManager> {
            on { startInstall(any<SplitInstallRequest>()) } doReturn Tasks.forResult(42)
        }
        val manager = LanguagePackManagerImpl(splitInstallManager)

        var result: Boolean? = null
        manager.requestLanguagePack("fr") { result = it }

        verify(splitInstallManager).registerListener(listenerCaptor.capture())
        // Idle the looper so Tasks.forResult fires addOnSuccessListener and sets targetSessionId = 42
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        listenerCaptor.firstValue.onStateUpdate(sessionState)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(result).isFalse()
    }

    @Test
    fun requestLanguagePack_unrelatedSession_isIgnored() {
        val listenerCaptor = argumentCaptor<SplitInstallStateUpdatedListener>()
        val splitInstallManager = mock<SplitInstallManager> {
            on { startInstall(any<SplitInstallRequest>()) } doReturn Tasks.forResult(42)
        }
        val manager = LanguagePackManagerImpl(splitInstallManager)

        var result: Boolean? = null
        manager.requestLanguagePack("de") { result = it }

        verify(splitInstallManager).registerListener(listenerCaptor.capture())
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        // Send INSTALLED for a different session — should be ignored
        listenerCaptor.firstValue.onStateUpdate(makeSessionState(SplitInstallSessionStatus.INSTALLED, sessionId = 99))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(result).isNull()
    }

    @Test
    fun requestLanguagePack_sessionIdZero_alreadyInstalled_callsOnResultTrue() {
        val splitInstallManager = mock<SplitInstallManager> {
            on { startInstall(any<SplitInstallRequest>()) } doReturn Tasks.forResult(0)
        }
        val manager = LanguagePackManagerImpl(splitInstallManager)

        var result: Boolean? = null
        manager.requestLanguagePack("de") { result = it }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(result).isTrue()
        verify(splitInstallManager).unregisterListener(any())
    }

    @Test
    fun requestLanguagePack_requiresUserConfirmation_callsOnResultFalse() {
        val listenerCaptor = argumentCaptor<SplitInstallStateUpdatedListener>()
        val splitInstallManager = mock<SplitInstallManager> {
            on { startInstall(any<SplitInstallRequest>()) } doReturn Tasks.forResult(42)
        }
        val manager = LanguagePackManagerImpl(splitInstallManager)

        var result: Boolean? = null
        manager.requestLanguagePack("de") { result = it }

        verify(splitInstallManager).registerListener(listenerCaptor.capture())
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        listenerCaptor.firstValue.onStateUpdate(makeSessionState(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(result).isFalse()
        verify(splitInstallManager).unregisterListener(any())
    }

    @Test
    fun requestLanguagePack_stateArrivesBeforeSessionId_isBufferedAndProcessed() {
        val listenerCaptor = argumentCaptor<SplitInstallStateUpdatedListener>()
        // Use a deferred task so addOnSuccessListener hasn't fired when onStateUpdate is called
        val taskSource = com.google.android.gms.tasks.TaskCompletionSource<Int>()
        val splitInstallManager = mock<SplitInstallManager> {
            on { startInstall(any<SplitInstallRequest>()) } doReturn taskSource.task
        }
        val manager = LanguagePackManagerImpl(splitInstallManager)

        var result: Boolean? = null
        manager.requestLanguagePack("de") { result = it }

        verify(splitInstallManager).registerListener(listenerCaptor.capture())
        // Deliver state update before session ID is known (addOnSuccessListener not yet called)
        listenerCaptor.firstValue.onStateUpdate(makeSessionState(SplitInstallSessionStatus.INSTALLED, sessionId = 42))
        assertThat(result).isNull() // buffered, not yet processed

        // Now resolve the task — addOnSuccessListener fires, replays the buffered state
        taskSource.setResult(42)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(result).isTrue()
    }

    @Test
    fun requestLanguagePack_startInstallFails_callsOnResultFalse() {
        val splitInstallManager = mock<SplitInstallManager> {
            on { startInstall(any<SplitInstallRequest>()) } doReturn Tasks.forException(RuntimeException("network"))
        }
        val manager = LanguagePackManagerImpl(splitInstallManager)

        var result: Boolean? = null
        manager.requestLanguagePack("ja") { result = it }

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(result).isFalse()
    }
}
