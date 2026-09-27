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

package org.connectbot.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.FileDescriptor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MoshPtyWindowTest {
    @Test
    fun resizeDuringForkIsAppliedWhenDescriptorIsAttached() {
        val applied = mutableListOf<MoshPtyWindow.Size>()
        val window = MoshPtyWindow { _, size -> applied.add(size) }
        val forkSize = window.snapshot()

        window.update(100, 40, 1000, 800)
        window.attach(FileDescriptor())

        assertEquals(MoshPtyWindow.Size(), forkSize)
        assertEquals(listOf(MoshPtyWindow.Size(100, 40, 1000, 800)), applied)
    }

    @Test
    fun resizeAfterAttachCannotBeOverwrittenByStartupReplay() {
        val replayStarted = CountDownLatch(1)
        val finishReplay = CountDownLatch(1)
        val applied = mutableListOf<MoshPtyWindow.Size>()
        val window = MoshPtyWindow { _, size ->
            if (applied.isEmpty()) {
                replayStarted.countDown()
                check(finishReplay.await(5, TimeUnit.SECONDS))
            }
            applied.add(size)
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val attach = executor.submit { window.attach(FileDescriptor()) }
            check(replayStarted.await(5, TimeUnit.SECONDS))
            val update = executor.submit { window.update(90, 30, 900, 600) }
            finishReplay.countDown()
            attach.get(5, TimeUnit.SECONDS)
            update.get(5, TimeUnit.SECONDS)

            assertEquals(listOf(MoshPtyWindow.Size(), MoshPtyWindow.Size(90, 30, 900, 600)), applied)
        } finally {
            finishReplay.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun detachedDescriptorIsNotResizedAndReplacementReceivesLatestSize() {
        val descriptors = mutableListOf<FileDescriptor>()
        val applied = mutableListOf<MoshPtyWindow.Size>()
        val window = MoshPtyWindow { fd, size ->
            descriptors.add(fd)
            applied.add(size)
        }
        val first = FileDescriptor()
        val replacement = FileDescriptor()
        window.attach(first)
        window.detach()
        window.update(120, 50, 1200, 1000)
        window.attach(replacement)

        assertEquals(2, descriptors.size)
        assertSame(first, descriptors[0])
        assertSame(replacement, descriptors[1])
        assertEquals(MoshPtyWindow.Size(120, 50, 1200, 1000), applied.last())
    }

    @Test
    fun invalidCharacterSizeDoesNotReplaceLatestValidSize() {
        val applied = mutableListOf<MoshPtyWindow.Size>()
        val window = MoshPtyWindow { _, size -> applied.add(size) }
        window.update(90, 30, 900, 600)
        window.attach(FileDescriptor())
        window.update(0, 30, 0, 600)
        window.update(90, 0, 900, 0)

        assertEquals(listOf(MoshPtyWindow.Size(90, 30, 900, 600)), applied)
        assertEquals(MoshPtyWindow.Size(90, 30, 900, 600), window.snapshot())
    }
}
