import java.nio.file.Files
import java.nio.file.StandardCopyOption
import net.neoforged.moddevgradle.internal.RunGameTask

plugins {
    id("net.neoforged.moddev")
    `maven-publish`
    id("platform-conventions")
}

val aw2at = Aw2AtTask.configureDefault(
    getProject(),
    rootProject.layout.projectDirectory.file("src/main/resources/moonrise.accesswidener").getAsFile(),
    sourceSets.main.get()
)

neoForge {
    version = libs.versions.neoforge.get()
    validateAccessTransformers = true
    accessTransformers.files.setFrom(aw2at.flatMap { t -> t.getOutputFile() })
    mods {
        register("moonrise") {
            sourceSet(sourceSets.main.get())
            sourceSet(rootProject.sourceSets.main.get())
            sourceSet(rootProject.sourceSets.getByName("lithium"))
        }
    }
    runs {
        register("client") {
            client()
        }
        register("server") {
            server()
        }
    }
    unitTest {
        enable()
        testedMod = mods.named("moonrise")
    }
}

val gui = rootProject.property("enable_gui").toString() == "true"

dependencies {
    runtimeOnly(rootProject.sourceSets.main.get().output)
    runtimeOnly(rootProject.sourceSets.getByName("lithium").output)
    shadow(project(":"))
    shadow(rootProject.sourceSets.getByName("lithium").output)
    compileOnly(project(":"))

    libs(libs.concurrentutil) { isTransitive = false }
    libs(libs.yamlconfig) { isTransitive = false }
    libs(libs.snakeyaml)

    if (gui) {
        implementation(libs.clothConfig.neoforge)
        jarJar(libs.clothConfig.neoforge)
    } else {
        compileOnly(libs.clothConfig.neoforge)
    }
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version,
        "minecraft_version" to libs.versions.minecraft.get(),
    )
    inputs.properties(properties)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(properties)
    }
}

tasks.jar {
    archiveClassifier = "dev"
}

tasks.shadowJar {
    archiveClassifier = "dev-all"
    destinationDirectory = layout.buildDirectory.dir("libs")
    configurations = listOf(project.configurations.getByName("shadow"))
    relocate("ca.spottedleaf.concurrentutil", "ca.spottedleaf.moonrise.libs.ca.spottedleaf.concurrentutil")
    relocate("ca.spottedleaf.yamlconfig", "ca.spottedleaf.moonrise.libs.ca.spottedleaf.yamlconfig")
    relocate("org.yaml.snakeyaml", "ca.spottedleaf.moonrise.libs.org.yaml.snakeyaml")
}

val productionJar = tasks.register<Zip>("productionJar") {
    archiveClassifier = ""
    archiveExtension = "jar"
    destinationDirectory = layout.buildDirectory.dir("libs")
    from(tasks.jarJar)
    from(zipTree(tasks.shadowJar.flatMap { it.archiveFile }))
}

tasks.assemble {
    dependsOn(productionJar)
}

publishMods {
    file = productionJar.flatMap { it.archiveFile }
    modLoaders = listOf("neoforge")

    modrinth {
        incompatible(
            "notenoughcrashes",
            "starlight-neoforge",
            "canary"
        )
    }
    curseforge {
        incompatible(
            "not-enough-crashes-forge",
            "starlight-neoforge",
            "canary"
        )
    }
}

neoForge.runs.configureEach {
    runConfigCommon.systemProperties.get().forEach { (k, v) ->
        systemProperties.put(k, v)
    }
    runConfigCommon.jvmArgs.get().forEach {
        jvmArguments.add(it)
    }
}

// Setup a run with lithium for compatibility testing
neoForge {
    runs {
        register("lithiumClient") {
            client()
            disableIdeRun()
        }
    }
}
tasks.withType<RunGameTask>().configureEach {
    if (name == "runLithiumClient") {
        return@configureEach
    }
    val out = gameDirectory.get().getAsFile().toPath().resolve("mods/lithium-tmp.jar")
    doFirst {
        Files.deleteIfExists(out)
    }
}
val lithium = configurations.lithium
tasks.named<RunGameTask>("runLithiumClient") {
    val out = gameDirectory.get().getAsFile().toPath().resolve("mods/lithium-tmp.jar")
    doFirst {
        for (file in lithium.get().files) {
            Files.copy(file.toPath(), out, StandardCopyOption.REPLACE_EXISTING)
        }
    }
    doLast {
        Files.deleteIfExists(out)
    }
}
