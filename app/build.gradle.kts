import io.github.reactivecircus.appversioning.toSemVer
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
	id("com.android.application") version "8.2.2"
	id("com.github.ben-manes.versions") version "0.51.0"
	id("net.ltgt.errorprone") version "4.2.0"
	id("io.github.reactivecircus.app-versioning") version "1.4.0"
	id("com.github.kt3k.coveralls") version "2.12.2"
	id("com.mxalbert.gradle.jacoco-android") version "0.2.1"
	id("com.starter.easylauncher") version "6.4.0"
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
	implementation("org.connectbot:sshlib:2.2.25")
	"googleImplementation"("com.google.android.gms:play-services-basement:18.3.0")
	"ossImplementation"("org.conscrypt:conscrypt-android:2.5.3")

    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.preference:preference:1.2.1")
    implementation("com.google.android.material:material:1.9.0")

	androidTestUtil("androidx.test:orchestrator:$testRunnerVersion")
	androidTestImplementation("androidx.test:core:1.5.0")
	androidTestImplementation("androidx.test:rules:$testRunnerVersion")
	androidTestImplementation("androidx.test.espresso:espresso-core:$espressoVersion")
	androidTestImplementation("androidx.test.espresso:espresso-intents:$espressoVersion")
	androidTestImplementation("androidx.test.espresso:espresso-contrib:$espressoVersion") {
		exclude(group = "com.google.android.apps.common.testing.accessibility.framework", module = "accessibility-test-framework")
	}
	androidTestImplementation("androidx.test.ext:junit:1.1.5")
	androidTestImplementation("com.linkedin.testbutler:test-butler-library:2.2.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.mockito:mockito-core:5.17.0")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.robolectric:robolectric:4.14.1")

	// Needed for robolectric tests
	compileOnly("org.conscrypt:conscrypt-openjdk-uber:2.5.2")
	testRuntimeOnly("org.conscrypt:conscrypt-android:2.5.3")
	testImplementation("org.conscrypt:conscrypt-openjdk-uber:2.5.2")

	errorprone("com.google.errorprone:error_prone_core:2.36.0")
}
