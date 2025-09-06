plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral {
        mavenContent { releasesOnly() }
    }
    maven("https://maven.fabricmc.net/")
    maven("https://maven.architectury.dev/")
}

dependencies {
    implementation("net.fabricmc:access-widener:2.1.0")
    implementation("dev.architectury:at:1.0.1")
}
