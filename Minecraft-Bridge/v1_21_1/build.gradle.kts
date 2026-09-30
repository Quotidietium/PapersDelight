plugins {
    id("java-library")
    alias(libs.plugins.paperweight)
}

dependencies {
    paperweight.paperDevBundle(libs.versions.mc1211.get())
    compileOnly(project(":Minecraft-Bridge:api"))
    compileOnly(project(":Minecraft-Bridge"))
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
