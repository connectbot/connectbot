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
package org.connectbot.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import org.connectbot.util.LanguagePackManager
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [LanguagePackModule::class],
)
object TestLanguagePackModule {

    @Provides
    @Singleton
    fun provideLanguagePackManager(): LanguagePackManager = FakeLanguagePackManager()
}

class FakeLanguagePackManager : LanguagePackManager {
    val fakeInstalledLanguages: MutableSet<String> = mutableSetOf()
    var nextRequestResult: Boolean = true
    var requestedLanguage: String? = null

    /** When true, the callback is held until [completePendingRequest] is called. */
    var deferCallback: Boolean = false

    /** When true, a successful result does NOT add the tag to [fakeInstalledLanguages] (simulates bundled/session-0 language). */
    var skipAddOnSuccess: Boolean = false
    private var pendingCallback: (() -> Unit)? = null

    override fun getInstalledLanguages(): Set<String> = fakeInstalledLanguages.toSet()

    override fun requestLanguagePack(languageTag: String, onResult: (Boolean) -> Unit) {
        requestedLanguage = languageTag
        if (deferCallback) {
            pendingCallback = {
                if (nextRequestResult && !skipAddOnSuccess) fakeInstalledLanguages.add(languageTag)
                onResult(nextRequestResult)
            }
        } else {
            if (nextRequestResult && !skipAddOnSuccess) fakeInstalledLanguages.add(languageTag)
            onResult(nextRequestResult)
        }
    }

    fun completePendingRequest() {
        pendingCallback?.invoke()
        pendingCallback = null
    }
}
