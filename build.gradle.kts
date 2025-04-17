// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
	repositories {
		google()
		mavenCentral()
	}
}

plugins {
	alias(libs.plugins.android.application) apply false
	id("com.diffplug.spotless") version "6.23.3" apply false
}

val translationsOnly: String = System.getenv("TRANSLATIONS_ONLY")?.trim() ?: ""

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

	if (translationsOnly.isEmpty()) {
		apply {
			plugin("com.diffplug.spotless")
		}
	}
}

if (translationsOnly.isEmpty()) {
	apply {
		plugin("com.diffplug.spotless")
	}

	configure<com.diffplug.gradle.spotless.SpotlessExtension>  {
		groovyGradle {
			target("**/*.gradle")
		}

		format("misc") {
			target("**/*.md", "**/.gitignore")

			trimTrailingWhitespace()
			endWithNewline()
		}
	}
}
