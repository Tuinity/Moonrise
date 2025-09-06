plugins {
    id("java-library")
}

extensions.create<RunConfigCommon>("runConfigCommon")
extensions.configure<RunConfigCommon>("runConfigCommon") {
    systemProperties.put("mixin.debug", "true")
    systemProperties.put("Moonrise.MaxViewDistance", "128")
    jvmArgs.addAll(listOf("-XX:+UseZGC", "-XX:+ZGenerational", "-XX:+UseDynamicNumberOfGCThreads", "-XX:-ZUncommit"))
}
