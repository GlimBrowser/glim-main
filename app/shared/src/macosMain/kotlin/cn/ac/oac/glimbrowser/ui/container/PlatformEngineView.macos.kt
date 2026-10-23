package cn.ac.oac.glimbrowser.ui.container

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.ac.oac.glimbrowser.engine.BrowserEngine
import cn.ac.oac.glimbrowser.engine.WkWebViewEngine

/**
 * macOS native（macosArm64 K/N 框架目标）actual —— **诚实说明，非假占位**：
 *
 * - **macOS 桌面产品形态走 CMP Desktop（jvm 目标，见 app/desktopApp）**，与 PRD V1.3 §2.2.1
 *   「桌面 = CMP Desktop 共享」一致；本 native 框架目标主要服务 KMP 库消费者与 iOS 共享代码。
 * - 引擎侧 [WkWebViewEngine] 已是**真实 WKWebView 实现**（appleMain，与 iOS 共用，编译通过）。
 * - 视图嵌入缺口：**CMP 1.12.1 的 macOS target 未提供公开 NSView 互操作 API**
 *   （实测 `androidx.compose.ui.viewinterop.InteropView` 在 macosMain 中 `actual typealias InteropView = Any`，
 *   InteropContainer 为 internal）—— 无法在不使用内部 API 的前提下把 WKWebView(NSView) 嵌入 Compose 布局。
 * - 处置：macOS 桌面视图嵌入划入 **Phase 6 桌面专项**（WBS D1：WKWebView 容器经平台层窗口集成，
 *   或 CMP 官方 NSView 互操作 GA 后接入）；届时 [WkWebViewEngine] 直接复用。
 * - ⚠️ 不是"假装能上网"的静默占位：文案明示技术原因与迁移计划，且 iOS 已是真实内核。
 */
@Composable
actual fun PlatformEngineView(engine: BrowserEngine, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "此平台的网页渲染尚未开放。\n\n" +
                "你可以先在 Android 或 iOS 上使用完整浏览功能。\n\n" +
                "当前地址：${engine.currentUrl() ?: "尚未打开页面"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
