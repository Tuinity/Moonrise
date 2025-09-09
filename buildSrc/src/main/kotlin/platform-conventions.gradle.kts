plugins {
    id("common-conventions")
    id("com.gradleup.shadow")
}

extensions.create<RunConfigCommon>("runConfigCommon")
extensions.configure<RunConfigCommon>("runConfigCommon") {
    systemProperties.put("mixin.debug", "true")
    systemProperties.put("Moonrise.MaxViewDistance", "128")
    jvmArgs.addAll(listOf("-XX:+UseZGC", "-XX:+ZGenerational", "-XX:+UseDynamicNumberOfGCThreads", "-XX:-ZUncommit"))
}

configurations.create("libs")
configurations.named("shadow") {
    extendsFrom(configurations.getByName("libs"))
}
configurations.named("implementation") {
    extendsFrom(configurations.getByName("libs"))
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

tasks.shadowJar {
    mergeServiceFiles()
}
