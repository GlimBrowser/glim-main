rootProject.name = "GlimBrowser"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":app:androidApp")
include(":app:desktopApp")
include(":app:shared")
include(":app:webApp")
include(":core")
include(":server")
// PRD V1.3 §2.4：shared:ui-adaptive —— 自适应布局系统（独立成模块，纯逻辑可单测）
include(":shared:ui-adaptive")
// PRD V1.2 §2.4：shared:adfilter —— 去广告规则引擎（解析/匹配/统计，纯 Kotlin KMP）
include(":shared:adfilter")
// PRD V1.2 §2.4：shared:network —— BYOK 网络层（Ktor + SSE + 能力探测）
include(":shared:network")
// PRD V1.2 §2.4：shared:agent —— BYOK Agent 编排（工具协议、json_object 降级链、上下文构建）
include(":shared:agent")
// PRD V1.0 §3.4：shared:plugin —— 插件/用户脚本（元数据、权限、作用域、吊销）
include(":shared:plugin")
// PRD V1.2 §2.4：shared:data —— SQLDelight 本地持久化（历史/书签/AI 档案/插件注册）
include(":shared:data")
// PRD V1.3 §3.5.2 + 附件 E：shared:window —— 多窗口 + 桌面快捷键映射
include(":shared:window")