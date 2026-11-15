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
    }
}

// Node.js 本地预置仓库（CI 里 workflow 会先下载 node 放到这个目录）。
// Kotlin/Wasm 的 :kotlinWasmNodeJsSetup 需要 org.nodejs:node，
// 但该包不在 Maven Central / 国内镜像里。
val localNodeRepo = uri("${System.getProperty("user.home")}/.gradle/local-node-repo")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven { url = localNodeRepo }
        ivy {
            url = localNodeRepo
            patternLayout { artifact("[artifact]v[revision]/[artifact]v[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://repo.huaweicloud.com/repository/maven/")
        maven("https://jitpack.io")
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "PixelColorPicker"
include(":app")
include(":webApp")