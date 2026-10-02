import xyz.jpenilla.runpaper.task.RunServer
import java.util.zip.ZipFile

plugins {
    id("java")
    alias(libs.plugins.runPaper)
    alias(libs.plugins.shadow)
}

group = "dev.tako"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

val paperRuntime = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

val foliaRuntime = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    implementation(libs.papersDelightApi)
    implementation(libs.ccScheduler)
    implementation(project(":NMS-Bridge"))
    implementation(project(":NMS-Bridge:v1_21_1"))
    implementation(project(":NMS-Bridge:v1_21_4"))
    implementation(project(":NMS-Bridge:v1_21_10"))
    implementation(project(":NMS-Bridge:v1_21_11"))
    implementation(libs.jetbrainsAnnotations)

    compileOnly(libs.paperApi)
    compileOnly(libs.gson)
    compileOnly(libs.craftEngineCore)
    compileOnly(libs.craftEngineBukkit)
    compileOnly(libs.craftEngineBukkitProxy)
    compileOnly(libs.placeholderApi)
    compileOnly(libs.libuid)

}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}

runPaper {
    disablePluginJarDetection()

    folia {
        registerTask {
            minecraftVersion(libs.versions.foliaRun.get())
            runDirectory(file("run-folia"))
            javaLauncher.set(foliaRuntime)
            jvmArgs(
                "-Xms4G",
                "-Xmx4G",
                "-Dfile.encoding=UTF-8",
                "-Dsun.stdout.encoding=UTF-8",
                "-Dsun.stderr.encoding=UTF-8",
                "--sun-misc-unsafe-memory-access=allow"
            )
        }
    }
}

tasks {
    processResources {
        val props = mapOf(
            "version" to rootProject.version.toString(),
            "craftEngine" to libs.versions.craftEngine.get()
        )
        filteringCharset = "UTF-8"
        inputs.properties(props)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
        filesMatching("papersdelight-build.properties") {
            expand(props)
        }
    }


    jar {
        enabled = false
    }

    shadowJar {
        archiveFileName.set("PapersDelight-${project.version}.jar")
        System.getenv("BUILD_FOLDER")?.let { destinationDirectory.set(file(it)) }
        relocate("org.jetbrains", "dev.tako.libs.org.jetbrains")
        relocate("org.intellij", "dev.tako.libs.org.intellij")
        relocate("cn.chengzhimeow.ccscheduler", "dev.tako.libs.cn.chengzhimeow.ccscheduler")
        exclude("META-INF/**")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    runServer {
        minecraftVersion(libs.versions.paperRun.get())
        javaLauncher.set(paperRuntime)
        jvmArgs(
            "-Xms4G",
            "-Xmx4G",
            "-Dfile.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8",
            "-Dsun.stderr.encoding=UTF-8"
        )
    }

    withType<RunServer>().configureEach {
        pluginJars(shadowJar.flatMap { it.archiveFile })
    }

    build {
        dependsOn(shadowJar)
    }
}