pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

@file:Suppress("ktlint:standard:property-naming")
val TRANSLATIONS_ONLY: String? by settings

if (TRANSLATIONS_ONLY.isNullOrBlank()) {
    include(":app")
    includeBuild("/Users/haroldmartin/Downloads/termlib") {
        dependencySubstitution {
            substitute(module("org.connectbot:termlib")).using(project(":lib"))
        }
    }
}
include(":translations")
