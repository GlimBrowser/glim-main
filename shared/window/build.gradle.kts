import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

/**
 * shared:window —— 多窗口与快捷键（PRD V1.3 §3.5.2 多窗口 + §3.5.1 布局缩放 + 附件 E 桌面快捷键映射表）。
 *
 * PRD 约束：
 *  - "共享 `WindowController` 接口；桌面真多窗口，移动栈式伪实现"（附录 B 多窗口行）
 *  - 标签拖出成窗（§3.5.2，桌面 Alpha/D2 交互）；移动为栈式伪实现（同窗口切换）
 *  - 快捷键 Cmd/Ctrl 归一化（附录 B："平台层仅转发"，映射表全平台共享）
 *
 * 本模块 = 纯逻辑（窗口状态模型 + 快捷键映射 + 归一化），平台窗口创建经 expect/actual 桥接（Phase 6 桌面专项接入）。
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
        namespace = "cn.ac.oac.glimbrowser.shared.window"
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
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
