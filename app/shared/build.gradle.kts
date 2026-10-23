import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // AI 配置/会话等 @Serializable 数据类需要序列化插件（否则运行期 "Serializer not found"）
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    // PRD V1.3 附录 B 列出 macosArm64/macosX64/mingwX64，但实测 CMP 1.12.1（foundation/ui/material3
    // 等 UI 依赖）不发布 macosX64/mingwX64 变体（macosX64 已被 Kotlin 官方弃用 KT-78660/KT-81596；
    // mingwX64 仅 runtime 发布、UI 核心库未发布）。桌面 Windows/macOS-Intel 形态在本工程走 CMP Desktop
    // (jvm) 目标，见 docs/progess/100_Phase0 多模块骨架.md「目标矩阵决策」。这里只保留实际可用的 macosArm64。
    macosArm64()

    jvm()
    
    js {
        browser()
        binaries.executable()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    android {
       namespace = "cn.ac.oac.glimbrowser.app.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.ktor.clientCio)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.datetime)
            // AI 运行时：HTTP 客户端（各端引擎注入；BYOK 直连 PRD §3.2.5）
            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.serializationKotlinxJson)
            implementation(libs.ktor.sse)
            implementation(libs.kotlinx.coroutinesCore)
            // Phase 1：接入自适应布局系统（PRD V1.3 §2.4 shared:ui-adaptive），api 暴露给 UI 层
            api(project(":shared:ui-adaptive"))
            // Phase 4：接入去广告规则引擎（PRD V1.2 §2.4 shared:adfilter），api 暴露给容器/壳
            api(project(":shared:adfilter"))
            // Phase 5：BYOK AI（附件 D 三步引导 UI 消费 network/agent）
            api(project(":shared:network"))
            api(project(":shared:agent"))
            // Phase 6：持久化 + 插件 + 多窗口（菜单入口/档案落库/快捷键簿记）
            api(project(":shared:data"))
            api(project(":shared:plugin"))
            api(project(":shared:window"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.clientCio)
            implementation(libs.sqldelight.androidDriver)
            // 系统返回手势/按键 → BackHandler（108 导航）
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiBackHandler)
            implementation(libs.androidx.core.ktx)
            // 000 §2 红线7：Android 密钥入加密安全区（EncryptedSharedPreferences）
            implementation(libs.androidx.security.crypto)
        }
        appleMain.dependencies {
            implementation(libs.ktor.clientDarwin)
            implementation(libs.sqldelight.nativeDriver)
            implementation(libs.compose.uiBackHandler)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.clientCio)
            implementation(libs.sqldelight.sqliteDriver)
            implementation(libs.compose.uiBackHandler)
        }
        jsMain.dependencies {
            implementation(libs.ktor.clientJs)
            implementation(libs.wrappers.browser)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}