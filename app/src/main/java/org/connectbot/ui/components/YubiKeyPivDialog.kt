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

package org.connectbot.ui.components

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.yubico.yubikit.core.application.InvalidPinException
import com.yubico.yubikit.piv.Slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.piv.YubiKeyPiv
import org.connectbot.service.PromptRequest

@Composable
internal fun <T> YubiKeyPivDialog(
    onDismiss: () -> Unit,
    onComplete: (Slot, T) -> Unit,
    operation: suspend (Activity, Slot, CharArray) -> T,
    signingPrompt: PromptRequest.YubiKeyPivPrompt? = null,
) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var slotText by remember { mutableStateOf(Slot.AUTHENTICATION.stringAlias.uppercase()) }
    var pin by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    var nfcOff by remember { mutableStateOf(nfcAdapter?.isEnabled == false) }
    if (nfcAdapter != null) {
        DisposableEffect(nfcAdapter) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    nfcOff = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF) != NfcAdapter.STATE_ON
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
                // Sent by the NFC service, not by the system uid, so a not-exported receiver would not get it.
                ContextCompat.RECEIVER_EXPORTED,
            )
            onDispose { context.unregisterReceiver(receiver) }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { nfcOff = nfcAdapter?.isEnabled == false }

    val slot = signingPrompt?.slot ?: runCatching { Slot.fromStringAlias(slotText) }
        .getOrNull()?.takeUnless { it == Slot.ATTESTATION }
    val dismiss: () -> Unit = {
        pin = ""
        job?.cancel()
        onDismiss()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        pin = ""
        job?.cancel()
    }

    fun submit(activity: Activity, slot: Slot, input: CharArray, automatic: Boolean = false) {
        error = null
        scope.launch(start = CoroutineStart.LAZY) {
            try {
                onComplete(slot, operation(activity, slot, input))
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidPinException) {
                error = if (e.attemptsRemaining == 0) {
                    resources.getString(R.string.pubkey_piv_pin_blocked)
                } else {
                    resources.getString(R.string.pubkey_piv_pin_wrong, e.attemptsRemaining)
                }
            } catch (e: YubiKeyPiv.PinRequired) {
                // The card forgot the PIN after all: just let the user type it.
                if (!automatic) error = resources.getString(R.string.pubkey_piv_pin_required)
            } catch (e: Exception) {
                error = resources.getString(
                    when (e) {
                        is YubiKeyPiv.DeviceTimeout -> R.string.pubkey_piv_timeout
                        is YubiKeyPiv.UnsupportedKey -> R.string.pubkey_piv_unsupported_key
                        is YubiKeyPiv.WrongKey -> R.string.pubkey_piv_wrong_key
                        else -> R.string.pubkey_piv_failed
                    },
                )
            } finally {
                synchronized(input) { input.fill('\u0000') }
                job = null
            }
        }.also {
            job = it
            it.start()
        }
    }

    // The card may still remember the PIN from the previous login: sign right away, only touch is left.
    LaunchedEffect(signingPrompt, activity) {
        if (signingPrompt != null && activity != null && job == null && YubiKeyPiv.canSignWithoutPin(signingPrompt.slot)) {
            submit(activity, signingPrompt.slot, CharArray(0), automatic = true)
        }
    }

    AlertDialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = {
            Text(
                if (signingPrompt == null) {
                    stringResource(R.string.pubkey_piv_add)
                } else {
                    stringResource(R.string.pubkey_piv_sign, signingPrompt.keyNickname)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (signingPrompt == null) {
                    OutlinedTextField(
                        value = slotText,
                        onValueChange = { value ->
                            if (value.length <= 2 && value.all { it in "0123456789abcdefABCDEF" }) {
                                slotText = value.uppercase()
                            }
                        },
                        label = { Text(stringResource(R.string.pubkey_piv_slot)) },
                        supportingText = { Text(stringResource(R.string.pubkey_piv_slot_help)) },
                        isError = slotText.length == 2 && slot == null,
                        enabled = job == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(stringResource(R.string.pubkey_piv_destination, signingPrompt.destination))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 8) pin = it },
                        label = { Text(stringResource(R.string.pubkey_piv_pin)) },
                        enabled = job == null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (job != null) {
                    Text(stringResource(R.string.pubkey_piv_connect))
                }
                if (activity == null) {
                    Text(stringResource(R.string.pubkey_piv_unavailable))
                }
                if (nfcOff) {
                    Text(stringResource(R.string.pubkey_piv_nfc_off))
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                        Text(stringResource(R.string.pubkey_piv_nfc_settings))
                    }
                }
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = activity != null && slot != null && job == null,
                onClick = {
                    if (activity == null || slot == null) return@TextButton
                    val encoded = pin.encodeToByteArray()
                    val validPin = encoded.isEmpty() || (encoded.size in 6..8 && encoded.none { it == 0.toByte() })
                    encoded.fill(0)
                    if (!validPin) {
                        error = resources.getString(R.string.pubkey_piv_pin_length)
                        return@TextButton
                    }
                    val input = pin.toCharArray()
                    pin = ""
                    submit(activity, slot, input)
                },
            ) {
                Text(stringResource(if (signingPrompt == null) R.string.pubkey_piv_read else R.string.pubkey_piv_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
