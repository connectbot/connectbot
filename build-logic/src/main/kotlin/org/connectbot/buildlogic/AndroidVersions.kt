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

package org.connectbot.buildlogic

// Stable patch slots occupy the lower half of each minor version's range.
// The upper half is reserved for the following minor version's alpha builds.
object AndroidVersions {
    fun code(tag: String, commits: Int): Int {
        val match = Regex("""v([0-9]+)\.([0-9]+)\.([0-9]+)(-alpha)?""").matchEntire(tag)
        requireNotNull(match) { "Unsupported version anchor: $tag" }
        val (majorText, minorText, patchText, prerelease) = match.destructured
        val major = majorText.toInt()
        val minor = minorText.toInt()
        val patch = patchText.toInt()
        require(major in 0..209 && minor in 0..99 && patch in 0..49) {
            "Version components exceed allocated ranges: $tag (patch must be <= 49)"
        }
        val alpha = prerelease.isNotEmpty()
        if (alpha) {
            require(patch == 0 && minor > 0) { "Alpha anchors require a new minor version: $tag" }
        }
        val capacity = if (alpha) 50_000 else 1_000
        require(commits in 0 until capacity) {
            "Version range exhausted for $tag: $commits commits (limit $capacity); create the next stable release"
        }
        val base = major * 10_000_000L + minor * 100_000L + patch * 1_000L
        val code = base - (if (alpha) 50_000 else 0) + commits
        require(code in 1..2_100_000_000L) { "Android version code out of range: $code" }
        return code.toInt()
    }
}
