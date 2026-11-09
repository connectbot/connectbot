@file:Suppress("ktlint:standard:property-naming")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        gradlePluginPortal()
    }
}

val TRANSLATIONS_ONLY: String? by settings

if (TRANSLATIONS_ONLY.isNullOrBlank()) {
    include(":app")
    include(":ctap2")
    include(":ctap2-android")
    include(":mosh")
}
include(":translations")
