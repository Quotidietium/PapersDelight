import xyz.jpenilla.runpaper.task.RunServer
import java.util.zip.ZipFile

plugins {
    id("java")
    alias(libs.plugins.runPaper)
    alias(libs.plugins.shadow)
}

group = "dev.tako"

// 仓库显式声明：repositoriesMode=PREFER_PROJECT 时项目仓库优先于 settings 声明，
// 根项目若不声明，将只剩注入的镜像仓库，导致 dev.tako / cn.chengzhimeow 等自建仓库构件无法解析。
repositories {
    // 项目内本地仓库：承载仅在 GitHub Releases 分发、未上 Maven 仓库的构件（如 Libuid）
    maven {
        url = uri(rootDir.resolve("run/maven-repo"))
        content {
            includeGroup("dev.tako.libuid.local")
            includeModule("dev.tako", "libuid")
        }
    }
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.momirealms.net/releases/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://mvn.hezhongkj.top/releases/") {
        content {
            includeGroup("dev.tako")
        }
    }
    maven("https://repo-eo.catnies.top/releases/") {
        content {
            includeGroup("cn.chengzhimeow")
        }
    }
}

// 统一授予所有子项目（NMS-Bridge 及其 6 个子模块）相同的仓库集；
// repositoriesMode=PREFER_PROJECT 下，缺省仓库声明的子项目会被镜像注入源屏蔽，导致自建仓库构件不可达。
subprojects {
    @Suppress("UnstableApiUsage")
    repositories {
        maven {
            url = uri(rootProject.rootDir.resolve("run/maven-repo"))
            content { includeModule("dev.tako", "libuid") }
        }
        mavenLocal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.momirealms.net/releases/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://mvn.hezhongkj.top/releases/") {
            content { includeGroup("dev.tako") }
        }
        maven("https://repo-eo.catnies.top/releases/") {
            content { includeGroup("cn.chengzhimeow") }
        }
    }
}

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
            runDirectory(file("run/folia"))
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

    // 细粒度交付打包（见 CONTRIBUTING.md §3）
    register<Copy>("dist") {
        group = "distribution"
        description = "汇总 shadowJar 产物、NMS-Bridge 模块 jar 与基准运行时到 dist/"
        dependsOn(shadowJar, benchmarkSource.classesTaskName, "benchmarkRuntime")
        from(shadowJar.flatMap { it.archiveFile })
        into(rootProject.layout.projectDirectory.dir("dist"))
        doLast {
            val modulesDir = rootProject.layout.projectDirectory.dir("dist/modules")
            modulesDir.asFile.mkdirs()
            val benchDir = rootProject.layout.projectDirectory.dir("dist/benchmark")
            benchDir.asFile.mkdirs()
            project.copy {
                from(project(":NMS-Bridge:api").tasks.named("jar"))
                from(project(":NMS-Bridge").tasks.named("jar"))
                from(project(":NMS-Bridge:v1_21_1").tasks.named("jar"))
                from(project(":NMS-Bridge:v1_21_4").tasks.named("jar"))
                from(project(":NMS-Bridge:v1_21_10").tasks.named("jar"))
                from(project(":NMS-Bridge:v1_21_11").tasks.named("jar"))
                into(modulesDir)
            }
            project.copy {
                from(rootProject.file("benchmark/lib/runtime"))
                into(benchDir.dir("lib"))
            }
            project.copy {
                from(rootProject.file("benchmark/run-bench.sh"), rootProject.file("benchmark/compare.py"))
                into(benchDir)
            }
            project.copy {
                from(benchmarkSource.output.classesDirs)
                into(benchDir.dir("classes"))
            }
        }
    }

    build {
        dependsOn(shadowJar)
    }
}

// =====================================================================
// benchmark 基准测试 source set（见 CONTRIBUTING.md §5 与 benchmark/README.md）
// =====================================================================
val benchmarkSource = sourceSets.create("benchmark") {
    java.srcDir(file("benchmark/src"))
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + compileClasspath
}

// 基准运行时依赖：主插件编译所需的全部第三方 jar（被测插件类来自 benchmark/lib/<label>/plugin.jar）
val benchRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    benchRuntime(libs.papersDelightApi)
    benchRuntime(libs.paperApi)
    benchRuntime(libs.craftEngineCore)
    benchRuntime(libs.craftEngineBukkit)
    benchRuntime(libs.craftEngineBukkitProxy)
    benchRuntime(libs.gson)
    benchRuntime(libs.placeholderApi)
    benchRuntime(libs.libuid)
    benchRuntime(libs.ccScheduler)
    benchRuntime(libs.jetbrainsAnnotations)
}

tasks.register<Copy>("benchmarkRuntime") {
    from(benchRuntime)
    into(file("benchmark/lib/runtime"))
    description = "把基准运行时依赖 jar 拷贝到 benchmark/lib/runtime（见 run-bench.sh）"
}