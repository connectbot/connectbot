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

import de.undercouch.gradle.tasks.download.DownloadAction
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.zip.ZipInputStream

abstract class PrepareMoshArtifacts : DefaultTask() {
    @get:Input abstract val releaseTag: Property<String>

    @get:OutputDirectory abstract val jniLibsDirectory: DirectoryProperty

    @get:OutputDirectory abstract val assetsDirectory: DirectoryProperty

    @get:Input abstract val releaseBaseUrl: Property<String>

    @get:Input abstract val releaseApiBaseUrl: Property<String>

    // Construct actions during configuration so they capture only cache-compatible services.
    private val directDownloads = ABIS.associateWith { newDownload() }
    private val fallbackDownloads = ABIS.associateWith { newDownload() }
    private val metadataDownload = newDownload()

    init {
        releaseBaseUrl.convention("https://github.com/connectbot/mosh4android/releases/download")
        releaseApiBaseUrl.convention("https://api.github.com/repos/connectbot/mosh4android/releases/tags")
    }

    private fun newDownload() = DownloadAction(project, this).apply {
        retries(3)
        connectTimeout(15_000)
        readTimeout(60_000)
        tempAndMove(true)
        header("User-Agent", "ConnectBot-Gradle")
    }

    @TaskAction
    fun prepare() {
        val tag = releaseTag.get()
        val jniLibsRoot = jniLibsDirectory.get().asFile
        val assetsRoot = assetsDirectory.get().asFile
        jniLibsRoot.deleteRecursively()
        assetsRoot.deleteRecursively()
        var releaseAssets: List<*>? = null
        ABIS.forEach { abi ->
            val archive = temporaryDir.resolve("$tag/mosh-android-$abi.zip")
            var lastFailure: IOException? = null
            if (releaseAssets == null) {
                try {
                    download(directDownloads.getValue(abi), "${releaseBaseUrl.get()}/$tag/mosh-android-$abi.zip", archive)
                } catch (e: IOException) {
                    lastFailure = e
                }
            }
            if (lastFailure != null || releaseAssets != null) {
                logger.lifecycle("Downloading Mosh archive for $abi through the GitHub release API")
                try {
                    val assets = releaseAssets ?: run {
                        val metadata = temporaryDir.resolve("$tag/release.json")
                        download(metadataDownload, "${releaseApiBaseUrl.get()}/$tag", metadata, "application/vnd.github+json")
                        val release = JsonSlurper().parse(metadata) as Map<*, *>
                        (release["assets"] as List<*>).also { releaseAssets = it }
                    }
                    val asset = assets.filterIsInstance<Map<*, *>>().first { it["name"] == "mosh-android-$abi.zip" }
                    download(fallbackDownloads.getValue(abi), asset["url"] as String, archive)
                } catch (e: Exception) {
                    lastFailure?.let { e.addSuppressed(it) }
                    throw e
                }
            }
            extractArchive(archive, abi, abi == ABIS.first(), jniLibsRoot, assetsRoot)
        }
        assetsRoot.resolve("mosh-release.txt").writeText(tag)
    }

    private fun download(action: DownloadAction, url: String, destination: File, accept: String = "application/octet-stream") {
        action.src(url)
        action.dest(destination)
        action.header("Accept", accept)
        try {
            // Suppress worker failure propagation so the direct URL can fall back to the API.
            action.execute(false).get()
        } catch (e: ExecutionException) {
            throw IOException("Failed to download $url", e.cause)
        }
    }

    private fun extractArchive(file: File, abi: String, extractTerminfo: Boolean, jniLibsRoot: File, assetsRoot: File) {
        var foundClient = false
        var foundTerminfo = false
        ZipInputStream(file.inputStream().buffered()).use { archive ->
            var entry = archive.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "mosh-client" -> {
                        val target = jniLibsRoot.resolve("$abi/libmoshexec.so")
                        target.parentFile.mkdirs()
                        target.outputStream().use { archive.copyTo(it) }
                        foundClient = true
                    }

                    "terminfo.zip" -> {
                        if (extractTerminfo) {
                            val nonClosing = object : java.io.FilterInputStream(archive) {
                                override fun close() {}
                            }
                            ZipInputStream(nonClosing).use { nested ->
                                var nestedEntry = nested.nextEntry
                                while (nestedEntry != null) {
                                    if (nestedEntry.name == "share/terminfo/x/xterm-256color") {
                                        val target = assetsRoot.resolve("share/terminfo/x/xterm-256color")
                                        target.parentFile.mkdirs()
                                        target.outputStream().use { nested.copyTo(it) }
                                        foundTerminfo = true
                                    }
                                    nested.closeEntry()
                                    nestedEntry = nested.nextEntry
                                }
                            }
                        } else {
                            foundTerminfo = true
                        }
                    }
                }
                archive.closeEntry()
                entry = archive.nextEntry
            }
        }
        check(foundClient && foundTerminfo) { "Missing mosh-client or terminfo in $abi release archive" }
    }
    companion object {
        private val ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    }
}
