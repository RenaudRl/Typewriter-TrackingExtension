dependencies {
    compileOnly("de.bluecolored:bluemap-api:2.7.3")
    compileOnly("com.flowpowered:flow-math:1.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

plugins {
    kotlin("jvm") version "2.3.20"
    id("com.typewritermc.module-plugin") version "2.1.0"
}

group = "btcrenaud"
version = "0.0.8"

repositories {
    mavenLocal()
    maven("https://jitpack.io/")
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.typewritermc.com/beta/")
    maven("https://maven.typewritermc.com/external/")
    maven("https://repo.bluecolored.de/releases/")
    maven("https://repo.bluecolored.de/snapshots/")
}

typewriter {
    namespace = "renaud"

    extension {
        name = "Tracking"
        shortDescription = "Player tracking system with BlueMap integration"
        description = """Typewriter extension module providing additional entries for the Typewriter plugin ecosystem. Supports Paper and Folia server platforms with full feature parity. This module extends the core functionality with specialized entries. Compatible with the official Typewriter engine and designed for standalone use."""
        engineVersion = "0.9.0-beta-175"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA
        paper()
    }
}

    

kotlin {
    jvmToolchain(21)
}

