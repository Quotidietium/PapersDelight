plugins {
    id("java-library")
}

dependencies {
    api(project(":Minecraft-Bridge:api"))
    compileOnly(libs.paperApiBridge)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
