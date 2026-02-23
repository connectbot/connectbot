@file:Suppress("ktlint:standard:property-naming")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }
}

includeBuild("../sshlib") {
    dependencySubstitution {
        substitute(module("org.connectbot:sshlib")).using(project(":"))
    }
}

val TRANSLATIONS_ONLY: String? by settings

if (TRANSLATIONS_ONLY.isNullOrBlank()) {
    include(":app")
}
include(":translations")
