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

package org.connectbot.transport.sftp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SftpPathsTest {

    @Test
    fun join_appendsSeparatorWhenMissing() {
        assertEquals("/home/user/file.txt", SftpPaths.join("/home/user", "file.txt"))
    }

    @Test
    fun join_doesNotDoubleSeparator() {
        assertEquals("/file.txt", SftpPaths.join("/", "file.txt"))
        assertEquals("/home/user/file.txt", SftpPaths.join("/home/user/", "file.txt"))
    }

    @Test
    fun parent_ofNestedPath_returnsDirectory() {
        assertEquals("/home", SftpPaths.parent("/home/user"))
        assertEquals("/home/user", SftpPaths.parent("/home/user/file.txt"))
    }

    @Test
    fun parent_ofTopLevelPath_returnsRoot() {
        assertEquals("/", SftpPaths.parent("/home"))
    }

    @Test
    fun parent_ofRoot_returnsNull() {
        assertNull(SftpPaths.parent("/"))
    }

    @Test
    fun parent_ofRelativePath_returnsNull() {
        assertNull(SftpPaths.parent("relative/path"))
        assertNull(SftpPaths.parent(""))
    }

    @Test
    fun parent_ignoresTrailingSeparator() {
        assertEquals("/home", SftpPaths.parent("/home/user/"))
    }

    @Test
    fun name_returnsLastComponent() {
        assertEquals("file.txt", SftpPaths.name("/home/user/file.txt"))
        assertEquals("user", SftpPaths.name("/home/user/"))
        assertEquals("/", SftpPaths.name("/"))
    }
}
