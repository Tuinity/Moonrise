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

tasks.shadowJar {
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()
}
