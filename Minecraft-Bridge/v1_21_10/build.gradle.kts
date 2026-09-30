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
    paperweight.paperDevBundle(libs.versions.mc12110.get())
    compileOnly(project(":Minecraft-Bridge:api"))
    compileOnly(project(":Minecraft-Bridge"))
    compileOnly(libs.papersDelightApi) { isTransitive = false }
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
