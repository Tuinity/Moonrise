import me.modmuss50.mpp.ModPublishExtension
import me.modmuss50.mpp.ReleaseType

plugins {
    id("java-library")
    id("net.neoforged.moddev")
    id("me.modmuss50.mod-publish-plugin") version "0.8.4" apply false
}

extensions.create<RunConfigCommon>("runConfigCommon")

val getGitCommit = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim() }

val aw2at = Aw2AtTask.configureDefault(
    project,
    layout.projectDirectory.file("src/main/resources/moonrise.accesswidener").asFile,
    sourceSets.main.get()
)

neoForge {
    neoFormVersion = providers.gradleProperty("neoform_version").get()
    validateAccessTransformers = true
    accessTransformers.files.setFrom(aw2at.flatMap { t -> t.outputFile })
}

extensions.configure<RunConfigCommon>("runConfigCommon") {
    systemProperties.put("mixin.debug", "true")
    systemProperties.put("Moonrise.MaxViewDistance", "128")
    jvmArgs.addAll(listOf("-XX:+UseZGC", "-XX:+ZGenerational", "-XX:+UseDynamicNumberOfGCThreads", "-XX:-ZUncommit"))
}

dependencies {
    compileOnly("net.fabricmc:sponge-mixin:0.15.4+mixin.0.8.7")
    compileOnly("io.github.llamalad7:mixinextras-common:0.4.1")
    // work around minecraft (MDG) forcing ASM 9.3 which is incompatible with the above deps...
    components.withModule("net.neoforged:minecraft-dependencies", RemoveAsmConstraint::class.java)

    api("ca.spottedleaf:concurrentutil:${rootProject.property("concurrentutil_version")}") { isTransitive = false }
    api("ca.spottedleaf:yamlconfig:${rootProject.property("yamlconfig_version")}") { isTransitive = false }
    api("org.yaml:snakeyaml:${rootProject.property("snakeyaml_version")}")

    // todo: does cloth publish a platform-agnostic jar in mojang mappings?
    compileOnly("me.shedaniel.cloth:cloth-config-neoforge:${rootProject.property("cloth_version")}")
}

allprojects {
    group = rootProject.property("maven_group").toString()
    version = rootProject.property("mod_version").toString() + "+" + getGitCommit.get()

    plugins.apply("java-library")

    java {
        withSourcesJar()

        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter:${rootProject.property("junit_version")}")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
    }

    // make build reproducible
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
    }

    tasks.named<org.gradle.jvm.tasks.Jar>("jar").configure {
        from(rootProject.file("LICENSE")) {
            rename { "${it}_${rootProject.base.archivesName.get()}" }
        }
    }
}

subprojects {
    plugins.apply("me.modmuss50.mod-publish-plugin")
    plugins.apply("java-library")
    plugins.apply("com.gradleup.shadow")

    configurations.create("libs")
    configurations.named("shadow") {
        extendsFrom(configurations.getByName("libs"))
    }
    configurations.named("implementation") {
        extendsFrom(configurations.getByName("libs"))
    }

    configure<ModPublishExtension> {
        if (project.version.toString().contains("-beta.")) {
            type = ReleaseType.BETA
        } else {
            type = ReleaseType.STABLE
        }
        changelog = providers.environmentVariable("RELEASE_NOTES")

        val supportedMcVersions = rootProject.property("supported_minecraft_versions").toString().split(',')

        modrinth {
            projectId = "KOHu7RCS"
            accessToken = providers.environmentVariable("MODRINTH_TOKEN")
            minecraftVersions = supportedMcVersions
        }

        curseforge {
            projectId = "1096335"
            accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
            minecraftVersions = supportedMcVersions
        }
    }

    // Setup a run with lithium for compatibility testing
    configurations.create("lithium")
    dependencies {
        var coordinates = "maven.modrinth:lithium:"
        if (project.name == "Moonrise-NeoForge") {
            coordinates += rootProject.property("neo_lithium_version").toString()
        } else {
            coordinates += rootProject.property("fabric_lithium_version").toString()
        }
        add("lithium", coordinates)
    }
}
