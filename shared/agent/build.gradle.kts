import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * shared:agent —— BYOK Agent 编排层（PRD V1.2 §2.4：Agent 编排、工具协议、json_object 校验与降级、
 * 上下文构建；附件 C 全部工具协议；PRD V1.1 §3.2.2 Agent 层架构）。
 *
 * 职责链（附件 C.1.2）：上下文构建 → 工具选择 → 协议适配（messages/response_format）→ 输出解析（json_object 降级链 C.1.5）
 * 硬约束（000 §2）：对话类不带 json_object、注入防护（围栏/白名单）、解析失败降级不崩溃。
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
        namespace = "cn.ac.oac.glimbrowser.shared.agent"
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
            api(project(":shared:network"))
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.kotlinx.coroutinesCore)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
