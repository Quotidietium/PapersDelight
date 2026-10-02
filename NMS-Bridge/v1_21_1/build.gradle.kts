plugins {
    id("java-library")
    alias(libs.plugins.paperweight)
}

dependencies {
    paperweight.paperDevBundle(libs.versions.mc1211.get())
    compileOnly(project(":NMS-Bridge:api"))
    compileOnly(project(":NMS-Bridge"))
}

java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
