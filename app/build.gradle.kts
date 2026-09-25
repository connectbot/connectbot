import io.github.reactivecircus.appversioning.toSemVer
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.util.zip.ZipInputStream

abstract class PrepareOssMoshArtifacts : DefaultTask() {
    @get:Input abstract val releaseTag: Property<String>

    @get:OutputDirectory abstract val jniLibsDirectory: DirectoryProperty

    @get:OutputDirectory abstract val assetsDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val tag = releaseTag.get()
        val jniLibsRoot = jniLibsDirectory.get().asFile
        val assetsRoot = assetsDirectory.get().asFile
        jniLibsRoot.deleteRecursively()
        assetsRoot.deleteRecursively()
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        abis.forEach { abi ->
            val url = URI("https://github.com/connectbot/mosh4android/releases/download/$tag/mosh-android-$abi.zip").toURL()
            val connection = url.openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 60_000
            }
            var foundClient = false
            var foundTerminfo = false
            ZipInputStream(connection.getInputStream().buffered()).use { archive ->
                var entry = archive.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "mosh-client" -> {
                            val target = jniLibsRoot.resolve("$abi/libmoshexec.so")
                            target.parentFile.mkdirs()
                            target.outputStream().use { archive.copyTo(it) }
                            foundClient = true
                        }

                        "terminfo.zip" -> {
                            if (abi == abis.first()) {
                                val nonClosing = object : java.io.FilterInputStream(archive) {
                                    override fun close() {}
                                }
                                ZipInputStream(nonClosing).use { nested ->
                                    var nestedEntry = nested.nextEntry
                                    while (nestedEntry != null) {
                                        if (nestedEntry.name == "share/terminfo/x/xterm-256color") {
                                            val target = assetsRoot.resolve("share/terminfo/x/xterm-256color")
                                            target.parentFile.mkdirs()
                                            target.outputStream().use { nested.copyTo(it) }
                                            foundTerminfo = true
                                        }
                                        nested.closeEntry()
                                        nestedEntry = nested.nextEntry
                                    }
                                }
                            } else {
                                foundTerminfo = true
                            }
                        }
                    }
                    archive.closeEntry()
                    entry = archive.nextEntry
                }
            }
            check(foundClient && foundTerminfo) { "Missing mosh-client or terminfo in $abi release archive" }
        }
        assetsRoot.resolve("mosh-release.txt").writeText(tag)
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.app.versioning)
    alias(libs.plugins.easylauncher)
    alias(libs.plugins.spotless)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kover)
    alias(libs.plugins.sonarqube)
}

val moshReleaseTag = rootProject.file("gradle/mosh4android.version")
    .readLines().first { it.isNotBlank() && !it.startsWith("#") }.trim()

