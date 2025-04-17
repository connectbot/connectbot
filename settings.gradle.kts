pluginManagement {
	repositories {
		google()
		gradlePluginPortal()
	}
}

val translationsOnly: String = System.getenv("TRANSLATIONS_ONLY")?.trim() ?: ""
if (translationsOnly.isEmpty()) {
	include(":app")
}
include(":translations")
