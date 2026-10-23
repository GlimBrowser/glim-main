import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * shared:plugin —— 插件/用户脚本中心（PRD V1.0 §3.4 插件系统 + V1.1 §3.4 插件中心 + 附件 E 插件权限）。
 *
 * 关键约束（000 §2 + PRD §3.4/§10）：
 *  - 权限逐项授权（用户可见可关）+ 沙箱执行（脚本白名单 API，禁动态代码加载/反射）
 *  - iOS/macOS 政策：插件收敛为「用户脚本 + 内置模块」（PRD §2.2.2 硬约束），不承诺完整 Chrome 扩展兼容
 *  - 远程吊销（脚本指纹黑名单，PRD §3.4/§10 风险表）
 *  - 订阅更新（CDN，server 模块占位）
 *
 * 本模块 = 纯 Kotlin 元数据/权限/作用域模型 + 校验逻辑（可单测）；脚本执行沙箱在 Phase 6 平台桥接层。
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
        namespace = "cn.ac.oac.glimbrowser.shared.plugin"
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
            implementation(libs.kotlinx.serializationJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
