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

package org.connectbot.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class UrlUtilsTest {

    @Test
    fun normalizeUrl_EmptyInput_ReturnsEmptyString() {
        assertEquals("", UrlUtils.normalizeUrl(""))
        assertEquals("", UrlUtils.normalizeUrl("   "))
    }

    @Test
    fun normalizeUrl_WithHttpScheme_StaysSame() {
        assertEquals("http://google.com", UrlUtils.normalizeUrl("http://google.com"))
    }

    @Test
    fun normalizeUrl_WithHttpsScheme_StaysSame() {
        assertEquals("https://google.com", UrlUtils.normalizeUrl("https://google.com"))
    }

    @Test
    fun normalizeUrl_WithMailtoScheme_StaysSame() {
        assertEquals("mailto:test@example.com", UrlUtils.normalizeUrl("mailto:test@example.com"))
    }

    @Test
    fun normalizeUrl_WithTelScheme_StaysSame() {
        assertEquals("tel:123456789", UrlUtils.normalizeUrl("tel:123456789"))
    }

    @Test
    fun normalizeUrl_WithJavascript_StaysSame() {
        assertEquals("javascript:alert(1)", UrlUtils.normalizeUrl("javascript:alert(1)"))
    }

    @Test
    fun normalizeUrl_WithoutScheme_AddsHttps() {
        assertEquals("https://www.google.com", UrlUtils.normalizeUrl("www.google.com"))
        assertEquals("https://google.com/search?q=test", UrlUtils.normalizeUrl("google.com/search?q=test"))
        assertEquals("https://google.com:8080", UrlUtils.normalizeUrl("google.com:8080"))
        assertEquals("https://example.com/path://foo", UrlUtils.normalizeUrl("example.com/path://foo"))
        assertEquals("https://localhost:3000", UrlUtils.normalizeUrl("localhost:3000"))
        assertEquals("https://myhost:8080/api/v1", UrlUtils.normalizeUrl("myhost:8080/api/v1"))
    }

    @Test
    fun openUrl_StartsActivityWithCorrectIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val url = "www.google.com"

        val result = UrlUtils.openUrl(context, url)

        assertTrue(result.isSuccess)

        val shadowContext = shadowOf(context as android.app.Application)
        val nextStartedActivity = shadowContext.nextStartedActivity

        assertEquals(Intent.ACTION_VIEW, nextStartedActivity.action)
        assertEquals("https://www.google.com", nextStartedActivity.dataString)
        assertTrue(nextStartedActivity.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun openUrl_WithFullUrl_StartsActivityWithCorrectIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val url = "http://example.com"

        UrlUtils.openUrl(context, url)

        val shadowContext = shadowOf(context as android.app.Application)
        val nextStartedActivity = shadowContext.nextStartedActivity

        assertEquals(Intent.ACTION_VIEW, nextStartedActivity.action)
        assertEquals("http://example.com", nextStartedActivity.dataString)
    }

    @Test
    fun openUrl_ReturnsFailure_WhenActivityNotFound() {
        val context = mock(Context::class.java)
        val url = "http://example.com"

        `when`(context.startActivity(any())).thenThrow(ActivityNotFoundException())

        val result = UrlUtils.openUrl(context, url)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ActivityNotFoundException)
    }

    @Test
    fun openUrl_ReturnsFailure_WhenGenericExceptionOccurs() {
        val context = mock(Context::class.java)
        val url = "http://example.com"

        `when`(context.startActivity(any())).thenThrow(RuntimeException("Test exception"))

        val result = UrlUtils.openUrl(context, url)

        assertTrue(result.isFailure)
        assertEquals("Test exception", result.exceptionOrNull()?.message)
    }

    @Test
    fun openUrl_ReturnsSuccess_ForEmptyUrl() {
        val context = mock(Context::class.java)
        val result = UrlUtils.openUrl(context, "")
        assertTrue(result.isSuccess)
    }

    @Test
    fun openUrl_ReturnsFailure_ForUnsupportedSchemes() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // intent:// scheme
        val intentResult = UrlUtils.openUrl(context, "intent://example.com#Intent;scheme=http;package=com.android.browser;end")
        assertTrue("Expected failure for intent://", intentResult.isFailure)
        val intentExc = intentResult.exceptionOrNull()
        assertTrue("Expected IllegalArgumentException but got $intentExc", intentExc is IllegalArgumentException)
        assertEquals("Unsupported scheme: intent", intentExc?.message)

        // file:// scheme
        val fileResult = UrlUtils.openUrl(context, "file:///etc/passwd")
        assertTrue(fileResult.isFailure)
        assertTrue(fileResult.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Unsupported scheme: file", fileResult.exceptionOrNull()?.message)

        // javascript: scheme
        val jsResult = UrlUtils.openUrl(context, "javascript:alert(1)")
        assertTrue(jsResult.isFailure)
        assertTrue(jsResult.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Unsupported scheme: javascript", jsResult.exceptionOrNull()?.message)
    }
}
