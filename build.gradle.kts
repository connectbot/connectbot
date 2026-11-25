// Top-level build file where you can add configuration options common to all sub-projects/modules.

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.diffplug.spotless.extra.wtp.EclipseWtpFormatterStep

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
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.dynamic.feature) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.spotless)
}

val compileSdkVersion = libs.versions.compileSdk.get().split('.', limit = 2)
val sdkMajor = compileSdkVersion[0].toInt()
val sdkMinor = compileSdkVersion.getOrNull(1)?.toInt()
val minimumSdk = libs.versions.minSdk.get().toInt()
val applicationTargetSdk = libs.versions.targetSdk.get().toInt()

subprojects {
    listOf("com.android.application", "com.android.library", "com.android.dynamic-feature").forEach { pluginId ->
        pluginManager.withPlugin(pluginId) {
            extensions.configure<CommonExtension> {
                compileSdk {
                    version = release(sdkMajor) {
                        minorApiLevel = sdkMinor
                    }
                }
                defaultConfig.minSdk = minimumSdk
                if (this is ApplicationExtension) {
                    defaultConfig.targetSdk = applicationTargetSdk
                }
            }
        }
    }

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
        licenseHeaderFile("spotless/license-header.txt")
    }

    kotlin {
        target("app/src/**/*.kt", "build-logic/src/**/*.kt", "ctap2/src/**/*.kt", "ctap2-android/src/**/*.kt")
        ktlint("1.8.0")
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:0.6.8"))
        licenseHeaderFile("spotless/license-header.txt")
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint("1.8.0")
    }

    format("xml") {
        target("**/*.xml")
        targetExclude("**/.idea/**/*.xml", "**/build/**/*.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }

    yaml {
        target(".github/**/*.yml", ".github/**/*.yaml")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("toml") {
        target("**/*.toml")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("misc") {
        target(listOf("**/*.md", "**/.gitignore", "**/.gitattributes", "**/.editorconfig"))
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}
