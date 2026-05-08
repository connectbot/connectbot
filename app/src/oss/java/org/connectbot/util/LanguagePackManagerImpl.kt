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
import dagger.hilt.android.qualifiers.ApplicationContext
import org.connectbot.R
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import javax.inject.Inject

class LanguagePackManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LanguagePackManager {

    override fun getInstalledLanguages(): Set<String> {
        val languages = mutableSetOf<String>()
        try {
            val parser = context.resources.getXml(R.xml._generated_res_locale_config)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "locale") {
                    val tag = parser.getAttributeValue(
                        "http://schemas.android.com/apk/res/android",
                        "name",
                    )
                    if (!tag.isNullOrBlank()) languages.add(tag)
                }
                event = parser.next()
            }
            parser.close()
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse locale config")
        }
        return languages
    }

    override fun requestLanguagePack(languageTag: String, onResult: (success: Boolean) -> Unit) {
        onResult(true)
    }
}
