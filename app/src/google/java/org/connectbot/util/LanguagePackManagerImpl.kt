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

import android.os.Handler
import android.os.Looper
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

class LanguagePackManagerImpl @Inject constructor(
    private val splitInstallManager: SplitInstallManager,
) : LanguagePackManager {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun getInstalledLanguages(): Set<String> = try {
        splitInstallManager.installedLanguages
    } catch (e: Exception) {
        Timber.w(e, "Failed to query installed languages")
        emptySet()
    }

    override fun requestLanguagePack(languageTag: String, onResult: (success: Boolean) -> Unit) {
        if (languageTag.isEmpty()) {
            onResult(true)
            return
        }

        val locale = Locale.forLanguageTag(languageTag)
        val request = SplitInstallRequest.newBuilder()
            .addLanguage(locale)
            .build()

        var targetSessionId = -1
        // Buffer any state update that arrives before addOnSuccessListener assigns targetSessionId.
        var pendingState: SplitInstallSessionState? = null

        val listener = object : SplitInstallStateUpdatedListener {
            override fun onStateUpdate(state: SplitInstallSessionState) {
                if (targetSessionId == -1) {
                    // Session ID not yet known; buffer and process after startInstall succeeds.
                    pendingState = state
                    return
                }
                if (state.sessionId() != targetSessionId) return
                handleState(state, languageTag, this, onResult)
            }
        }

        splitInstallManager.registerListener(listener)
        splitInstallManager.startInstall(request)
            .addOnSuccessListener { sessionId ->
                if (sessionId == 0) {
                    // Already installed — no state update will arrive.
                    Timber.d("SplitInstall already installed for %s", languageTag)
                    splitInstallManager.unregisterListener(listener)
                    mainHandler.post { onResult(true) }
                } else {
                    targetSessionId = sessionId
                    Timber.d("SplitInstall started for %s, sessionId=%d", languageTag, sessionId)
                    // Replay any buffered state update now that the session ID is known.
                    pendingState?.let { state ->
                        pendingState = null
                        if (state.sessionId() == targetSessionId) {
                            handleState(state, languageTag, listener, onResult)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Timber.w(e, "SplitInstall startInstall failed for %s", languageTag)
                splitInstallManager.unregisterListener(listener)
                mainHandler.post { onResult(false) }
            }
    }

    private fun handleState(
        state: SplitInstallSessionState,
        languageTag: String,
        listener: SplitInstallStateUpdatedListener,
        onResult: (Boolean) -> Unit,
    ) {
        Timber.d("SplitInstall state for %s: %d", languageTag, state.status())
        when (state.status()) {
            SplitInstallSessionStatus.INSTALLED -> {
                splitInstallManager.unregisterListener(listener)
                mainHandler.post { onResult(true) }
            }

            SplitInstallSessionStatus.FAILED,
            SplitInstallSessionStatus.CANCELED,
            SplitInstallSessionStatus.UNKNOWN,
            -> {
                Timber.w("SplitInstall failed/canceled/unknown for %s, errorCode=%d", languageTag, state.errorCode())
                splitInstallManager.unregisterListener(listener)
                mainHandler.post { onResult(false) }
            }

            SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                Timber.w("SplitInstall requires user confirmation for %s", languageTag)
                splitInstallManager.unregisterListener(listener)
                mainHandler.post { onResult(false) }
            }
        }
    }
}
