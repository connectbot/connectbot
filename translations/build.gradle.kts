import com.pswidersk.gradle.python.uv.UvTask

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
    installDir.set(layout.buildDirectory.dir("uv"))
}

val appDir = "../app/"

tasks.register<UvTask>("translateStrings") {
    group = "Translations"
    description = "Translate missing Android strings using the local Ollama LLM."

    workingDir = projectDir
    args =
        listOf(
            "run",
            "python",
            file("translate.py").toString(),
            file(appDir + "src/main/res").toString(),
        )
}
