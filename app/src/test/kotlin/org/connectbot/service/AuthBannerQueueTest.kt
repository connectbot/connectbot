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

package org.connectbot.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class AuthBannerQueueTest {
    @Test
    fun enqueue_withEmptyUrls_ignoresBanner() {
        val queue = AuthBannerQueue()

        queue.enqueue("host", "message", emptyList(), "en")

        assertThat(queue.authBanners.value).isEmpty()
    }

    @Test
    fun enqueue_withDuplicateSourceAndMessage_keepsFirstBanner() {
        val queue = AuthBannerQueue()

        queue.enqueue("host", "message", listOf("https://example.com/one"), "en")
        queue.enqueue("host", "message", listOf("https://example.com/two"), "en")

        assertThat(queue.authBanners.value).hasSize(1)
        assertThat(queue.authBanners.value.single().urls).containsExactly("https://example.com/one")
    }

    @Test
    fun enqueue_withMoreThanMaxBanners_keepsNewestBanners() {
        val queue = AuthBannerQueue()

        repeat(11) { index ->
            queue.enqueue("host-$index", "message-$index", listOf("https://example.com/$index"), null)
        }

        assertThat(queue.authBanners.value).hasSize(10)
        assertThat(queue.authBanners.value.first().sourceName).isEqualTo("host-1")
        assertThat(queue.authBanners.value.last().sourceName).isEqualTo("host-10")
    }

    @Test
    fun dismiss_removesBannerById() {
        val queue = AuthBannerQueue()
        queue.enqueue("host", "message", listOf("https://example.com"), null)
        val bannerId = queue.authBanners.value.single().id

        queue.dismiss(bannerId)

        assertThat(queue.authBanners.value).isEmpty()
    }

    @Test
    fun dismissFrom_removesOnlyMatchingSource() {
        val queue = AuthBannerQueue()
        queue.enqueue("target", "target message", listOf("https://target.example.com"), null)
        queue.enqueue("jump", "jump message", listOf("https://jump.example.com"), null)

        queue.dismissFrom("jump")

        assertThat(queue.authBanners.value).singleElement()
            .extracting(AuthBanner::sourceName)
            .isEqualTo("target")
    }
}
