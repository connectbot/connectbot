// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    extra.set("TRANSLATIONS_ONLY", System.getenv("TRANSLATIONS_ONLY")?.trim())
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.spotless)
}

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
        mavenLocal()
    }
}

if (!extra.has("TRANSLATIONS_ONLY") || extra.get("TRANSLATIONS_ONLY") == null) {
    apply(from = "spotless.gradle")
}
