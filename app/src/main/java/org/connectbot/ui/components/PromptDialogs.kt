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
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.connectbot.R
import org.connectbot.service.PromptRequest
import org.connectbot.service.PromptResponse

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

    LaunchedEffect(prompt) {
        if (activity == null) {
            Log.e("PromptDialogs", "Cannot show BiometricPrompt: FragmentActivity not found")
            onResponse(PromptResponse.BiometricResponse(false))
            return@LaunchedEffect
        }

        val executor = ContextCompat.getMainExecutor(context)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Log.d("PromptDialogs", "Biometric authentication succeeded")
                onResponse(PromptResponse.BiometricResponse(true))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Log.e("PromptDialogs", "Biometric authentication error: $errorCode - $errString")
                onResponse(PromptResponse.BiometricResponse(false))
            }

            override fun onAuthenticationFailed() {
                Log.w("PromptDialogs", "Biometric authentication failed (not recognized)")
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

