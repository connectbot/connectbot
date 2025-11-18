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
import android.content.Intent
import com.google.android.gms.security.ProviderInstaller
import com.google.android.gms.security.ProviderInstaller.ProviderInstallListener

/**
 * Loads the Google Play Services provider that upgrades Conscrypt on the device.
 */
object ProviderLoader {
    @JvmStatic
    fun load(context: Context, listener: ProviderLoaderListener) {
        ProviderInstaller.installIfNeededAsync(context, ProviderInstallListenerWrapper(listener))
    }

    private class ProviderInstallListenerWrapper(private val mListener: ProviderLoaderListener) :
        ProviderInstallListener {
        override fun onProviderInstalled() {
            mListener.onProviderLoaderSuccess()
        }

        override fun onProviderInstallFailed(i: Int, intent: Intent?) {
            mListener.onProviderLoaderError()
        }
    }
}
