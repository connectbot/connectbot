import io.github.reactivecircus.appversioning.toSemVer
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.ben.manes.versions)
	alias(libs.plugins.ltgt.errorprone)
	alias(libs.plugins.reactivecircus.appversioning)
	alias(libs.plugins.kt3k.coveralls)
	alias(libs.plugins.mxalbert.jacocoandroid)
	alias(libs.plugins.starter.easylauncher)
}

val testRunnerVersion = "1.5.0"
val espressoVersion = "3.5.1"

apply(plugin = "com.diffplug.spotless")

//apply from: "../config/quality.gradle"

coveralls.jacocoReportPath = "build/reports/coverage/google/debug/report.xml"

appVersioning {
	tagFilter = "v*"

	overrideVersionCode { gitTag, _, _ ->
		val semVer = gitTag.toSemVer()
		semVer.major * 10000000 + semVer.minor * 100000 + semVer.patch * 1000 + gitTag.commitsSinceLatestTag
	}

	overrideVersionName { gitTag, _, _ ->
		if (gitTag.commitsSinceLatestTag == 0) {
			gitTag.rawTagName
		} else {
			"${gitTag.rawTagName}-${gitTag.commitsSinceLatestTag}-g${gitTag.commitHash.substring(0, 7)}"
		}
	}
}

android {
	namespace = "org.connectbot"
	compileSdk = 33

	defaultConfig {
		applicationId = "org.connectbot"

		minSdkVersion(14)
		targetSdkVersion(33)

		vectorDrawables.useSupportLibrary = true

		ndk {
			abiFilters += arrayOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
		}

		testApplicationId = "org.connectbot.tests"
		testInstrumentationRunner = "org.connectbot.ConnectbotJUnitRunner"

		// The following argument makes the Android Test Orchestrator run its
		// "pm clear" command after each test invocation. This command ensures
		// that the app"s state is completely cleared between tests.
		testInstrumentationRunnerArguments += mutableMapOf(Pair("clearPackageData", "true"))
	}

	buildFeatures {
		buildConfig = true
	}

	signingConfigs {
		if (project.hasProperty("keystorePassword")) {
			create("release") {
				storeFile = file(property("keystoreFile") as String)
				storePassword = property("keystorePassword") as String
				keyAlias = property("keystoreAlias") as String
				keyPassword = property("keystorePassword") as String
			}
		}
	}
	buildTypes {
		release {
			isShrinkResources = true
			isMinifyEnabled = true

			proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard.cfg")
			testProguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard.cfg", "proguard-tests.cfg")

			if (project.hasProperty("keystorePassword")) {
				//noinspection GroovyAssignabilityCheck
				signingConfig = signingConfigs["release"]
			}
		}

		debug {
			// This is necessary to avoid using multiDex
			isMinifyEnabled = true

			proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard.cfg", "proguard-debug.cfg")
			testProguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard.cfg", "proguard-tests.cfg")

			applicationIdSuffix = ".debug"
			isTestCoverageEnabled = true
		}
	}

	flavorDimensions("license")

	productFlavors {
		// This product flavor uses the Conscrypt library which is open
		// source and licensed under Apache 2.
		create("oss") {
			dimension = "license"
			versionNameSuffix = "-oss"
		}

		// This product flavor uses the Google Play Services library for
		// ProviderInstaller. It uses Conscrypt under-the-hood, but the
		// Google Play Services SDK itself is not open source.
		create("google") {
			dimension = "license"
			versionNameSuffix = ""
		}
	}

	testOptions {
		// temporarily disable the orchestrator as this breaks coverage: https://issuetracker.google.com/issues/72758547
		//execution "ANDROID_TEST_ORCHESTRATOR"
		animationsDisabled = true

		unitTests {
			isIncludeAndroidResources = true
			all {
				jacoco {
					isIncludeAndroidResources = true
				}
			}
		}
	}

	lintOptions {
		isAbortOnError = false
		lintConfig = file("lint.xml")
	}

	packagingOptions {
		exclude("META-INF/LICENSE.txt")
		exclude("LICENSE.txt")
		exclude("**/*.gwt.xml")
	}

	externalNativeBuild { cmake { path = file("CMakeLists.txt") } }
}

spotless {
	java {
		target("**/*.java")
		removeUnusedImports()
	}
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        check("InvalidInlineTag", CheckSeverity.OFF)
        check("AlmostJavadoc", CheckSeverity.OFF)
        check("EmptyBlockTag", CheckSeverity.OFF)
        check("MissingSummary", CheckSeverity.OFF)
        check("ClassCanBeStatic", CheckSeverity.OFF)
        check("ClassNewInstance", CheckSeverity.OFF)
        check("DefaultCharset", CheckSeverity.OFF)
        check("SynchronizeOnNonFinalField", CheckSeverity.OFF)
        excludedPaths.set(".*/app/src/main/java/de/mud/.*|.*/app/src/main/java/org/apache/.*|.*/app/src/main/java/org/keyczar/.*")
    }
}

tasks.withType<Test>().configureEach {
	jacoco {
		exclude("jdk.internal.*")
	}
	// needed for Java 11 https://github.com/gradle/gradle/issues/5184#issuecomment-457865951
}

// Dependencies must be below the android block to allow productFlavor specific deps.
dependencies {
    implementation(libs.sshlib)
    "googleImplementation"(libs.play.services.basement)
    "ossImplementation"(libs.conscrypt.android)

    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference)
    implementation(libs.material)

    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.espresso.contrib) {
        exclude(group = "com.google.android.apps.common.testing.accessibility.framework", module = "accessibility-test-framework")
    }
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.testbutler.library)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(libs.robolectric)

    // Needed for robolectric tests
    testCompileOnly(libs.conscrypt.openjdk.uber)
    testRuntimeOnly(libs.conscrypt.android)
    testImplementation(libs.conscrypt.openjdk.uber)

    errorprone(libs.errorprone.core)
}
