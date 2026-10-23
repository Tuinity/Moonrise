import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import xyz.jpenilla.resourcefactory.fabric.Environment

plugins {
	`java-library`
	id("xyz.jpenilla.quiet-fabric-loom-remap")
	id("ploceus")
	id("com.gradleup.shadow")
	id("xyz.jpenilla.resource-factory-fabric-convention")
}

group = rootProject.property("maven_group").toString()
version = rootProject.version.toString()

base {
	archivesName = "${rootProject.property("archives_base_name")}-client"
}

java {
	withSourcesJar()

	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

tasks.withType<AbstractArchiveTask>().configureEach {
	isPreserveFileTimestamps = false
	isReproducibleFileOrder = true
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release = 25
}

ploceus {
	setIntermediaryGeneration(2)
}

loom {
	clientOnlyMinecraftJar()

	runs.configureEach {
		generateRunConfig = true
	}

	mods {
		create("main") {
			sourceSet("main")
		}
	}
}

// Share the common sources from the root project.
val rootSourceSets = rootProject.extensions.getByType<SourceSetContainer>().getByName("main")
sourceSets.named("main") {
	java.srcDirs(rootSourceSets.java.srcDirs)
	resources.srcDirs(rootSourceSets.resources.srcDirs)
}

// Bundle normal implementation dependencies (our LeafPile libs) into the shadow jar.
configurations.named("shadow") {
	extendsFrom(configurations.named("implementation"))
}

dependencies {
	minecraft("com.mojang:minecraft:${libs.versions.minecraft.get()}")
	exceptions(ploceus.raven(libs.versions.raven.get()))
	signatures(ploceus.sparrow(libs.versions.sparrow.get()))
	nests(ploceus.nests(libs.versions.nests.client.get()))
	mappings(ploceus.featherMappings(libs.versions.feather.get()))

	implementation(libs.leafpile.concurrentutil)
	implementation(libs.leafpile.yamlconfig)

	modImplementation(libs.fabric.loader)

	testImplementation(libs.fabric.loader.junit)
	testImplementation(libs.junit.jupiter)
	testRuntimeOnly(libs.junit.platform.launcher)

	ploceus.dependOsl(libs.versions.osl.get())
}

tasks.test {
	useJUnitPlatform()
	systemProperty("fabric.side", "client")
}

fabricModJson {
	id = "moonrise"
	version = project.version.toString()
	name = "Moonrise"
	environment = Environment.CLIENT
	author("Spottedleaf")
	contact {
		issues = "https://github.com/Tuinity/Moonrise/issues"
		sources = "https://github.com/Tuinity/Moonrise"
		homepage = "https://www.curseforge.com/minecraft/mc-mods/moonrise"
		extra.put("discord", "https://discord.gg/tuinity")
	}
	license("GPL-3.0-only")
	icon("assets/moonrise/icon.png")
	mixin("moonrise.mixins.json")
	depends("fabricloader", ">=${libs.versions.fabric.loader.get()}")
	depends("minecraft", libs.versions.game.version.get())
	depends("osl-entrypoints", ">=${libs.versions.entrypoints.get()}")
}

tasks.named<ShadowJar>("shadowJar") {
	destinationDirectory = layout.buildDirectory.dir("libs")
	dependencies {
		// Minecraft provides these at runtime
		exclude(dependency("it.unimi.dsi:fastutil:.*"))
		exclude(dependency("org.slf4j:slf4j-api:.*"))
		exclude(dependency("net.java.dev.jna:jna:.*"))
	}
	configurations = setOf(project.configurations.getByName("shadow"))
	relocate("ca.spottedleaf.concurrentutil", "ca.spottedleaf.moonrise.libs.ca.spottedleaf.concurrentutil")
	relocate("ca.spottedleaf.yamlconfig", "ca.spottedleaf.moonrise.libs.ca.spottedleaf.yamlconfig")
	relocate("org.yaml.snakeyaml", "ca.spottedleaf.moonrise.libs.org.yaml.snakeyaml")
}
