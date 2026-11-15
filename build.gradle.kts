// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Compose Multiplatform（仅 webApp 用）
    id("org.jetbrains.compose") version "1.12.2" apply false
    // Kotlin Multiplatform：与 app 用同一个 Kotlin 版本
    id("org.jetbrains.kotlin.multiplatform") version "2.3.10" apply false
}
