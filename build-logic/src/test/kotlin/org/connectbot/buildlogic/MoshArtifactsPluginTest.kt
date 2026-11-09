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

import com.sun.net.httpserver.HttpServer
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MoshArtifactsPluginTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun downloadsRetriesAndReusesConfigurationCache() = withServer { server, requests ->
        val root = fixture(server.address.port)
        server.createContext("/downloads/") { exchange ->
            val count = requests.computeIfAbsent(exchange.requestURI.path) { AtomicInteger() }.incrementAndGet()
            if (exchange.requestURI.path.endsWith("arm64-v8a.zip") && count <= 2) {
                exchange.sendResponseHeaders(503, -1)
            } else {
                val abi = abiFromPath(exchange.requestURI.path)
                val bytes = archive(abi)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        val first = runner(root).build()
        assertTrue(first.output.contains("BUILD SUCCESSFUL"))
        assertEquals(3, requests.getValue("/downloads/test/mosh-android-arm64-v8a.zip").get())
        assertArtifacts(root)
        val second = runner(root).build()
        assertTrue(second.output.contains("Reusing configuration cache"))
        assertArtifacts(root)
    }

    @Test
    fun fallsBackOnceToReleaseMetadataAndUsesApiForAllAbis() = withServer { server, requests ->
        val root = fixture(server.address.port)
        server.createContext("/downloads/") { exchange ->
            requests.computeIfAbsent("direct") { AtomicInteger() }.incrementAndGet()
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.createContext("/releases/test") { exchange ->
            requests.computeIfAbsent("metadata") { AtomicInteger() }.incrementAndGet()
            assertEquals("application/vnd.github+json", exchange.requestHeaders.getFirst("Accept"))
            val assets = ABIS.joinToString(",") { abi ->
                """{"name":"mosh-android-$abi.zip","url":"http://127.0.0.1:${server.address.port}/assets/mosh-android-$abi.zip"}"""
            }
            val bytes = """{"assets":[$assets]}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.createContext("/assets/") { exchange ->
            requests.computeIfAbsent("asset") { AtomicInteger() }.incrementAndGet()
            assertEquals("application/octet-stream", exchange.requestHeaders.getFirst("Accept"))
            val bytes = archive(abiFromPath(exchange.requestURI.path))
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        runner(root).build()
        // A permanent 404 goes straight to the API; transient 503s are retried.
        assertEquals(1, requests.getValue("direct").get())
        assertEquals(1, requests.getValue("metadata").get())
        assertEquals(4, requests.getValue("asset").get())
        assertArtifacts(root)
    }

    @Test
    fun rejectsArchiveWithoutRequiredEntries() = withServer { server, _ ->
        val root = fixture(server.address.port)
        server.createContext("/downloads/") { exchange ->
            val bytes = zip(mapOf("unrelated" to byteArrayOf(1)))
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        val result = runner(root).buildAndFail()
        assertTrue(result.output.contains("Missing mosh-client or terminfo"))
        assertTrue(!root.resolve("build/artifacts/assets/mosh-release.txt").exists())
    }

    private fun runner(root: File) = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .withArguments("prepareFixture", "--configuration-cache", "--rerun-tasks", "--max-workers=1", "-Dorg.gradle.jvmargs=-Xmx512m")

    private fun fixture(port: Int): File {
        val root = temporary.newFolder()
        root.resolve("settings.gradle.kts").writeText("""rootProject.name = "mosh-fixture" """.trim())
        root.resolve("gradle").mkdirs()
        root.resolve("gradle/mosh4android.version").writeText("test\n")
        root.resolve("build.gradle.kts").writeText(
            """
            import org.connectbot.buildlogic.PrepareMoshArtifacts
            plugins { id("org.connectbot.mosh-artifacts") }
            tasks.register<PrepareMoshArtifacts>("prepareFixture") {
                releaseTag.set("test")
                releaseBaseUrl.set("http://127.0.0.1:$port/downloads")
                releaseApiBaseUrl.set("http://127.0.0.1:$port/releases")
                jniLibsDirectory.set(layout.buildDirectory.dir("artifacts/jniLibs"))
                assetsDirectory.set(layout.buildDirectory.dir("artifacts/assets"))
            }
            """.trimIndent(),
        )
        return root
    }

    private fun assertArtifacts(root: File) {
        for (abi in ABIS) {
            assertArrayEquals(abi.toByteArray(), root.resolve("build/artifacts/jniLibs/$abi/libmoshexec.so").readBytes())
        }
        assertEquals("terminfo", root.resolve("build/artifacts/assets/share/terminfo/x/xterm-256color").readText())
        assertEquals("test", root.resolve("build/artifacts/assets/mosh-release.txt").readText())
    }

    private fun abiFromPath(path: String) = path.substringAfter("mosh-android-").removeSuffix(".zip")

    private fun archive(abi: String) = zip(
        mapOf(
            "mosh-client" to abi.toByteArray(),
            "terminfo.zip" to zip(mapOf("share/terminfo/x/xterm-256color" to "terminfo".toByteArray())),
            "../outside" to "ignored".toByteArray(),
        ),
    )

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun withServer(test: (HttpServer, ConcurrentHashMap<String, AtomicInteger>) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            test(server, ConcurrentHashMap())
        } finally {
            server.stop(0)
        }
    }

    companion object {
        private val ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    }
}
