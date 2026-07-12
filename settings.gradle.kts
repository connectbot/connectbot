pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }
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
