import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.benmanes.gradle.versions.updates.resolutionstrategy.ComponentSelectionWithCurrent
import io.github.reactivecircus.appversioning.toSemVer
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.versions)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.app.versioning)
    alias(libs.plugins.coveralls)
    alias(libs.plugins.jacoco.android)
    alias(libs.plugins.easylauncher)
    alias(libs.plugins.spotless)
}


apply(from = "../config/quality.gradle")

coveralls {
    jacocoReportPath = "build/reports/coverage/google/debug/report.xml"
}

appVersioning {
    tagFilter.set("v[0-9].*")
    overrideVersionCode { gitTag, _, _ ->
        val semVer = gitTag.toSemVer()
        semVer.major * 10000000 + semVer.minor * 100000 + semVer.patch * 1000 + gitTag.commitsSinceLatestTag
    }
    overrideVersionName { gitTag, _, _ ->
        if (gitTag.commitsSinceLatestTag != 0) {
            "git-${gitTag.rawTagName}-${gitTag.commitsSinceLatestTag}-g${gitTag.commitHash}"
        } else {
            gitTag.rawTagName
        }
    }
}

android {
    namespace = "org.connectbot"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.connectbot"

        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
        }

        testApplicationId = "org.connectbot.tests"
        testInstrumentationRunner = "org.connectbot.ConnectbotJUnitRunner"

        // The following argument makes the Android Test Orchestrator run its
        // "pm clear" command after each test invocation. This command ensures
        // that the app's state is completely cleared between tests.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        multiDexEnabled = true
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
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true

            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard.cfg")
            testProguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard.cfg", "proguard-tests.cfg")

            if (project.hasProperty("keystorePassword")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        getByName("debug") {
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard.cfg", "proguard-debug.cfg")
            testProguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard.cfg", "proguard-tests.cfg")

            applicationIdSuffix = ".debug"
            enableUnitTestCoverage = true
        }
    }

    flavorDimensions.add("license")

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
        //execution = "ANDROID_TEST_ORCHESTRATOR"
        animationsDisabled = true

        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = false
        lintConfig = file("lint.xml")
    }

    packaging {
        resources.excludes.add("META-INF/LICENSE.txt")
        resources.excludes.add("LICENSE.txt")
        resources.excludes.add("**/*.gwt.xml")
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

spotless {
    java {
        target("**/*.java")
        removeUnusedImports()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        checks.put("InvalidInlineTag", CheckSeverity.OFF)
        checks.put("AlmostJavadoc", CheckSeverity.OFF)
        checks.put("EmptyBlockTag", CheckSeverity.OFF)
        checks.put("MissingSummary", CheckSeverity.OFF)
        checks.put("ClassCanBeStatic", CheckSeverity.OFF)
        checks.put("ClassNewInstance", CheckSeverity.OFF)
        checks.put("DefaultCharset", CheckSeverity.OFF)
        checks.put("SynchronizeOnNonFinalField", CheckSeverity.OFF)
        excludedPaths.set(".*/app/src/main/java/de/mud/.*|.*/app/src/main/java/org/apache/.*|.*/app/src/main/java/org/keyczar/.*")
    }
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// Do not want any release candidates for updates.
tasks.withType<DependencyUpdatesTask>().configureEach {
    revision = "release"
    checkForGradleUpdate = false
    outputFormatter = "json"

    // Android apparently marks their "alpha" as "release" so we have to reject them.
    resolutionStrategy {
        componentSelection {
            all(Action<ComponentSelectionWithCurrent> {
                val rejected = listOf(
                    "alpha",
                    "beta",
                    "rc",
                    "cr",
                    "m",
                    "preview"
                ).any { qualifier ->
                    candidate.version.matches(Regex("(?i).*[.-]${qualifier}[.\\d-]*"))
                }
                if (rejected) {
                    reject("Release candidate")
                }
            })
        }
    }
}

dependencies {
    implementation(libs.sshlib)
    "googleImplementation"(libs.play.services.basement)
    "ossImplementation"(libs.conscrypt.android)

    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference)
    implementation(libs.material)
    implementation(libs.androidx.multidex)

    add("androidTestUtil", libs.androidx.test.orchestrator)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.espresso.contrib) {
        exclude(group = "com.google.android.apps.common.testing.accessibility.framework", module = "accessibility-test-framework")
    }
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.test.butler)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(libs.robolectric)

    testCompileOnly(libs.conscrypt.openjdk.uber)
    testRuntimeOnly(libs.conscrypt.android)
    testImplementation(libs.conscrypt.openjdk.uber)

    errorprone(libs.errorprone.core)
}
