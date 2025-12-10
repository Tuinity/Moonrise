import me.modmuss50.mpp.ModPublishExtension
import me.modmuss50.mpp.ReleaseType

plugins {
    id("common-conventions")
    id("net.neoforged.moddev")
    id("me.modmuss50.mod-publish-plugin") version "1.1.0" apply false
}

val aw2at = Aw2AtTask.configureDefault(
    project,
    layout.projectDirectory.file("src/main/resources/moonrise.accesswidener").asFile,
    sourceSets.main.get()
)

sourceSets.create("lithium")

neoForge {
    neoFormVersion = libs.versions.neoform.get()
    validateAccessTransformers = true
    accessTransformers.files.setFrom(aw2at.flatMap { t -> t.outputFile })
    addModdingDependenciesTo(sourceSets.getByName("lithium"))
}

dependencies {
    compileOnly(libs.mixin)
    compileOnly(libs.mixinExtras)
    compileOnly(libs.asm)

    api(libs.concurrentutil) { isTransitive = false }
    api(libs.yamlconfig) { isTransitive = false }
    api(libs.snakeyaml)

    // todo: does cloth publish a platform-agnostic jar in mojang mappings?
    compileOnly(libs.clothConfig.neoforge)

    "lithiumCompileOnly"("maven.modrinth:lithium:${rootProject.property("neo_lithium_version")}")
    compileOnly(sourceSets.getByName("lithium").output)
}

subprojects {
    plugins.apply("me.modmuss50.mod-publish-plugin")

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
}
