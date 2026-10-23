import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * shared:ui-adaptive —— 自适应布局系统（PRD V1.3 §2.4 模块划分 / §3.5.1 自适应布局系统）。
 *
 * 职责（纯逻辑 + 轻量 Compose 挂点）：
 *  - WindowSizeClass 判定（Compact <600dp / Medium 600–1000dp / Expanded >1000dp，PRD V1.3 §3.5.1）
 *  - 导航模式（底部导航 / 侧边抽屉 / 三栏）与分栏宽度策略推导 —— 纯函数，可单测
 *  - 独立成模块的动机（PRD V1.3 §2.3 架构要点 2）："形态判定、分栏策略、导航模式切换为纯逻辑，可单测"
 *
 * 目标矩阵与 app/shared 保持一致的裁剪原则（见 docs/progess/100_Phase0 §2）：
 * 仅声明 CMP 实际发布的 native 目标（macosArm64，不声明 macosX64/mingwX64）。
 */
kotlin {
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "cn.ac.oac.glimbrowser.shared.uiadaptive"
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
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
