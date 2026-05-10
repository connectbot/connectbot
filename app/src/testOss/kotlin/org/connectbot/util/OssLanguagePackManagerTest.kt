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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OssLanguagePackManagerTest {

    private lateinit var manager: LanguagePackManager

    @Before
    fun setUp() {
        manager = LanguagePackManagerImpl(RuntimeEnvironment.getApplication())
    }

    @Test
    fun getInstalledLanguages_returnsNonEmptySet() {
        assertThat(manager.getInstalledLanguages()).isNotEmpty()
    }

    @Test
    fun requestLanguagePack_anyTag_callsOnResultTrue() {
        var result: Boolean? = null
        manager.requestLanguagePack("de") { result = it }
        assertThat(result).isTrue()
    }

    @Test
    fun requestLanguagePack_emptyTag_callsOnResultTrue() {
        var result: Boolean? = null
        manager.requestLanguagePack("") { result = it }
        assertThat(result).isTrue()
    }
}
