plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.1.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.bluecolored.de/releases")
    maven("https://jitpack.io")
    flatDir {
        dir("libs")
    }
}

dependencies {
    implementation("com.typewritermc:QuestExtension:0.9.0")
    implementation("com.typewritermc:BasicExtension:0.9.0")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("de.bluecolored:bluemap-api:2.7.3")
    compileOnly("com.flowpowered:flow-math:1.0.3")
}



group = "com.btc.typewriter"
version = "0.1.0"

typewriter {
    namespace = "tracking"

    extension {
        name = "Tracking"
        shortDescription = "Player tracking system with BlueMap integration"
        description = """
            A tracking extension that records player movements in sessions.
            Features:
            - Configurable particle tracking for admins.
            - BlueMap integration to visualize player paths.
            - Session-based storage using Artifacts.
            - In-game inspection commands.
        """.trimIndent()
        engineVersion = "0.9.0-beta-171"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA

        dependencies {
            paper()
        }
    }
}

kotlin {
    jvmToolchain(21)
}

