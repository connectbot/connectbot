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
}
include(":translations")

// Include local termlib as composite build for OSC8 hyperlink support
includeBuild("../termlib") {
    dependencySubstitution {
        substitute(module("org.connectbot:termlib")).using(project(":lib"))
    }
}
