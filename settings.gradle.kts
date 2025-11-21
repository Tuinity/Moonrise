pluginManagement {
    repositories {
        mavenCentral {
            mavenContent { releasesOnly() }
        }
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://maven.neoforged.net/releases/") {
            name = "NeoForged"
        }
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "PaperMC"
        }
        maven("https://repo.jpenilla.xyz/snapshots") {
            name = "jmp"
        }
        maven("https://maven.architectury.dev/") {
            name = "architectury"
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("quiet-fabric-loom") version "1.13.328"
    // https://projects.neoforged.net/neoforged/moddevgradle
    id("net.neoforged.moddev.repositories") version "2.0.119"
}

dependencyResolutionManagement {
    repositories {
        maven("https://api.modrinth.com/maven") {
            mavenContent {
                includeGroup("maven.modrinth")
            }
        }
        mavenCentral {
            mavenContent { releasesOnly() }
        }
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "PaperMC"
        }
        maven("https://maven.shedaniel.me/")
        maven("https://maven.terraformersmc.com/releases/")
        // TODO - remove on NeoForge release
        maven {
            name = "Maven for PR #2815" // https://github.com/neoforged/NeoForge/pull/2815
            url = uri("https://prmaven.neoforged.net/NeoForge/pr2815")
            content {
                includeModule("net.neoforged", "neoforge")
                includeModule("net.neoforged", "testframework")
            }
        }
    }
    versionCatalogs {
        create("fabricApiLibs") {
            from("net.fabricmc.fabric-api:fabric-api-catalog:${providers.gradleProperty("fabric_api_version").get()}")
        }
    }
}

rootProject.name = "Moonrise"

include("fabric")
findProject(":fabric")!!.name = "Moonrise-Fabric"
include("neoforge")
findProject(":neoforge")!!.name = "Moonrise-NeoForge"

// includeBuild("../YamlConfig") // Uncomment to use local YamlConfig
// includeBuild("../ConcurrentUtil") // Uncomment to use local ConcurrentUtil
