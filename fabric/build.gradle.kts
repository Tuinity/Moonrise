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
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("loader_version")}")
    testImplementation("net.fabricmc:fabric-loader-junit:${rootProject.property("loader_version")}")

    runtimeOnly(rootProject.sourceSets.main.get().output)
    shadow(project(":"))
    compileOnly(project(":"))

    libs("ca.spottedleaf:concurrentutil:${rootProject.property("concurrentutil_version")}") { isTransitive = false }
    libs("ca.spottedleaf:yamlconfig:${rootProject.property("yamlconfig_version")}") { isTransitive = false }
    libs("org.yaml:snakeyaml:${rootProject.property("snakeyaml_version")}")

    if (gui) {
        add("guiCompileOnly", project(":"))
        runtimeOnly(sourceSets.named("gui").get().output)
        shadow(sourceSets.named("gui").get().output)
        add("modGuiImplementation", "me.shedaniel.cloth:cloth-config-fabric:${rootProject.property("cloth_version")}")
        modRuntimeOnly("me.shedaniel.cloth:cloth-config-fabric:${rootProject.property("cloth_version")}")
        include("me.shedaniel.cloth:cloth-config-fabric:${rootProject.property("cloth_version")}")
        add("modGuiImplementation", "com.terraformersmc:modmenu:${rootProject.property("modmenu_version")}")
        modRuntimeOnly("com.terraformersmc:modmenu:${rootProject.property("modmenu_version")}")
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
        "minecraft_version" to rootProject.property("minecraft_version").toString(),
        "loader_version" to rootProject.property("loader_version").toString(),
        "mod_version" to rootProject.property("mod_version").toString()
    )
    inputs.properties(properties)
    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

tasks.shadowJar {
    archiveClassifier.set("dev-all")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
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
        }
    }
}

tasks.test {
    val classPathGroups = SourceSetHelper.getClasspath(loom.mods.named("main").get(), project)
        .map(File::getAbsolutePath)
        .toList()

    systemProperty("fabric.classPathGroups", classPathGroups)
}

afterEvaluate {
    val runConfigCommon = extensions.getByType(RunConfigCommon::class)
    loom.runs.configureEach {
        runConfigCommon.systemProperties.get().forEach {
            property(it.key, it.value)
        }
        runConfigCommon.jvmArgs.get().forEach {
            vmArgs.add(it)
        }
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
