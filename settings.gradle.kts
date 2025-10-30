pluginManagement {
    val MAVEN_REPO_CACHE: String? by settings
    if (!MAVEN_REPO_CACHE.isNullOrBlank()) {
        repositories {
            maven {
                url = uri(MAVEN_REPO_CACHE!!)
            }
        }
    } else {
        repositories {
            google()
            gradlePluginPortal()
        }
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}

val MAVEN_REPO_CACHE: String? by settings
val GRADLE_BUILD_CACHE: String? by settings
val TRANSLATIONS_ONLY: String? by settings

buildCache {
    local {
        isEnabled = GRADLE_BUILD_CACHE.isNullOrBlank()
    }
    if (!GRADLE_BUILD_CACHE.isNullOrBlank()) {
        remote<org.gradle.caching.http.HttpBuildCache> {
            url = uri(GRADLE_BUILD_CACHE!!)
            isPush = true
        }
    }
}

if (TRANSLATIONS_ONLY.isNullOrBlank()) {
    include(":app")
}
include(":translations")