val generatedOssMosh = layout.buildDirectory.dir("generated/mosh/oss")
val prepareOssMoshArtifacts = tasks.register<PrepareOssMoshArtifacts>("prepareOssMoshArtifacts") {
    releaseTag.set(moshReleaseTag)
    jniLibsDirectory.set(generatedOssMosh.map { it.dir("jniLibs") })
    assetsDirectory.set(generatedOssMosh.map { it.dir("assets") })
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

    dynamicFeatures += setOf(":mosh")

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
            enableSplit = true
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
            manifestPlaceholders["memtagMode"] = "async"
            isShrinkResources = true
            isMinifyEnabled = true

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg")
            testProguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg", "proguard-tests.cfg")

            if (project.hasProperty("keystorePassword")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        debug {
            manifestPlaceholders["memtagMode"] = "sync"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg", "proguard-debug.cfg")
            testProguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg", "proguard-tests.cfg")

            applicationIdSuffix = ".debug"
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
            buildConfigField("String", "MOSH_RELEASE_TAG", "\"$moshReleaseTag\"")
        }

        // This product flavor uses the Google Play Services library for
        // ProviderInstaller. It uses Conscrypt under-the-hood, but the
        // Google Play Services SDK itself is not open source.
        create("google") {
            dimension = "license"
            versionNameSuffix = ""
            // Google Play Services available for downloadable fonts
            buildConfigField("Boolean", "HAS_DOWNLOADABLE_FONTS", "true")
            buildConfigField("String", "MOSH_RELEASE_TAG", "\"$moshReleaseTag\"")
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
        getByName("test") {
            kotlin.directories.add("src/sharedTest/kotlin")
        }
        getByName("testOss") {
            kotlin.directories.add("src/sharedTest/kotlin")
        }
        getByName("testGoogle") {
            kotlin.directories.add("src/sharedTest/kotlin")
        }
        getByName("androidTest") {
            kotlin.directories.add("src/sharedTest/kotlin")
        }
    }

    lint {
        abortOnError = false
        lintConfig = file("lint.xml")
        checkTestSources = true
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

val sonarJavaBinaries = mutableListOf<Provider<String>>()
val sonarJavaTestBinaries = mutableListOf<Provider<String>>()
val sonarAndroidLintReportPaths = mutableListOf<Provider<String>>()

androidComponents {
    onVariants(selector().withFlavor("license" to "oss")) { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(prepareOssMoshArtifacts) { it.jniLibsDirectory }
        variant.sources.assets?.addGeneratedSourceDirectory(prepareOssMoshArtifacts) { it.assetsDirectory }
        variant.packaging.jniLibs.useLegacyPackaging.set(true)
        variant.packaging.jniLibs.useLegacyPackagingFromBundle.set(true)
    }

    onVariants(selector().withFlavor("license" to "google")) { variant ->
        // The Play-delivered mosh-client needs an executable path in nativeLibraryDir.
        variant.packaging.jniLibs.useLegacyPackaging.set(true)
        variant.packaging.jniLibs.useLegacyPackagingFromBundle.set(true)
    }

    onVariants(selector().withBuildType("debug")) { variant ->
        val variantName = variant.name
        val variantTaskName = variantName.replaceFirstChar { it.uppercaseChar() }

        sonarJavaBinaries +=
            layout.buildDirectory
                .dir("intermediates/classes/$variantName/jacoco$variantTaskName/dirs")
                .map { it.asFile.path }
        sonarJavaBinaries +=
            layout.buildDirectory
                .dir("intermediates/javac/$variantName/compile${variantTaskName}JavaWithJavac/classes")
                .map { it.asFile.path }
        sonarJavaTestBinaries +=
            layout.buildDirectory
                .dir("intermediates/classes/${variantName}UnitTest/transform${variantTaskName}UnitTestClassesWithAsm/dirs")
                .map { it.asFile.path }
        sonarJavaTestBinaries +=
            layout.buildDirectory
                .dir("intermediates/javac/${variantName}AndroidTest/compile${variantTaskName}AndroidTestJavaWithJavac/classes")
                .map { it.asFile.path }
        sonarAndroidLintReportPaths +=
            layout.buildDirectory
                .file("reports/lint-results-$variantName.xml")
                .map { it.asFile.path }
    }
}

kover {
    reports {
        filters {
            excludes {
                // Third-party code vendored in the source tree
                packages(
                    "de.mud.*",
                    "com.google.ase",
                    "org.apache.*",
                    "org.keyczar.*",
                    "org.openintents.*",
                )
                // Hilt/Dagger generated code
                packages("dagger.*", "hilt_aggregated_deps")
                classes(
                    "*_MembersInjector",
                    "*_MembersInjector\$*",
                    "*_Factory",
                    "*_Factory\$*",
                    "*Hilt_*",
                    "*_GeneratedInjector",
                    "*_HiltModules*",
                    "*_HiltComponents*",
                    "*Module_Provide*",
                    "*Module_Bind*",
                )
                // Room generated implementations
                classes(
                    "*_Impl",
                    "*_Impl\$*",
                    "*_AutoMigration_*",
                    "*_AutoMigration_*\$*",
                )
                // Build config
                classes("*.BuildConfig")
            }
        }
    }
}

sonar {
    properties {
        property("sonar.projectKey", "connectbot_connectbot")
        property("sonar.organization", "connectbot")
        property("sonar.host.url", "https://sonarcloud.io")
        property(
            "sonar.exclusions",
            """
            |src/main/java/com/google/ase/**,
            |src/main/java/de/mud/**,
            |src/main/java/org/apache/**,
            |src/main/java/org/keyczar/**,
            |src/main/java/org/openintents/**
            """.trimMargin(),
        )
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            """
            |build/reports/kover/reportOssDebug.xml,
            |build/reports/kover/reportGoogleDebug.xml,
            |build/reports/coverage/androidTest/oss/debug/connected/report.xml,
            |build/reports/coverage/androidTest/google/debug/connected/report.xml,
            """.trimMargin(),
        )
    }
}

afterEvaluate {
    sonar {
        properties {
            property("sonar.java.binaries", sonarJavaBinaries.joinToString(",") { it.get() })
            property("sonar.java.test.binaries", sonarJavaTestBinaries.joinToString(",") { it.get() })
            property("sonar.androidLint.reportPaths", sonarAndroidLintReportPaths.joinToString(",") { it.get() })
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

// Generate filtered export schema from Room schema
// Includes keyboard configuration and host/profile tables in the export schema
val generateExportSchema by tasks.registering {
    val exportTables = setOf("profiles", "hosts", "port_forwards", "automation_actions", "keyboard_layouts", "keyboard_macros", "keyboard_items")
    val excludedFields = setOf("last_connect", "host_key_algo")

    // Read schema version from Room's @Database annotation.
    val databaseFile = file("src/main/java/org/connectbot/data/ConnectBotDatabase.kt")
    val schemaVersion =
        databaseFile
            .readText()
            .let { Regex("""@Database\s*\([\s\S]*?version\s*=\s*(\d+)""").find(it) }
            ?.groupValues
            ?.get(1)
            ?.toInt()
            ?: error("Could not find @Database version in $databaseFile")
    val inputFile = file("schemas/org.connectbot.data.ConnectBotDatabase/$schemaVersion.json")
    val outputDir = file("build/generated/exportSchema")
    val outputFile = file("$outputDir/export_schema.json")

    inputs.file(databaseFile)
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

dependencies {
    implementation(libs.sshlib)
    implementation(libs.termlib)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.navigation.testing)
    implementation(libs.androidx.ui)
    implementation(libs.reorderable)
    "googleImplementation"(libs.play.services.basement)
    "googleImplementation"(libs.play.feature.delivery)
    testImplementation(libs.play.feature.delivery)
    "ossImplementation"(libs.conscrypt.android)

    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.preference)
    implementation(libs.material)
    implementation(libs.timber)
    implementation(libs.re2j)
    implementation(libs.reorderable)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
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

    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)
    testImplementation(libs.androidx.compose.ui.test)
    testImplementation(libs.androidx.compose.ui.test.junit4)

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
}
