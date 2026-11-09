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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property

abstract class MoshArtifactsExtension {
    abstract val releaseTag: Property<String>
}

class MoshArtifactsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("de.undercouch.download")
        val extension = project.extensions.create("moshArtifacts", MoshArtifactsExtension::class.java)
        extension.releaseTag.convention(
            project.providers.fileContents(project.rootProject.layout.projectDirectory.file("gradle/mosh4android.version"))
                .asText.map { text -> text.lineSequence().first { it.isNotBlank() && !it.startsWith("#") }.trim() },
        )
        project.pluginManager.withPlugin("com.android.application") {
            registerArtifacts(project, extension, "Oss", "oss")
        }
        project.pluginManager.withPlugin("com.android.dynamic-feature") {
            registerArtifacts(project, extension, "Google", "google")
        }
    }

    private fun registerArtifacts(project: Project, extension: MoshArtifactsExtension, name: String, flavor: String) {
        project.tasks.register("prepare${name}MoshArtifacts", PrepareMoshArtifacts::class.java) {
            releaseTag.set(extension.releaseTag)
            jniLibsDirectory.set(project.layout.buildDirectory.dir("generated/mosh/$flavor/jniLibs"))
            assetsDirectory.set(project.layout.buildDirectory.dir("generated/mosh/$flavor/assets"))
        }
    }
}
