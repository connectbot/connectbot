/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2017 Kenny Root
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
import org.conscrypt.OpenSSLProvider
import java.security.Security

/**
 * Loads the Conscrypt provider for the oss (Open Source Software) version of ConnectBot that
 * uses OpenSSL. This provider doesn't rely on Google Play Services.
 */
object ProviderLoader {
    @JvmStatic
    fun load(context: Context, listener: ProviderLoaderListener) {
        try {
            Security.insertProviderAt(OpenSSLProvider(), 1)
            listener.onProviderLoaderSuccess()
        } catch (e: Exception) {
            listener.onProviderLoaderError()
        }
    }
}
