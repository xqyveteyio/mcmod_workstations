plugins {
	id("dev.kikugie.loom-back-compat")
	id("maven-publish")
}

version = "${property("mod_version")}+${sc.current.version}"
group = property("maven_group") as String

base {
	archivesName.set("${archivesBaseName}-${sc.current.version}-fabric")
}

val javaNumber = property("java_version").toString()
val javaVer = JavaVersion.toVersion(javaNumber)
val obfuscated = sc.current.parsed < "26.1"
val archivesBaseName = property("archives_base_name").toString()

loom {
	accessWidenerPath = sc.process(
		rootProject.file("src/main/resources/keyboard_workstations.accesswidener"),
		"build/processed.accesswidener"
	)

	runConfigs.all {
		preferGradleTask = true
		generateRunConfig = true
		runDirectory = rootProject.file("run")
	}
}

repositories {
	mavenCentral()
}

dependencies {
	minecraft("com.mojang:minecraft:${sc.current.version}")
	if (obfuscated) {
		mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
	}
	modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
}

tasks.processResources {
	val props = mapOf(
		"version" to version.toString(),
		"minecraft" to property("minecraft_dep").toString(),
		"java" to javaNumber,
		"loader" to property("loader_version").toString(),
		"fabric_api_id" to property("fabric_api_id").toString(),
	)

	inputs.properties(props)

	filesMatching("fabric.mod.json") {
		expand(props)
	}

	if (sc.current.version == "1.16.5") {
		exclude("data/minecraft/tags/blocks/mineable/**")
	}

	if (sc.current.parsed >= "1.21") {
		filesMatching("data/**/recipes/**") {
			filter { line: String ->
				line.replace(Regex(""""item": "(keyboard_workstations:[^"]+)""""), """"id": "$1"""")
			}
		}

		eachFile {
			path = path
				.replace("/recipes/", "/recipe/")
				.replace("/loot_tables/", "/loot_table/")
				.replace("/tags/blocks/", "/tags/block/")
		}
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release.set(javaNumber.toInt())
	options.compilerArgs.addAll(listOf("-Xmaxerrs", "1000"))
}

java {
	withSourcesJar()
	sourceCompatibility = javaVer
	targetCompatibility = javaVer
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(javaNumber))
	}
}

tasks.jar {
	manifest {
		attributes(
			mapOf(
				"Implementation-Title" to archivesBaseName,
				"Implementation-Version" to version,
			)
		)
	}
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			artifactId = archivesBaseName
			from(components["java"])
		}
	}
}
