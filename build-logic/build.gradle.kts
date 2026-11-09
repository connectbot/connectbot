plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(libs.gradle.download)
    testImplementation(libs.junit)
    testImplementation(gradleTestKit())
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        register("moshArtifacts") {
            id = "org.connectbot.mosh-artifacts"
            implementationClass = "org.connectbot.buildlogic.MoshArtifactsPlugin"
        }
    }
}

tasks.test {
    maxHeapSize = "512m"
}
