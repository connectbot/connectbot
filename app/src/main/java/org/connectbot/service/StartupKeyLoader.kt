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

package org.connectbot.service

import org.connectbot.data.entity.Pubkey
import java.security.KeyPair

/**
 * Outcome of attempting to load a key marked as "unlock at startup" without user interaction.
 */
sealed class StartupKeyLoadOutcome {
    data class Loaded(val pubkey: Pubkey, val pair: KeyPair) : StartupKeyLoadOutcome()
    data class NeedsPassphrase(val pubkey: Pubkey) : StartupKeyLoadOutcome()
    data class Failed(val pubkey: Pubkey, val reason: String) : StartupKeyLoadOutcome()
}

/**
 * Classify a startup key into whether it can be loaded silently, needs a passphrase prompt,
 * or has failed to load for other reasons. Extracted from TerminalManager so it can be unit
 * tested without the Android service lifecycle.
 *
 * Encrypted keys are never attempted with a null password; the caller is expected to surface
 * a prompt via the returned [StartupKeyLoadOutcome.NeedsPassphrase] outcome.
 */
fun classifyStartupKey(
    pubkey: Pubkey,
    convert: (Pubkey, String?) -> KeyPair?
): StartupKeyLoadOutcome {
    if (pubkey.encrypted) {
        return StartupKeyLoadOutcome.NeedsPassphrase(pubkey)
    }
    return try {
        val pair = convert(pubkey, null)
        if (pair == null) {
            StartupKeyLoadOutcome.Failed(pubkey, "Failed to convert key to KeyPair")
        } else {
            StartupKeyLoadOutcome.Loaded(pubkey, pair)
        }
    } catch (e: Exception) {
        StartupKeyLoadOutcome.Failed(pubkey, e.message ?: "Unknown error loading key")
    }
}
