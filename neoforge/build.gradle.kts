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
            sourceSet(rootProject.sourceSets.getByName("architectury"))
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
    runtimeOnly(rootProject.sourceSets.getByName("architectury").output)
    shadow(project(":"))
    shadow(rootProject.sourceSets.getByName("lithium").output)
    shadow(rootProject.sourceSets.getByName("architectury").output)
    compileOnly(project(":"))

    libs(libs.leafpile) { isTransitive = false }
    libs(libs.zstdjni)
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
    archiveClassifier = "slim"
}

tasks.shadowJar {
    archiveClassifier = "all"
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

// Compatibility-testing runs: each mod is only loaded in its dedicated client/server run.
fun Project.compatRuns(name: String, dependency: Any) {
    val compatSourceSet = sourceSets.create("${name}Compat")
    configurations.named(compatSourceSet.runtimeClasspathConfigurationName) {
        extendsFrom(configurations.getByName(sourceSets.main.get().runtimeClasspathConfigurationName))
    }
    dependencies.add(compatSourceSet.runtimeOnlyConfigurationName, dependency)
    neoForge {
        runs {
            register("${name}Client") {
                client()
                disableIdeRun()
                sourceSet = compatSourceSet
            }
            register("${name}Server") {
                server()
                disableIdeRun()
                sourceSet = compatSourceSet
            }
        }
    }
}

compatRuns("lithium", libs.lithium.neoforge)
compatRuns("architectury", libs.architectury.neoforge)
