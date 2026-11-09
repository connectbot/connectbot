import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kover)
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}
kotlin { jvmToolchain(17) }
dependencies {
    api(libs.yubikit.core)
    implementation(libs.yubikit.fido) {
        // YubiKit 3.0.0 declares Moshi but does not reference it; Okio is transitive.
        exclude(group = "com.squareup.moshi", module = "moshi")
    }
    testImplementation(libs.junit)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

kover { useJacoco(libs.versions.jacoco.get()) }

tasks.register<Sync>("stageSonarClasses") {
    into(layout.buildDirectory.dir("sonar-classes"))
    from(tasks.named<KotlinCompile>("compileKotlin").flatMap { it.destinationDirectory }) {
        include("**/*.class")
        into("main/ctap2/kotlin")
    }
    from(tasks.named<KotlinCompile>("compileTestKotlin").flatMap { it.destinationDirectory }) {
        include("**/*.class")
        into("test/ctap2/kotlin")
    }
}
