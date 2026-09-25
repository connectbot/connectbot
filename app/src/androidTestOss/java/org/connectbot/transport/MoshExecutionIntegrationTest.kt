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

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.util.InstallMosh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mosh.MoshClient
import java.io.File
import java.io.FileDescriptor

/**
 * On-device integration test verifying that the bundled mosh-client binary
 * can be installed, executed, and managed through JNI without SELinux denials,
 * missing execute permissions, or dynamic linker restrictions.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MoshExecutionIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun installClient_extractsTerminfoAndLocatesExecutable() {
        val result = InstallMosh.installClient(context)
        assertTrue("Mosh installation should succeed: ${result.errorMessage}", result.success)

        val terminfoPath = InstallMosh.getTerminfoPath()
        assertNotNull("Terminfo path should not be null", terminfoPath)
        val terminfoFile = File(terminfoPath!!, "x/xterm-256color")
        assertTrue("Terminfo xterm-256color file should exist at ${terminfoFile.absolutePath}", terminfoFile.isFile)

        val clientPath = InstallMosh.getMoshClientPath(context)
        assertNotNull("Mosh client path should not be null", clientPath)
        val clientFile = File(clientPath!!)
        assertTrue("Mosh client file should exist at $clientPath", clientFile.isFile)
        assertTrue("Mosh client should have execute permission", clientFile.canExecute())
    }

    @Test
    fun moshClientBinary_executesDirectlyWithoutSelinuxDenial() {
        val result = InstallMosh.installClient(context)
        assertTrue("Mosh installation should succeed: ${result.errorMessage}", result.success)

        val clientPath = InstallMosh.getMoshClientPath(context)
        assertNotNull("Mosh client path should not be null", clientPath)

        // Verify that direct execve via ProcessBuilder is not blocked by SELinux, noexec, or linker
        val process = ProcessBuilder(clientPath, "--version")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        assertEquals("mosh-client --version should exit with code 0. Output: $output", 0, exitCode)
        assertTrue("Output should identify as mosh: $output", output.contains("mosh", ignoreCase = true))
    }

    @Test
    fun moshClientForkExec_createsPtyAndStartsSubprocess() {
        val result = InstallMosh.installClient(context)
        assertTrue("Mosh installation should succeed: ${result.errorMessage}", result.success)

        val clientPath = InstallMosh.getMoshClientPath(context)
        assertNotNull("Mosh client path should not be null", clientPath)

        val terminfoPath = InstallMosh.getTerminfoPath()
        assertNotNull("Terminfo path should not be null", terminfoPath)

        val pidArray = LongArray(1)
        val ptyFd: FileDescriptor? = MoshClient.forkExec(
            clientPath!!,
            "127.0.0.1",
            "60000",
            "AAAAAAAAAAAAAAAAAAAAAA",
            terminfoPath!!,
            "en_US.UTF-8",
            24,
            80,
            0,
            0,
            pidArray,
        )

        assertNotNull("PTY master FileDescriptor must not be null", ptyFd)
        assertTrue("PTY master descriptor must be valid", ptyFd!!.valid())
        val childPid = pidArray[0]
        assertTrue("Child PID must be positive (got $childPid)", childPid > 0)

        try {
            // Confirm the forked subprocess is running
            assertTrue("Child process with PID $childPid should be alive", File("/proc/$childPid").exists())

            // Confirm PTY window resizing via JNI works
            MoshClient.setPtyWindowSize(ptyFd, 40, 100, 0, 0)
        } finally {
            // Clean up child process and file descriptor
            MoshClient.kill(childPid, 9)
            try {
                ParcelFileDescriptor.dup(ptyFd).close()
            } catch (ignored: Exception) {
            }
        }
    }
}
