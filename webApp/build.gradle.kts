import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
        }
        binaries.executable()
    }

    // CMP 1.12.x：依赖写在 sourceSets，不再用 compose.dependencies {}
    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
                implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
                implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
                implementation("org.jetbrains.compose.ui:ui:1.12.2")
                implementation("org.jetbrains.compose.components:components-resources:1.12.2")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            }
        }
    }
}

repositories {
    maven("https://maven.aliyun.com/repository/google")
    maven("https://maven.aliyun.com/repository/central")
    maven("https://maven.aliyun.com/repository/public")
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}
