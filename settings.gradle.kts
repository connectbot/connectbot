pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        gradlePluginPortal()
    }
}

val translationsOnly = providers.gradleProperty("TRANSLATIONS_ONLY").orNull

if (translationsOnly.isNullOrBlank()) {
    include(":app")
    include(":ctap2")
    include(":ctap2-android")
    include(":mosh")
}
include(":translations")
