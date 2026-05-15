@file:Suppress("ktlint:standard:property-naming")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }
}

val TRANSLATIONS_ONLY: String? by settings

if (TRANSLATIONS_ONLY.isNullOrBlank()) {
    include(":app")
}
include(":translations")
