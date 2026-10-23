import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

/**
 * shared:adfilter —— 去广告规则引擎（PRD V1.2 §2.4 模块清单：规则解析（EasyList 兼容）、匹配、
 * 规则编译产物生成、净化统计 —— **KMP 双端共享**）。
 *
 * 关键约束（PRD V1.2 §2.4 + V1.3 §3.3.5）：
 *  - 规则**引擎**（解析/匹配/统计）全平台共享（纯 Kotlin，不依赖 jsoup/ahocorasick 等 JVM-only 库）；
 *  - **执行层平台化**：Android shouldInterceptRequest / iOS WKContentRuleList / Windows WebResourceRequested
 *    各自消费本模块产出的匹配结果（接入点已在 Phase 3 实现类预留）。
 *  - 平台差异如实标注（PRD V1.3 §3.6.2）：报告按端标注"拦截/净化"。
 *
 * 目标矩阵与 shared:ui-adaptive 裁剪一致（仅 macosArm64，见 docs/progess/100 §2）。
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
        namespace = "cn.ac.oac.glimbrowser.shared.adfilter"
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
