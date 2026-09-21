pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }
}

@file:Suppress("ktlint:standard:property-naming")
val TRANSLATIONS_ONLY: String? by settings

includeBuild("../cbssh") {
    dependencySubstitution {
        substitute(module("org.connectbot.sshlib:sshlib")).using(project(":sshlib"))
    }
}

if (TRANSLATIONS_ONLY.isNullOrBlank()) {
    include(":app")
}
include(":translations")
