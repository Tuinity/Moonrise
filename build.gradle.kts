import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile

plugins {
	`java-library`
}

allprojects {
	repositories {
		maven("https://repo.papermc.io/repository/maven-public/") {
			name = "PaperMC"
			mavenContent {
				includeGroup("ca.spottedleaf")
				includeGroup("ca.spottedleaf.leafpile")
			}
		}
		mavenCentral()
	}
}

val getGitCommit = providers.exec {
	commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim() }

group = property("maven_group").toString()
version = "${property("mod_version")}+${getGitCommit.get()}+mc${libs.versions.minecraft.get()}"

java {
	withSourcesJar()

	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

// make build reproducible
tasks.withType<AbstractArchiveTask>().configureEach {
	isPreserveFileTimestamps = false
	isReproducibleFileOrder = true
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.release = 25
}

dependencies {
	api(libs.leafpile.concurrentutil)
	api(libs.leafpile.yamlconfig)
}
