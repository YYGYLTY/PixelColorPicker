import org.jetbrains.compose.resources.ResourcesExtension
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
                // localStorage / fetch 等浏览器 API
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
            }
        }
    }
}


// 中文字体需要 Res.font.* 访问器。
// 默认 Auto 模式在“资源只被 wasmJs sourceSet 引用”时会跳过 Res 类生成，
// 强制 Always 保证 Res 一定存在。
compose.resources {
    generateResClass = always
    packageOfResClass = "com.pixelcolorpicker.generated.resources"
    publicResClass = false
    nameOfResClass = "Res"
}
