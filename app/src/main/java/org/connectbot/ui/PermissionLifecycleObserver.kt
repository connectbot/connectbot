/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

package org.connectbot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.connectbot.util.NotificationPermissionHelper

/**
 * Observes lifecycle events and re-checks notification permission status when the screen resumes.
 * This handles cases where the user grants or revokes permission in Settings and returns to the app.
 *
 * @param onPermissionChanged Callback invoked with the current permission state when screen resumes
 */
@Composable
fun ObservePermissionOnResume(onPermissionChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Use rememberUpdatedState to ensure the effect always uses the latest callback
    // without restarting the effect when the callback changes.
    val currentOnPermissionChanged by rememberUpdatedState(onPermissionChanged)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Check current permission status - may have changed while user was away
                val isGranted = NotificationPermissionHelper.isNotificationPermissionGranted(context)
                currentOnPermissionChanged(isGranted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
