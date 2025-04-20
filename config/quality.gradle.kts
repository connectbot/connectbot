apply(plugin = "checkstyle")

//checkstyle {
//	toolVersion = "8.10"
//}

tasks.named("check") {
	dependsOn("checkstyle")
}

tasks.register<Checkstyle>("checkstyle") {
	source("src/main/java")
	include("org/connectbot/**/*.java")
	exclude("**/gen/**")
	classpath = files()
}
