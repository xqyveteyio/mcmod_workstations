pluginManagement {
	repositories {
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.7.6"
	id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

stonecutter {
	create(rootProject) {
		versions("1.16.5", "1.20.1", "1.21.1")
		vcsVersion = "1.20.1"
	}
}

rootProject.name = "keyboard_workstations"
