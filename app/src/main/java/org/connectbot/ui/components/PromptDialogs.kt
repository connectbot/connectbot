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

package org.connectbot.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.connectbot.R
import org.connectbot.service.PromptRequest
import org.connectbot.service.PromptResponse
import timber.log.Timber

/**
 * Handler for biometric authentication prompts.
 * Triggers BiometricPrompt when the prompt request is active.
 */
@Composable
fun BiometricPromptHandler(
    prompt: PromptRequest.BiometricPrompt,
    onResponse: (PromptResponse.BiometricResponse) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val currentOnResponse by rememberUpdatedState(onResponse)

    LaunchedEffect(prompt) {
        if (activity == null) {
            Timber.e("Cannot show BiometricPrompt: FragmentActivity not found")
            currentOnResponse(PromptResponse.BiometricResponse(false))
            return@LaunchedEffect
        }

        val executor = ContextCompat.getMainExecutor(context)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Timber.d("Biometric authentication succeeded")
                currentOnResponse(PromptResponse.BiometricResponse(true))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Timber.e("Biometric authentication error: $errorCode - $errString")
                currentOnResponse(PromptResponse.BiometricResponse(false))
            }

            override fun onAuthenticationFailed() {
                Timber.w("Biometric authentication failed (not recognized)")
                // Don't respond yet - let the user retry or cancel
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.pubkey_biometric_prompt_title))
            .setSubtitle(context.getString(R.string.pubkey_biometric_prompt_subtitle, prompt.keyNickname))
            .setNegativeButtonText(context.getString(R.string.delete_neg))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

/**
 * Find the FragmentActivity from the context hierarchy.
 */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is FragmentActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
