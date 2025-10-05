import net.fabricmc.loom.task.RunGameTask
import net.fabricmc.loom.util.gradle.SourceSetHelper

plugins {
    id("quiet-fabric-loom")
    `maven-publish`
    id("platform-conventions")
}

val gui = rootProject.property("enable_gui").toString() == "true"
if (gui) {
    sourceSets.create("gui")
    loom.createRemapConfigurations(sourceSets.getByName("gui"))
}

dependencies {
    minecraft(libs.fabricMinecraft)
    mappings(loom.layered {
        officialMojangMappings()
        parchment(rootProject.property("neoForge.parchment.parchmentArtifact"))
    })
    modImplementation(libs.fabricLoader)
    testImplementation(libs.fabricLoader.junit)

    runtimeOnly(rootProject.sourceSets.main.get().output)
    runtimeOnly(rootProject.sourceSets.getByName("lithium").output)
    shadow(project(":"))
    shadow(rootProject.sourceSets.getByName("lithium").output)
    compileOnly(project(":"))

    libs(libs.concurrentutil) { isTransitive = false }
    libs(libs.yamlconfig) { isTransitive = false }
    libs(libs.snakeyaml)

    if (gui) {
        add("guiCompileOnly", project(":"))
        runtimeOnly(sourceSets.named("gui").get().output)
        shadow(sourceSets.named("gui").get().output)
        add("modGuiImplementation", libs.clothConfig.fabric)
        modRuntimeOnly(libs.clothConfig.fabric)
        include(libs.clothConfig.fabric)
        add("modGuiImplementation", libs.modmenu)
        modRuntimeOnly(libs.modmenu)
    }

    modImplementation(platform(fabricApiLibs.bom))
    modImplementation(fabricApiLibs.command.api.v2)
    modImplementation(fabricApiLibs.lifecycle.events.v1)
    include(fabricApiLibs.command.api.v2)
    include(fabricApiLibs.base)
}

if (gui) {
    afterEvaluate {
        configurations.named("guiCompileOnly") {
            extendsFrom(configurations.getByName("minecraftNamedCompile"))
        }
    }
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version,
        "minecraft_version" to libs.versions.minecraft.get(),
        "loader_version" to libs.versions.fabricLoader.get(),
    )
    inputs.properties(properties)
    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

tasks.shadowJar {
    archiveClassifier.set("dev-all")
    configurations = listOf(project.configurations.getByName("shadow"))
    relocate("ca.spottedleaf.concurrentutil", "ca.spottedleaf.moonrise.libs.ca.spottedleaf.concurrentutil")
    relocate("ca.spottedleaf.yamlconfig", "ca.spottedleaf.moonrise.libs.ca.spottedleaf.yamlconfig")
    relocate("org.yaml.snakeyaml", "ca.spottedleaf.moonrise.libs.org.yaml.snakeyaml")
}

publishMods {
    file = tasks.remapJar.flatMap { it.archiveFile }
    modLoaders = listOf("fabric")

    modrinth {
        incompatible(
            "notenoughcrashes",
            "starlight",
            "c2me-fabric"
        )
    }
    curseforge {
        incompatible(
            "not-enough-crashes",
            "starlight",
            "c2me"
        )
    }
}

loom {
    accessWidenerPath.set(rootProject.file("src/main/resources/moonrise.accesswidener"))
    mixin {
        useLegacyMixinAp = false
    }
    runs.configureEach {
        ideConfigGenerated(true)
    }
    mods {
        create("main") {
            sourceSet("main")
            sourceSet("main", project.rootProject)
            sourceSet("lithium", project.rootProject)
        }
    }
}

tasks.test {
    val classPathGroups = SourceSetHelper.getClasspath(loom.mods.named("main").get(), project)
        .map(File::getAbsolutePath)
        .toList()

    systemProperty("fabric.classPathGroups", classPathGroups)
}

loom.runs.configureEach {
    runConfigCommon.systemProperties.get().forEach {
        property(it.key, it.value)
    }
    runConfigCommon.jvmArgs.get().forEach {
        vmArgs.add(it)
    }
}

// Setup a run with lithium for compatibility testing
sourceSets.create("lithium")
loom {
    createRemapConfigurations(sourceSets.getByName("lithium"))
    runs {
        register("lithiumClient") {
            client()
        }
    }
}
configurations.named("modLithiumRuntimeOnly") {
    extendsFrom(configurations.getByName("lithium"))
}
tasks.named("runLithiumClient", RunGameTask::class.java) {
    (classpath as ConfigurableFileCollection).from(configurations.named("modRuntimeClasspathLithiumMapped"))
}
