plugins {
    id("java-library")
    alias(libs.plugins.paperweight)
}

repositories {
    mavenLocal()
    maven("https://mvn.hezhongkj.top/releases/") {
        content {
            includeGroup("dev.tako")
        }
    }
}

dependencies {
    paperweight.paperDevBundle(libs.versions.mc12111.get())
    compileOnly(project(":Minecraft-Bridge:api"))
    compileOnly(project(":Minecraft-Bridge:v1_21_10"))
    compileOnly(project(":Minecraft-Bridge"))
    compileOnly(libs.papersDelightApi) { isTransitive = false }
}

configurations.configureEach {
    resolutionStrategy.force("net.kyori:adventure-text-serializer-ansi:4.26.1")
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
