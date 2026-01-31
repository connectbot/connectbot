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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import timber.log.Timber

private const val TAG = "BiometricPromptHelper"

/**
 * State holder for BiometricPrompt interactions.
 */
@Stable
class BiometricPromptState(
    private val activity: FragmentActivity,
    private val onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
    private val onError: (Int, CharSequence) -> Unit,
    private val onFailed: () -> Unit
) {
    private var biometricPrompt: BiometricPrompt? = null
    var isAuthenticating by mutableStateOf(false)
        private set

    private val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            Timber.d("Authentication succeeded")
            isAuthenticating = false
            onSuccess(result)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            Timber.e("Authentication error: $errorCode - $errString")
            isAuthenticating = false
            onError(errorCode, errString)
        }

        override fun onAuthenticationFailed() {
            Timber.w("Authentication failed")
            // Don't set isAuthenticating to false here - user can retry
            onFailed()
        }
    }

    init {
        val executor = ContextCompat.getMainExecutor(activity)
        biometricPrompt = BiometricPrompt(activity, executor, callback)
    }

    /**
     * Authenticate with biometrics using a CryptoObject.
     * Use this when you need to perform cryptographic operations after authentication.
     *
     * @param title The title shown in the biometric prompt
     * @param subtitle The subtitle shown in the biometric prompt
     * @param negativeButtonText The text for the cancel button
     * @param cryptoObject The CryptoObject for cryptographic operations
     */
    fun authenticate(
        title: String,
        subtitle: String,
        negativeButtonText: String,
        cryptoObject: BiometricPrompt.CryptoObject? = null
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        isAuthenticating = true
        if (cryptoObject != null) {
            biometricPrompt?.authenticate(promptInfo, cryptoObject)
        } else {
            biometricPrompt?.authenticate(promptInfo)
        }
    }

    /**
     * Cancel any ongoing authentication.
     */
    fun cancelAuthentication() {
        biometricPrompt?.cancelAuthentication()
        isAuthenticating = false
    }

    fun dispose() {
        biometricPrompt = null
    }
}

/**
 * Remember a BiometricPromptState for use in Compose.
 *
 * @param onSuccess Called when authentication succeeds. The AuthenticationResult contains
 *                  the CryptoObject (if provided) with an authenticated Signature.
 * @param onError Called when authentication fails with an error.
 *                The error code can be one of BiometricPrompt.ERROR_* constants.
 * @param onFailed Called when a biometric is recognized but not valid (wrong finger).
 *                 The prompt remains visible for retry.
 * @return BiometricPromptState, or null if FragmentActivity context is not available
 */
@Composable
fun rememberBiometricPromptState(
    onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
    onError: (Int, CharSequence) -> Unit,
    onFailed: () -> Unit = {}
): BiometricPromptState? {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()

    if (activity == null) {
        Timber.w("BiometricPromptState requires a FragmentActivity context, biometric auth unavailable")
        return null
    }

    // Use rememberUpdatedState to ensure the BiometricPromptState always uses the latest callbacks
    // even though it's remembered across recompositions.
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnFailed by rememberUpdatedState(onFailed)

    val state = remember(activity) {
        BiometricPromptState(
            activity = activity,
            onSuccess = { currentOnSuccess(it) },
            onError = { code, msg -> currentOnError(code, msg) },
            onFailed = { currentOnFailed() }
        )
    }

    DisposableEffect(state) {
        onDispose {
            state.dispose()
        }
    }

    return state
}

/**
 * Find the FragmentActivity from a Context by unwrapping ContextWrappers.
 * In Compose, LocalContext.current often returns a ContextThemeWrapper or similar,
 * not the actual Activity, so we need to unwrap it.
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

/**
 * Common error codes from BiometricPrompt.
 */
object BiometricErrorCodes {
    /** The user canceled the operation. */
    const val ERROR_USER_CANCELED = BiometricPrompt.ERROR_USER_CANCELED

    /** The user pressed the negative button. */
    const val ERROR_NEGATIVE_BUTTON = BiometricPrompt.ERROR_NEGATIVE_BUTTON

    /** Too many attempts. */
    const val ERROR_LOCKOUT = BiometricPrompt.ERROR_LOCKOUT

    /** Too many attempts. Requires device unlock. */
    const val ERROR_LOCKOUT_PERMANENT = BiometricPrompt.ERROR_LOCKOUT_PERMANENT

    /** The device does not have a biometric sensor. */
    const val ERROR_NO_BIOMETRICS = BiometricPrompt.ERROR_NO_BIOMETRICS

    /** The biometric hardware is unavailable. */
    const val ERROR_HW_UNAVAILABLE = BiometricPrompt.ERROR_HW_UNAVAILABLE

    /** A generic error occurred. */
    const val ERROR_VENDOR = BiometricPrompt.ERROR_VENDOR
}
