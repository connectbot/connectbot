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

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.connectbot.di.CoroutineDispatchers
import timber.log.Timber

/** One provider installation per application process, shared by host editing and connections. */
object CryptoProviderState {
    private var installation: CompletableDeferred<Unit>? = null

    suspend fun ensureLoaded(context: Context, dispatchers: CoroutineDispatchers) {
        val (completion, start) = synchronized(this) {
            installation?.let { it to false } ?: CompletableDeferred<Unit>().also { installation = it }.let { it to true }
        }
        if (start) {
            try {
                withContext(NonCancellable + dispatchers.main) {
                    ProviderLoader.load(
                        context.applicationContext,
                        object : ProviderLoaderListener {
                            override fun onProviderLoaderSuccess() {
                                completion.complete(Unit)
                            }
                            override fun onProviderLoaderError() {
                                Timber.w("Crypto provider installation failed; using installed providers")
                                completion.complete(Unit)
                            }
                        },
                    )
                }
            } catch (e: Exception) {
                completion.complete(Unit)
                currentCoroutineContext().ensureActive()
                Timber.w(e, "Crypto provider installation failed; using installed providers")
            }
        }
        completion.await()
    }
}
