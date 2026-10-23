import me.modmuss50.mpp.ModPublishExtension
import me.modmuss50.mpp.ReleaseType

plugins {
    id("common-conventions")
    id("net.neoforged.moddev")
    id("me.modmuss50.mod-publish-plugin") version "2.2.0" apply false
    id("xyz.jpenilla.quiet-fabric-loom-companion")
}

val aw2at = Aw2AtTask.configureDefault(
    project,
    layout.projectDirectory.file("src/main/resources/moonrise.accesswidener").asFile,
    sourceSets.main.get()
)

sourceSets.create("lithium")
sourceSets.create("architectury")

neoForge {
    neoFormVersion = libs.versions.neoform.get()
    validateAccessTransformers = true
    accessTransformers.files.setFrom(aw2at.flatMap { t -> t.outputFile })
    addModdingDependenciesTo(sourceSets.getByName("lithium"))
    addModdingDependenciesTo(sourceSets.getByName("architectury"))
}

dependencies {
    compileOnly(libs.mixin)
    compileOnly(libs.mixinExtras)
    compileOnly(libs.asm)

    api(libs.leafpile.common)
    api(libs.leafpile.concurrentutil)
    api(libs.leafpile.profiler)
    api(libs.leafpile.sampler)
    api(libs.leafpile.yamlconfig)
    api(libs.leafpile.ioutil)

    compileOnly(libs.clothConfig.common)

    "lithiumCompileOnly"(libs.lithium.neoforge)
    "architecturyCompileOnly"(libs.architectury)
    compileOnly(sourceSets.getByName("lithium").output)
}

subprojects {
    plugins.apply("me.modmuss50.mod-publish-plugin")

    configure<ModPublishExtension> {
        val version = project.version.toString()
        type = when {
            version.contains("-alpha.") -> ReleaseType.ALPHA
            version.contains("-beta.") -> ReleaseType.BETA
            else -> ReleaseType.STABLE
        }
        changelog = providers.environmentVariable("RELEASE_NOTES")

        val supportedMcVersions = rootProject.property("supported_minecraft_versions").toString().split(',')
        // CurseForge does not list individual snapshot/pre-release versions, so only publish there for full releases
        val allMcReleases = supportedMcVersions.all { it.matches(Regex("""\d+(\.\d+)+""")) }

        modrinth {
            projectId = "KOHu7RCS"
            accessToken = providers.environmentVariable("MODRINTH_TOKEN")
            minecraftVersions = supportedMcVersions
            environment = CLIENT_OR_SERVER_PREFERS_BOTH
        }

        if (allMcReleases) {
            curseforge {
                projectId = "1096335"
                accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
                minecraftVersions = supportedMcVersions
                client = true
                server = true
            }
        }
    }
}
