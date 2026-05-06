// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    extra.set("TRANSLATIONS_ONLY", System.getenv("TRANSLATIONS_ONLY")?.trim())
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.findLibrary("kotlin-gradle-plugin").get())
        classpath(libs.findLibrary("ksp-gradle-plugin").get())
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.spotless)
}

subprojects {
    buildscript {
        repositories {
            google()
            mavenCentral()
        }
    }
}

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

spotless {
    ratchetFrom = "origin/main"

    java {
        target("app/src/main/java/org/connectbot/**/*.java")
        importOrder()
        removeUnusedImports()
        googleJavaFormat()
    }

    kotlin {
        target("app/src/**/*.kt")
        ktlint("1.8.0")
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:0.5.8"))
    }

    groovyGradle {
        target("**/*.gradle")
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint()
    }

    format("misc") {
        target(listOf("**/*.md", "**/.gitignore"))

        trimTrailingWhitespace()
        endWithNewline()
    }
}
