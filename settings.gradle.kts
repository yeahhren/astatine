pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.ornithemc.net/releases") { name = "Ornithe Releases" }
        maven("https://maven.ornithemc.net/snapshots") { name = "Ornithe Snapshots" }
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Astatine"
