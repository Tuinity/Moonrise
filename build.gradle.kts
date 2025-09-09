import me.modmuss50.mpp.ModPublishExtension
import me.modmuss50.mpp.ReleaseType

plugins {
    id("common-conventions")
    id("net.neoforged.moddev")
    id("me.modmuss50.mod-publish-plugin") version "0.8.4" apply false
}

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

dependencies {
    compileOnly(libs.mixin)
    compileOnly(libs.mixinExtras)
    // work around minecraft (MDG) forcing ASM 9.3 which is incompatible with the above deps...
    components.withModule("net.neoforged:minecraft-dependencies", RemoveAsmConstraint::class.java)

    api(libs.concurrentutil) { isTransitive = false }
    api(libs.yamlconfig) { isTransitive = false }
    api(libs.snakeyaml)

    // todo: does cloth publish a platform-agnostic jar in mojang mappings?
    compileOnly(libs.clothConfig.neoforge)
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
