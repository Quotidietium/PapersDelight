pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugins/")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
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
    versionCatalogs {
        create("libs") {
            from(files("libs.versions.toml"))
        }
    }
}

rootProject.name = "PapersDelight"

buildCache {
    local {
        directory = File(rootDir, ".gradle-local/build-cache")
    }
}

include(":NMS-Bridge")
include(":NMS-Bridge:api")
include(":NMS-Bridge:v1_21_1")
include(":NMS-Bridge:v1_21_4")
include(":NMS-Bridge:v1_21_10")
include(":NMS-Bridge:v1_21_11")
