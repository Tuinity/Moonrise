plugins {
    id("java-library")
}

val getGitCommit = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim() }

version = version.toString() + "+" + getGitCommit.get()

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
    options.release = 21
}

tasks.named<Jar>("jar").configure {
    val archivesBaseName = rootProject.base.archivesName.get()
    val licenseFile = rootProject.file("LICENSE")
    from(licenseFile) {
        rename { "${it}_${archivesBaseName}" }
    }
}
