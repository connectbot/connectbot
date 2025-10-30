import com.diffplug.gradle.spotless.SpotlessPlugin

// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    extra.set("MAVEN_REPO_CACHE", System.getenv("MAVEN_REPO_CACHE")?.trim())
    extra.set("TRANSLATIONS_ONLY", System.getenv("TRANSLATIONS_ONLY")?.trim())
    if (extra.has("MAVEN_REPO_CACHE") && extra.get("MAVEN_REPO_CACHE") != null) {
        repositories {
            maven { url = uri(extra.get("MAVEN_REPO_CACHE")!!) }
        }
    } else {
        repositories {
            google()
            mavenCentral()
        }
    }
}

plugins {
    alias(libs.plugins.spotless)
}

if (extra.has("MAVEN_REPO_CACHE") && extra.get("MAVEN_REPO_CACHE") != null) {
    allprojects {
        buildscript {
            repositories {
                maven { url = uri(extra.get("MAVEN_REPO_CACHE")!!) }
            }
        }
        repositories {
            maven { url = uri(extra.get("MAVEN_REPO_CACHE")!!) }
        }
    }
} else {
    allprojects {
        buildscript {
            repositories {
                google()
                mavenCentral()
            }
        }
        repositories {
            google()
            mavenCentral()
        }
    }
}

if (!extra.has("TRANSLATIONS_ONLY") || extra.get("TRANSLATIONS_ONLY") == null) {
    apply(from = "spotless.gradle")
}

tasks.register("resolveDependencies") {
    description = "Resolves all projects dependencies from the repository."
    group = "Build Server"

    doLast {
        rootProject.allprojects {
            project.buildscript.configurations.forEach { configuration ->
                if (configuration.isCanBeResolved) {
                    configuration.resolve()
                }
            }

            project.configurations.forEach { configuration ->
                if (configuration.isCanBeResolved) {
                    configuration.resolve()
                }
            }
        }
    }
}
