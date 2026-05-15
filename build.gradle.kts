// Top-level build file where you can add configuration options common to all sub-projects/modules.

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
        licenseHeaderFile("spotless/license-header.txt")
    }

    kotlin {
        target("app/src/**/*.kt")
        ktlint("1.8.0")
            .editorConfigOverride(
                mapOf(
                    "ij_kotlin_imports_layout" to "*,java.**,javax.**,kotlin.**,^",
                    "ktlint_code_style" to "android_studio",
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                    "ktlint_standard_property-naming" to "disabled",
                    "ktlint_standard_backing-property-naming" to "disabled",
                    "ktlint_standard_filename" to "disabled",
                    "ktlint_standard_discouraged-comment-location" to "disabled",
                    "ktlint_standard_max-line-length" to "disabled",
                    "ktlint_standard_kdoc" to "disabled",
                    "ktlint_compose_compositionlocal-allowlist" to "disabled",
                ),
            ).customRuleSets(listOf("io.nlopez.compose.rules:ktlint:0.5.8"))
        licenseHeaderFile("spotless/license-header.txt")
    }

    groovyGradle {
        target("**/*.gradle")
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
