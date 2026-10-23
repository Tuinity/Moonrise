pluginManagement {
	repositories {
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/") {
			name = "Fabric"
		}
		maven("https://maven.ornithemc.net/releases") {
			name = "Ornithe Releases"
		}
		maven("https://repo.jpenilla.xyz/snapshots") {
			name = "jmp"
		}
	}
}

plugins {
	id("xyz.jpenilla.quiet-fabric-loom-remap") version "1.19.347" apply false
	id("ploceus") version "1.18.2" apply false
	id("com.gradleup.shadow") version "9.6.1" apply false
	id("xyz.jpenilla.resource-factory-fabric-convention") version "1.3.1" apply false
}

rootProject.name = "Moonrise-Beta"

include("client")
findProject(":client")!!.name = "Moonrise-Beta-Client"
include("server")
findProject(":server")!!.name = "Moonrise-Beta-Server"
