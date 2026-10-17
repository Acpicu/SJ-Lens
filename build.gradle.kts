// 根构建脚本：只声明插件版本，具体配置在各模块内。
// 依赖版本统一由 gradle/libs.versions.toml 管理（版本目录）。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
