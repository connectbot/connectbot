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

package org.connectbot.passkey

import android.content.Context
import kotlinx.coroutines.withContext
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import javax.inject.Inject

/** Passive until the user explicitly starts an import. */
class PasskeyImportSource @Inject constructor(private val dispatchers: CoroutineDispatchers) {
    suspend fun discover(context: Context): Pubkey {
        val origin = withContext(dispatchers.io) { PasskeySupport.origin(context) }
        return PasskeyDiscovery.discover(origin, dispatchers.default) { options ->
            PasskeySupport.get(context, options)
        }
    }
}
