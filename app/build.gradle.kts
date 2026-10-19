import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 版本号与 versionCode 来自 gradle.properties，与 CI 的 tag 校验同源（规格第十九章门禁）
val appVersionName: String = providers.gradleProperty("VERSION_NAME").get()
val appVersionCode: Int = providers.gradleProperty("VERSION_CODE").get().toInt()

// 正式签名信息从 keystore.properties 读取；该文件不入库（见 .gitignore）。
// 没有这个文件时自动退回 debug 签名，保证任何人 clone 下来都能直接构建。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.sj.followclip"
    // compileSdk 决定能调用到哪些 API，可高于 targetSdk。
    // 规格只钉住 targetSdk = 34，此处 compileSdk 取 36 以匹配当前 AndroidX 依赖的要求。
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sj.followclip"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // 必须放在 buildTypes 之前：release 里要按名字取到这个配置
    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
            // v3 支持密钥轮换：以后换签名也能覆盖安装；v4 供 Play 增量安装
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        debug {
            // 日常自测一律用 debug 包，不占用签名凭据（规格第十五章铁律三）
        }
        release {
            // 有 keystore.properties 就用正式签名，没有才退回 debug 签名。
            // 正式签名是「别人也能装、以后能覆盖升级」的前提；debug 签名做不到这两点。
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// APK 产物名规范（SJFollowClip-v版本号-release.apk，规格第三章第（三）节）
// 不在 Gradle 里改名：AGP 8.x 的 VariantOutput 公共 API 不暴露 outputFileName，
// 强行使用内部实现会让构建脚本变脆。改名放在发布工作流的打包步骤里做一次即可。
// 本地构建产物路径：app/build/outputs/apk/debug/app-debug.apk

dependencies {
    // Phase 0 只引入空壳界面所需的最小依赖集合。
    // Room、Navigation、OkHttp 等按规格第十九章在 Phase 1 及以后按需引入。
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
