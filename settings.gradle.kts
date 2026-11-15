pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://repo.huaweicloud.com/repository/gradle-plugin/")
        maven("https://repo.huaweicloud.com/repository/maven/")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // Compose Multiplatform 插件
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        // Kotlin/Wasm 需要 Node.js 二进制；nodejs.org 不在 Maven Central，
        // 必须单独配 Ivy 仓库，否则 :kotlinWasmNodeJsSetup 会 404
        ivy("https://nodejs.org/dist") {
            patternLayout { artifact("[artifact]v[revision]/[artifact]v[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
            content { includeModule("org.nodejs", "yarn") }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://repo.huaweicloud.com/repository/maven/")
        maven("https://jitpack.io")
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        ivy("https://nodejs.org/dist") {
            patternLayout { artifact("[artifact]v[revision]/[artifact]v[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
            content { includeModule("org.nodejs", "yarn") }
        }
    }
}

rootProject.name = "My Application"
include(":app")
include(":webApp")
