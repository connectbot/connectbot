import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension

apply<org.gradle.api.plugins.quality.CheckstylePlugin>()

configure<CheckstyleExtension> {
    toolVersion = "8.10"
}

tasks.named("check") {
    dependsOn("checkstyle")
}

tasks.register<Checkstyle>("checkstyle") {
    source("src/main/java")
    include("org/connectbot/**/*.java")
    exclude("**/gen/**")

    classpath = files()
}
