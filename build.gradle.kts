// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Compose Multiplatform（仅 webApp 用）
    id("org.jetbrains.compose") version "1.12.2" apply false
    // Kotlin Multiplatform：Wasm 标准库预编译版本为 2.3.20，需与之对齐
    id("org.jetbrains.kotlin.multiplatform") version "2.3.20" apply false
}
