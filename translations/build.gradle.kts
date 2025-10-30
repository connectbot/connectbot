import com.pswidersk.gradle.python.uv.UvxTask
import org.gradle.api.tasks.bundling.Tar

plugins {
    alias(libs.plugins.python.uv)
}

/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2015 Kenny Root, Jeffrey Sharkey
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

pythonUvPlugin {
    installDir.set(buildDir.resolve("uv"))
}

val appDir = "../app/"
val localeDir = file(appDir + "locale")
val launchpadImportFile = "launchpad-import.tar.gz"
val launchpadExportFile = rootProject.file("launchpad-export.tar.gz")

val android2poArgs = listOf(
    "--groups",
    "strings",
    "--android",
    file(appDir + "src/main/res").toString(),
    "--gettext",
    localeDir.toString(),
    "--template",
    "fortune/fortune.pot",
    "--layout",
    "fortune/%(locale)s.po"
)

val a2poArgs = listOf(
    "--python", "3.9",
    "--from", "git+https://github.com/miracle2k/android2po.git#egg=android2po",
    "a2po",
)

val a2poTask = tasks.register<UvxTask>("a2po") {
    args = a2poArgs
}

tasks.register<Copy>("untarTranslations") {
    from(tarTree(resources.gzip(launchpadExportFile)))
    into(localeDir)

    // Unsupported Android locales
    exclude("**/oc.po", "**/ca@valencia.po")
}

tasks.register<UvxTask>("importToAndroid") {
    group = "Translations"
    description = "Import translations into ConnectBot from app/locale directory."

    args = listOf(a2poArgs, listOf("import"), android2poArgs).flatten()
}

tasks.register("translationsImport") {
    group = "Translations"
    description = "Import translations from a Launchpad export tarball."

    dependsOn("untarTranslations", "importToAndroid")
}

tasks.register<UvxTask>("exportFromAndroid") {
    group = "Translations"
    description = "Export translations from ConnectBot into the app/locale directory."

    args = listOf(a2poArgs, listOf("export"), android2poArgs).flatten()
}

tasks.register<Tar>("translationsExport") {
    group = "Translations"
    description = "Export translations from ConnectBot in a format suitable to import into Launchpad"

    dependsOn("exportFromAndroid")
    destinationDirectory.set(file("$rootDir"))
    archiveFileName.set(launchpadImportFile)
    from(localeDir)
    compression = org.gradle.api.tasks.bundling.Compression.GZIP
    doLast {
        println()
        println("Import file into Launchpad:")
        println(archiveFile.get().asFile)
        println()
    }
}
