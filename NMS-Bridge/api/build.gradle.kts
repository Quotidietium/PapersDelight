plugins {
    id("java-library")
}

dependencies {
    compileOnly(libs.paperApi)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}
