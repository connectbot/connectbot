import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.benmanes.gradle.versions.updates.resolutionstrategy.ComponentSelectionWithCurrent
import io.github.reactivecircus.appversioning.toSemVer
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.versions)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.app.versioning)
    alias(libs.plugins.coveralls)
    alias(libs.plugins.jacoco.android)
    alias(libs.plugins.easylauncher)
    alias(libs.plugins.spotless)
    alias(libs.plugins.hilt.android)
}

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
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "org.connectbot"

        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()

        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
            debugSymbolLevel = "full"
        }

        testApplicationId = "org.connectbot.tests"
        testInstrumentationRunner = "org.connectbot.HiltTestRunner"

        // The following argument makes the Android Test Orchestrator run its
        // "pm clear" command after each test invocation. This command ensures
        // that the app's state is completely cleared between tests.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"

        multiDexEnabled = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
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

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg")
            testProguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg", "proguard-tests.cfg")

            if (project.hasProperty("keystorePassword")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        debug {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg", "proguard-debug.cfg")
            testProguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg", "proguard-tests.cfg")

            applicationIdSuffix = ".debug"
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }

    flavorDimensions.add("license")

    productFlavors {
        // This product flavor uses the Conscrypt library which is open
        // source and licensed under Apache 2.
        create("oss") {
            dimension = "license"
            versionNameSuffix = "-oss"
            // No Google Play Services available for downloadable fonts
            buildConfigField("Boolean", "HAS_DOWNLOADABLE_FONTS", "false")
        }

        // This product flavor uses the Google Play Services library for
        // ProviderInstaller. It uses Conscrypt under-the-hood, but the
        // Google Play Services SDK itself is not open source.
        create("google") {
            dimension = "license"
            versionNameSuffix = ""
            // Google Play Services available for downloadable fonts
            buildConfigField("Boolean", "HAS_DOWNLOADABLE_FONTS", "true")
        }
    }

    testOptions {
        execution = "ANDROID_TEST_ORCHESTRATOR"
        animationsDisabled = true
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("main") {
            assets.directories.add("build/generated/exportSchema")
        }
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
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
        excludedPaths.set(".*/src/main/java/de/mud/.*|.*/src/main/java/org/apache/.*|.*/src/main/java/org/keyczar/.*")
    }
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// Generate filtered export schema from Room schema
// Only includes tables needed for export/import (profiles, hosts, port_forwards)
val generateExportSchema by tasks.registering {
    val exportTables = setOf("profiles", "hosts", "port_forwards")
    val excludedFields = setOf("last_connect", "host_key_algo")

    // Read schema version from ConnectBotDatabase.kt to avoid duplicate definitions
    val databaseFile = file("src/main/java/org/connectbot/data/ConnectBotDatabase.kt")
    val schemaVersion =
        databaseFile
            .readText()
            .let { Regex("""const val SCHEMA_VERSION\s*=\s*(\d+)""").find(it) }
            ?.groupValues
            ?.get(1)
            ?.toInt()
            ?: error("Could not find SCHEMA_VERSION in $databaseFile")
    val inputFile = file("schemas/org.connectbot.data.ConnectBotDatabase/$schemaVersion.json")
    val outputDir = file("build/generated/exportSchema")
    val outputFile = file("$outputDir/export_schema.json")

    inputs.file(inputFile)
    outputs.file(outputFile)

    doLast {
        val inputJson = groovy.json.JsonSlurper().parseText(inputFile.readText()) as Map<*, *>
        val database = inputJson["database"] as Map<*, *>
        val entities = database["entities"] as List<*>

        // Filter entities to only include export tables
        val filteredEntities =
            entities
                .filter { entity ->
                    val entityMap = entity as Map<*, *>
                    entityMap["tableName"] in exportTables
                }.map { entity ->
                    val entityMap = (entity as Map<*, *>).toMutableMap()
                    // Mark excluded fields instead of removing them (needed for NOT NULL defaults)
                    val fields = entityMap["fields"] as List<*>
                    entityMap["fields"] =
                        fields.map { field ->
                            val fieldMap = (field as Map<*, *>).toMutableMap()
                            if (fieldMap["columnName"] in excludedFields) {
                                fieldMap["excluded"] = true
                            }
                            fieldMap
                        }
                    entityMap
                }

        val filteredSchema =
            mapOf(
                "formatVersion" to inputJson["formatVersion"],
                "database" to
                    mapOf(
                        "version" to database["version"],
                        "entities" to filteredEntities,
                    ),
            )

        outputDir.mkdirs()
        outputFile.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(filteredSchema)))
    }
}

// Ensure export schema is generated before tasks that read from the assets directory
tasks
    .matching {
        (it.name.contains("merge") && it.name.contains("Assets")) ||
            it.name.contains("Lint", ignoreCase = true)
    }.configureEach {
        dependsOn(generateExportSchema)
    }

// Do not want any release candidates for updates.
tasks.withType<DependencyUpdatesTask>().configureEach {
    revision = "release"
    checkForGradleUpdate = false
    outputFormatter = "json"

    // Android apparently marks their "alpha" as "release" so we have to reject them.
    resolutionStrategy {
        componentSelection {
            all(
                Action<ComponentSelectionWithCurrent> {
                    val rejected =
                        listOf(
                            "alpha",
                            "beta",
                            "rc",
                            "cr",
                            "m",
                            "preview",
                        ).any { qualifier ->
                            candidate.version.matches(Regex("(?i).*[.-]$qualifier[.\\d-]*"))
                        }
                    if (rejected) {
                        reject("Release candidate")
                    }
                },
            )
        }
    }
}

dependencies {
    implementation(libs.sshlib)
    implementation(libs.termlib)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.navigation.testing)
    implementation(libs.androidx.ui)
    "googleImplementation"(libs.play.services.basement)
    "ossImplementation"(libs.conscrypt.android)

    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.preference)
    implementation(libs.material)
    implementation(libs.timber)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.espresso.contrib) {
        exclude(group = "com.google.android.apps.common.testing.accessibility.framework", module = "accessibility-test-framework")
    }
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.assertj.core)

    androidTestUtil(libs.androidx.test.orchestrator)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.assertj.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    testCompileOnly(libs.conscrypt.openjdk.uber)
    testRuntimeOnly(libs.conscrypt.android)
    testImplementation(libs.conscrypt.openjdk.uber)

    errorprone(libs.errorprone.core)
}
