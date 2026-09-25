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

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.util.zip.ZipInputStream

abstract class PrepareGoogleMoshArtifacts : DefaultTask() {
    @get:Input abstract val releaseTag: Property<String>

    @get:OutputDirectory abstract val jniLibsDirectory: DirectoryProperty

    @get:OutputDirectory abstract val assetsDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val tag = releaseTag.get()
        val jniLibsRoot = jniLibsDirectory.get().asFile
        val assetsRoot = assetsDirectory.get().asFile
        jniLibsRoot.deleteRecursively()
        assetsRoot.deleteRecursively()
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        abis.forEach { abi ->
            val url = URI("https://github.com/connectbot/mosh4android/releases/download/$tag/mosh-android-$abi.zip").toURL()
            val connection = url.openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            var foundClient = false
            var foundTerminfo = false
            ZipInputStream(connection.getInputStream().buffered()).use { archive ->
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
                            if (abi == abis.first()) {
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
        assetsRoot.resolve("mosh-release.txt").writeText(tag)
    }
}

plugins {
    alias(libs.plugins.android.dynamic.feature)
}

val moshReleaseTag = rootProject.file("gradle/mosh4android.version")
    .readLines().first { it.isNotBlank() && !it.startsWith("#") }.trim()

val generatedGoogleMosh = layout.buildDirectory.dir("generated/mosh/google")
val prepareGoogleMoshArtifacts = tasks.register<PrepareGoogleMoshArtifacts>("prepareGoogleMoshArtifacts") {
    releaseTag.set(moshReleaseTag)
    jniLibsDirectory.set(generatedGoogleMosh.map { it.dir("jniLibs") })
    assetsDirectory.set(generatedGoogleMosh.map { it.dir("assets") })
}

android {
    namespace = "org.connectbot.mosh"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    flavorDimensions += listOf("license")
    productFlavors {
        create("oss") {
            dimension = "license"
        }
        create("google") {
            dimension = "license"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

androidComponents {
    onVariants(selector().withFlavor("license" to "google")) { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(prepareGoogleMoshArtifacts) { it.jniLibsDirectory }
        variant.sources.assets?.addGeneratedSourceDirectory(prepareGoogleMoshArtifacts) { it.assetsDirectory }
        variant.packaging.jniLibs.useLegacyPackaging.set(true)
        variant.packaging.jniLibs.useLegacyPackagingFromBundle.set(true)
    }
}

dependencies {
    implementation(project(":app"))
}
