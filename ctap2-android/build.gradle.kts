import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.android.library)
}
android {
    namespace = "org.connectbot.ctap2.android"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api(project(":ctap2"))
    implementation(libs.yubikit.android)
    implementation(libs.timber)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.assertj.core)
}

kover { useJacoco(libs.versions.jacoco.get()) }

tasks.register<Sync>("stageSonarClasses") {
    into(layout.buildDirectory.dir("sonar-classes"))
    from(tasks.named<KotlinCompile>("compileDebugKotlin").flatMap { it.destinationDirectory }) {
        include("**/*.class")
        into("main/ctap2-android/kotlin")
    }
    from(tasks.named<KotlinCompile>("compileDebugUnitTestKotlin").flatMap { it.destinationDirectory }) {
        include("**/*.class")
        into("test/ctap2-android/kotlin")
    }
}
