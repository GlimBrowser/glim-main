import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    id("app.cash.sqldelight")
}

/**
 * shared:data —— 本地持久化（PRD V1.2 §2.4：SQLDelight 六端同构 schema，历史/书签/插件注册表/AI 档案）。
 *
 * 硬约束（000 §2 + PRD §7.2/§3.2.1）：
 *  - **API Key 明文永不出平台 SecureKeyStore**：DB 只存 `ai_profiles.api_key_ref`（引用），
 *    以及加密密文（若平台要求落库，Phase 6 接平台加密列）——本层 schema 无明文 Key 列。
 *  - 埋点/历史：URL 可存（本地），但**上报需哈希**（PRD §7.2，上报层处理）。
 *  - 隐私标签（无痕）：不入历史（应用层写入前判定，PRD §3.1.3）。
 *
 * 驱动注入：各端 provide Driver（android-driver / native-driver / sqlite-driver(jvm 测试) / jdbc）。
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
        namespace = "cn.ac.oac.glimbrowser.shared.data"
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
            api(libs.sqldelight.runtime)
            api(libs.sqldelight.coroutinesExtensions)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
            api(libs.sqldelight.coroutinesExtensions)
        }
    }
}

sqldelight {
    databases {
        create("GlimBrowserDatabase") {
            packageName = "cn.ac.oac.glimbrowser.data"
            // schema 输出（migration 生成 Phase 后续；PRD §2.4 六端同构）
        }
    }
}
