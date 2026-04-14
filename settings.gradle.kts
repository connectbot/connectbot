@file:Suppress("ktlint:standard:property-naming")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }
}

// Substitute the published org.connectbot:termlib artifact with the locally
// checked-out termlib sibling at ../termlib while the bracket_paste feature
// branch is in flight on both repos.
includeBuild("../termlib") {
    dependencySubstitution {
        substitute(module("org.connectbot:termlib")).using(project(":lib"))
    }
}

val TRANSLATIONS_ONLY: String? by settings

if (TRANSLATIONS_ONLY.isNullOrBlank()) {
    include(":app")
}
include(":translations")
